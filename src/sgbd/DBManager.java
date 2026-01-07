package sgbd;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Il faut importer les classes des autres packages !
import espaceDisque.BufferManager;
import espaceDisque.DBConfig;
import espaceDisque.DiskManager;
import donnees.ColInfo;
import donnees.Record;
import donnees.Relation;

public class DBManager {

    private DBConfig dbConfig;
    private DiskManager diskManager;
    private BufferManager bufferManager;

    // Le Catalogue
    private Map<String, Relation> tables;

    public DBManager(DBConfig config) {
        this.dbConfig = config;
        this.diskManager = new DiskManager(config);
        this.bufferManager = new BufferManager(config, diskManager);
        this.tables = new HashMap<>();
    }

    /**
     * Démarrage du SGBD.
     */
    @SuppressWarnings("unchecked")
    public void Init() {
        diskManager.Init();

        File catalogFile = new File(dbConfig.dbpath + File.separator + "catalogue.db");
        if (catalogFile.exists()) {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(catalogFile))) {
                this.tables = (Map<String, Relation>) ois.readObject();

                for (Relation rel : tables.values()) {
                    rel.setManagers(this.diskManager, this.bufferManager);
                }
                System.out.println("[DBManager] Catalogue chargé : " + tables.size() + " tables.");
            } catch (Exception e) {
                System.err.println("[DBManager] Erreur chargement catalogue : " + e.getMessage());
            }
        }
    }

    /**
     * Arrêt du SGBD.
     */
    public void Finish() {
        bufferManager.FlushBuffers();
        try {
            File catalogFile = new File(dbConfig.dbpath + File.separator + "catalogue.db");
            try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(catalogFile))) {
                oos.writeObject(this.tables);
            }
        } catch (IOException e) {
            System.err.println("[DBManager] Erreur sauvegarde catalogue : " + e.getMessage());
        }
        diskManager.Finish();
        System.out.println("[DBManager] Arrêt complet.");
    }

    public Relation GetRelation(String name) {
        return tables.get(name);
    }

    // ==========================================
    // MÉTHODES DE TRAITEMENT DES COMMANDES (SQL)
    // ==========================================

    public void ProcessCommand(String command) {
        command = command.trim();
        String[] parts = command.split("\\s+");
        if (parts.length == 0)
            return;

        String verb = parts[0].toUpperCase();

        try {
            switch (verb) {
                case "CREATE":
                    if (parts.length > 1 && parts[1].equalsIgnoreCase("TABLE")) {
                        handleCreateTable(command);
                    }
                    break;
                case "INSERT":
                    if (parts.length > 1 && parts[1].equalsIgnoreCase("INTO")) {
                        handleInsert(command);
                    }
                    break;
                case "SELECT":
                    if (parts.length > 3) {
                        handleSelect(command);
                    }
                    break;
                case "DROP":
                    // Cas 1 : DROP TABLE <Nom> (Singulier)
                    if (parts.length > 1 && parts[1].equalsIgnoreCase("TABLE")) {
                        handleDrop(command);
                    }
                    // Cas 2 : DROP TABLES (Pluriel - pour tout supprimer)
                    else if (parts.length > 1 && parts[1].equalsIgnoreCase("TABLES")) {
                        handleDropAllTables();
                    }
                    break;

                case "APPEND":
                    if (parts.length > 1 && parts[1].equalsIgnoreCase("INTO")) {
                        handleAppend(command);
                    }
                    break;
                case "DELETE":
                    handleDelete(command); // Appel direct car le parsing est fait dedans
                    break;
                case "UPDATE":
                    if (parts.length > 1) {
                        handleUpdate(command);
                    }
                    break;

                case "DESCRIBE":
                    // Gestion de DESCRIBE TABLE
                    if (parts.length > 1 && parts[1].equalsIgnoreCase("TABLES")) {
                        handleDescribeTables();
                    }
                    break;
                case "EXIT":
                    break;
                default:
                    System.out.println("Commande inconnue : " + verb);
            }
        } catch (Exception e) {
            System.out.println("Erreur d'exécution : " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handleCreateTable(String command) {
        String[] parts = command.split("\\s+");
        if (parts.length < 3)
            return;
        String tableName = parts[2];

        if (tables.containsKey(tableName)) {
            System.out.println("Erreur : La table " + tableName + " existe déjà.");
            return;
        }

        int openParen = command.indexOf('(');
        int closeParen = command.lastIndexOf(')');
        if (openParen == -1 || closeParen == -1)
            return;

        String schemaStr = command.substring(openParen + 1, closeParen);
        String[] colDefs = schemaStr.split(",");
        List<ColInfo> columns = new ArrayList<>();

        for (String colDef : colDefs) {
            String[] colParts = colDef.trim().split(":");
            String colName = colParts[0].trim();
            String typeStr = colParts[1].trim().toUpperCase();

            // Alias REAL -> FLOAT ---
            // Si "REAL", on le traite comme un FLOAT
            if (typeStr.equals("REAL")) {
                typeStr = "FLOAT";
            }
            // ---------------------------------------

            ColInfo.ColType type;
            int length = 0;

            if (typeStr.contains("(")) {
                int p1 = typeStr.indexOf('(');
                int p2 = typeStr.indexOf(')');
                type = ColInfo.ColType.valueOf(typeStr.substring(0, p1));
                length = Integer.parseInt(typeStr.substring(p1 + 1, p2));
            } else {
                type = ColInfo.ColType.valueOf(typeStr);
            }
            columns.add(new ColInfo(colName, type, length));
        }

        Relation rel = new Relation(tableName, columns, diskManager, bufferManager, null, dbConfig);
        rel.createHeaderPage();
        tables.put(tableName, rel);
        System.out.println("Table " + tableName + " créée.");
    }

    /**
     * Gère la commande DROP TABLE.
     * Supprime la table du catalogue et libère l'espace disque.
     */
    private void handleDrop(String command) {
        String[] parts = command.trim().split("\\s+");

        // Vérification de la syntaxe : DROP TABLE <Nom>
        if (parts.length < 3) {
            System.out.println("Erreur syntaxe : DROP TABLE <NomTable>");
            return;
        }

        String tableName = parts[2];

        // Vérification que la table existe
        if (!tables.containsKey(tableName)) {
            System.out.println("Erreur : Table " + tableName + " inconnue.");
            return;
        }

        Relation rel = tables.get(tableName);

        // 1. Récupérer toutes les pages utilisées par la relation (Header + Data)
        // On utilise le nom complet 'espaceDisque.PageId' pour éviter les erreurs
        // d'import
        java.util.List<espaceDisque.PageId> pagesToFree = rel.getDataPages();

        // 2. Désallouer chaque page via le DiskManager
        for (espaceDisque.PageId pid : pagesToFree) {
            diskManager.DeallocPage(pid);
        }

        // 3. Supprimer la table du catalogue en mémoire
        tables.remove(tableName);
        System.out.println("Table " + tableName + " supprimée.");
    }

    /**
     * Gère la commande INSERT INTO.
     * Format : INSERT INTO NomRelation VALUES (val1,val2,...)
     */
    private void handleInsert(String command) {
        String[] parts = command.trim().split("\\s+");

        // 1. Vérification syntaxe de base
        if (parts.length < 5 || !parts[1].equalsIgnoreCase("INTO") || !parts[3].equalsIgnoreCase("VALUES")) {
            System.out.println("Erreur syntaxe : INSERT INTO <Nom> VALUES (<valeurs>)");
            return;
        }

        String tableName = parts[2];
        String valuePart = parts[4]; // "(v1,v2,...)"

        // 2. Nettoyage des parenthèses
        if (!valuePart.startsWith("(") || !valuePart.endsWith(")")) {
            System.out.println("Erreur : Les valeurs doivent être entre parenthèses.");
            return;
        }
        String content = valuePart.substring(1, valuePart.length() - 1);
        String[] valTokens = content.split(",");

        // 3. Récupération de la table
        if (!tables.containsKey(tableName)) {
            System.out.println("Erreur : Table " + tableName + " inconnue.");
            return;
        }
        Relation rel = tables.get(tableName);

        // 4. Vérification du nombre de valeurs
        if (valTokens.length != rel.getCols().size()) {
            System.out.println("Erreur : Nombre de valeurs incorrect.");
            return;
        }

        // =================================================================================
        // VALIDATION DES TYPES ET DES TAILLES
        // =================================================================================
        try {
            for (int i = 0; i < rel.getCols().size(); i++) {
                donnees.ColInfo col = rel.getCols().get(i);
                String valTest = valTokens[i].trim();

                // Test de conversion pour voir si ça plante
                if (col.type == donnees.ColInfo.ColType.INT) {
                    Integer.parseInt(valTest);
                } else if (col.type == donnees.ColInfo.ColType.FLOAT) {
                    Float.parseFloat(valTest);
                }
                // Vérification spécifique pour les chaînes
                else if (col.type == donnees.ColInfo.ColType.CHAR || col.type == donnees.ColInfo.ColType.VARCHAR) {
                    if (!valTest.startsWith("\"") || !valTest.endsWith("\"")) {
                        System.out.println("Erreur de type : La valeur " + valTest + " doit être entre guillemets.");
                        return;
                    }

                    // --- CONDITION SUPPLEMENTAIRE : VERIFICATION DE LA TAILLE ---
                    // On retire les guillemets pour compter la vraie taille
                    String valClean = valTest.substring(1, valTest.length() - 1);
                    // Si col.length > 0 (ce qui est le cas pour VARCHAR(N)), on vérifie
                    if (col.length > 0 && valClean.length() > col.length) {
                        System.out.println("Erreur de taille : La chaîne \"" + valClean + "\" (" + valClean.length()
                                + ") dépasse la limite de la colonne " + col.name + " (" + col.length + ").");
                        return; // On annule l'insertion
                    }
                }
            }
        } catch (NumberFormatException e) {
            System.out.println("Erreur de type : Une valeur ne correspond pas au type attendu (INT ou FLOAT).");
            return; // On annule l'insertion
        }

        // 5. Création du Record
        donnees.Record record = new donnees.Record();
        try {
            for (int i = 0; i < rel.getCols().size(); i++) {
                donnees.ColInfo col = rel.getCols().get(i);
                String token = valTokens[i].trim();

                switch (col.type) {
                    case INT:
                        record.values.add(Integer.parseInt(token));
                        break;
                    case FLOAT:
                        record.values.add(Float.parseFloat(token));
                        break;
                    case CHAR:
                    case VARCHAR:
                        if (token.startsWith("\"") && token.endsWith("\"")) {
                            record.values.add(token.substring(1, token.length() - 1));
                        } else {
                            System.out.println("Erreur : Chaîne sans guillemets : " + token);
                            return;
                        }
                        break;
                }
            }

            // 6. Insertion
            donnees.RecordId rid = rel.InsertRecord(record);

            if (rid != null) {
                System.out.println("Record inséré avec succès. (RID: " + rid + ")");
            } else {
                System.out.println("Erreur : Insertion échouée.");
            }

        } catch (Exception e) {
            System.out.println("Erreur insertion : " + e.getMessage());
        }
    }

    /**
     * Gère SELECT avec projection et filtrage.
     * Format : SELECT alias.Col1,alias.Col2 FROM NomTable alias WHERE ...
     * Ou : SELECT * FROM NomTable alias WHERE ...
     */
    private void handleSelect(String command) {
        System.out.println("[DEBUG] Analyse de la commande : " + command);

        int idxFrom = command.toUpperCase().indexOf(" FROM ");
        int idxWhere = command.toUpperCase().indexOf(" WHERE ");

        if (idxFrom == -1) {
            System.out.println("Erreur syntaxe : SELECT ... FROM ...");
            return;
        }

        String projPart = command.substring(6, idxFrom).trim();
        String fromPart;
        String wherePart = null;

        if (idxWhere != -1) {
            fromPart = command.substring(idxFrom + 6, idxWhere).trim();
            wherePart = command.substring(idxWhere).trim();
        } else {
            fromPart = command.substring(idxFrom + 6).trim();
        }

        String[] tableParts = fromPart.split("\\s+");
        String tableName = tableParts[0];
        String alias = (tableParts.length > 1) ? tableParts[1] : "";

        if (!tables.containsKey(tableName)) {
            System.out.println("Erreur : Table " + tableName + " inconnue.");
            return;
        }
        Relation rel = tables.get(tableName);

        // Pipeline
        donnees.IRecordIterator iterator = new donnees.RelationScanner(rel);

        // GESTION DU WHERE
        if (wherePart != null) {
            System.out.println("[DEBUG] Traitement du WHERE : " + wherePart);
            List<donnees.Condition> conditions = parseWhereClause(wherePart, rel, alias);
            if (!conditions.isEmpty()) {
                System.out.println("[DEBUG] " + conditions.size() + " conditions trouvées. Ajout du SelectOperator.");
                iterator = new donnees.SelectOperator(iterator, conditions);
            } else {
                System.out.println("[DEBUG] Aucune condition valide trouvée (Parsing échoué ?).");
            }
        }

        // GESTION DE LA PROJECTION (SELECT C2...)
        if (!projPart.equals("*")) {
            System.out.println("[DEBUG] Traitement de la projection : " + projPart);
            String[] colsRequested = projPart.split(",");
            List<Integer> colIndices = new ArrayList<>();

            for (String colReq : colsRequested) {
                colReq = colReq.trim();
                boolean found = false;

                // Cherche la colonne (En ignorant Majuscule/Minuscule)
                // 1. Essai avec alias retiré si présent
                String pureColName = colReq;
                if (!alias.isEmpty() && colReq.toUpperCase().startsWith(alias.toUpperCase() + ".")) {
                    pureColName = colReq.substring(alias.length() + 1);
                }

                for (int i = 0; i < rel.getCols().size(); i++) {
                    if (rel.getCols().get(i).name.equalsIgnoreCase(pureColName)) {
                        colIndices.add(i);
                        found = true;
                        System.out.println(
                                "[DEBUG] Colonne trouvée : " + rel.getCols().get(i).name + " (Index " + i + ")");
                        break;
                    }
                }

                if (!found) {
                    System.out.println("Erreur : Colonne '" + colReq + "' introuvable dans la table !");
                }
            }

            if (!colIndices.isEmpty()) {
                iterator = new donnees.ProjectOperator(iterator, colIndices);
            }
        } else {
            System.out.println("[DEBUG] Projection : Tout (*) sélectionné.");
        }

        // EXECUTION
        System.out.println("--- Résultat de la requête ---");
        int count = 0;
        donnees.Record rec;

        while ((rec = iterator.GetNextRecord()) != null) {

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < rec.values.size(); i++) {
                sb.append(rec.values.get(i));

                if (i < rec.values.size() - 1)
                    sb.append(" ; ");
            }
            System.out.println(sb.toString());
            count++;
        }

        iterator.Close();
        System.out.println("Total selected records = " + count);
    }

    /**
     * Gère la commande APPEND (Import CSV).
     * Format : APPEND INTO NomRelation ALLRECORDS (nomFichier.csv)
     */
    private void handleAppend(String command) {
        String[] parts = command.trim().split("\\s+");

        // Vérification syntaxe
        if (parts.length < 5 || !parts[1].equalsIgnoreCase("INTO") || !parts[3].equalsIgnoreCase("ALLRECORDS")) {
            System.out.println("Erreur syntaxe : APPEND INTO <Relation> ALLRECORDS (<Fichier>)");
            return;
        }

        String tableName = parts[2];
        String filePart = parts[4]; // "(S.csv)"

        // Nettoyage du nom de fichier (retirer les parenthèses)
        if (!filePart.startsWith("(") || !filePart.endsWith(")")) {
            System.out.println("Erreur : Le nom du fichier doit être entre parenthèses.");
            return;
        }
        String filename = filePart.substring(1, filePart.length() - 1);

        // Vérification existence table
        if (!tables.containsKey(tableName)) {
            System.out.println("Erreur : Table " + tableName + " inconnue.");
            return;
        }

        Relation rel = tables.get(tableName);
        File csvFile = new File(filename); // Le fichier est supposé être à la racine

        if (!csvFile.exists()) {
            System.out.println("Erreur : Fichier " + filename + " introuvable.");
            return;
        }

        // Lecture et Insertion
        int count = 0;
        try (BufferedReader br = new BufferedReader(new FileReader(csvFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty())
                    continue;

                // Parsing de la ligne CSV (similaire à INSERT, mais sans parenthèses)
                // "val1",val2,val3...
                String[] tokens = line.split(",");

                if (tokens.length != rel.getCols().size()) {
                    System.out.println("Ligne ignorée (nb colonnes incorrect) : " + line);
                    continue;
                }

                Record record = new Record();
                try {
                    for (int i = 0; i < rel.getCols().size(); i++) {
                        String val = tokens[i].trim();
                        ColInfo col = rel.getCols().get(i);

                        switch (col.type) {
                            case INT:
                                record.values.add(Integer.parseInt(val));
                                break;
                            case FLOAT:
                                record.values.add(Float.parseFloat(val));
                                break;
                            case CHAR:
                            case VARCHAR:
                                if (val.startsWith("\"") && val.endsWith("\"")) {
                                    record.values.add(val.substring(1, val.length() - 1));
                                } else {
                                    // Cas particulier : le TP dit que les strings ont des guillemets,
                                    // mais si le CSV est mal formé on peut décider d'être souple ou strict.
                                    // Restons stricts comme demandé.
                                    throw new IllegalArgumentException("String sans guillemets");
                                }
                                break;
                        }
                    }
                    // Insertion
                    rel.InsertRecord(record);
                    count++;
                } catch (Exception e) {
                    System.out.println("Erreur ligne CSV : " + line + " -> " + e.getMessage());
                }
            }
            System.out.println("Import terminé : " + count + " records ajoutés dans " + tableName);

        } catch (IOException e) {
            System.out.println("Erreur lecture fichier : " + e.getMessage());
        }
    }

    /**
     * Gère la commande DELETE.
     * Format : DELETE NomTable alias WHERE ...
     */
    private void handleDelete(String command) {
        // 1. Parsing
        int idxWhere = command.toUpperCase().indexOf(" WHERE ");
        String tablePart = (idxWhere == -1) ? command.substring(7).trim() : command.substring(7, idxWhere).trim();
        String wherePart = (idxWhere == -1) ? null : command.substring(idxWhere).trim();

        String[] tableTokens = tablePart.split("\\s+");
        String tableName = tableTokens[0];
        String alias = (tableTokens.length > 1) ? tableTokens[1] : "";

        if (!tables.containsKey(tableName)) {
            System.out.println("Erreur : Table " + tableName + " inconnue.");
            return;
        }
        Relation rel = tables.get(tableName);

        // 2. Parsing des conditions
        List<donnees.Condition> conditions = null;
        if (wherePart != null) {
            conditions = parseWhereClause(wherePart, rel, alias);
        } else {
            // Si pas de WHERE, on supprime tout ? (Attention, danger !)
            // Pour le TP, on peut accepter ou demander une confirmation.
            // Ici on accepte (conditions = null signifie "tout matcher").
            conditions = new ArrayList<>();
        }

        // 3. Exécution
        int deletedCount = rel.DeleteRecords(conditions);
        System.out.println("Total deleted records = " + deletedCount);
    }

    /**
     * Gère la commande UPDATE avec support multi-colonnes.
     * Format : UPDATE Table SET col1=val1, col2=val2 WHERE ...
     */
    private void handleUpdate(String command) {
        // 1. Découpage basique "UPDATE ... SET ... WHERE ..."
        String[] parts = command.split("(?i) WHERE ");
        String wherePart = (parts.length > 1) ? parts[1] : "";

        String beforeWhere = parts[0];
        String[] updateParts = beforeWhere.split("(?i) SET ");

        if (updateParts.length < 2) {
            System.out.println("Erreur syntaxe : UPDATE <Table> SET <Modifs> [WHERE <Cond>]");
            return;
        }

        // Récupération Table et Alias
        String tablePart = updateParts[0].substring(6).trim(); // Enlève "UPDATE"
        String alias = "";
        String tableName = tablePart;

        if (tablePart.contains(" ")) {
            String[] tSplit = tablePart.split("\\s+");
            tableName = tSplit[0];
            alias = tSplit[1];
        }

        if (!tables.containsKey(tableName)) {
            System.out.println("Erreur : Table " + tableName + " inconnue.");
            return;
        }
        Relation rel = tables.get(tableName);

        // 2. Analyse des modifications (SET col1=v1, col2=v2)
        String setClause = updateParts[1].trim();
        // On sépare par les virgules (Attention si une valeur contient une virgule, ce
        // split simple peut casser,
        // mais pour ce TP on suppose des valeurs simples ou on améliorerait avec un
        // regex)
        String[] assignments = setClause.split(",");

        java.util.Map<Integer, Object> updatesMap = new java.util.HashMap<>();

        for (String assign : assignments) {
            String[] kv = assign.split("=");
            if (kv.length < 2)
                continue;

            String fullColName = kv[0].trim();
            String valStr = kv[1].trim();

            // Gestion alias (t.Age -> Age)
            String colName = fullColName;
            if (!alias.isEmpty() && fullColName.startsWith(alias + ".")) {
                colName = fullColName.substring(alias.length() + 1);
            }

            // Trouver l'index de la colonne
            int colIndex = -1;
            donnees.ColInfo colInfo = null;
            for (int i = 0; i < rel.getCols().size(); i++) {
                if (rel.getCols().get(i).name.equalsIgnoreCase(colName)) {
                    colIndex = i;
                    colInfo = rel.getCols().get(i);
                    break;
                }
            }

            if (colIndex == -1) {
                System.out.println("Erreur : Colonne " + colName + " inconnue.");
                return;
            }

            // Conversion de la valeur
            Object finalVal = null;
            try {
                switch (colInfo.type) {
                    case INT:
                        finalVal = Integer.parseInt(valStr);
                        break;
                    case FLOAT:
                        finalVal = Float.parseFloat(valStr);
                        break;
                    case CHAR:
                    case VARCHAR:
                        if (valStr.startsWith("\"") && valStr.endsWith("\""))
                            finalVal = valStr.substring(1, valStr.length() - 1);
                        else
                            finalVal = valStr; // Ou erreur si on est strict
                        break;
                }
                updatesMap.put(colIndex, finalVal);
            } catch (Exception e) {
                System.out.println("Erreur de type pour la colonne " + colName);
                return;
            }
        }

        // 3. Analyse du WHERE
        List<donnees.Condition> conditions = null;
        if (!wherePart.isEmpty()) {
            conditions = parseWhereClause("WHERE " + wherePart, rel, alias);
        }

        // 4. Exécution
        int count = rel.UpdateRecords(conditions, updatesMap);
        System.out.println("Total updated records = " + count);
    }

    /**
     * Affiche la liste des tables et leurs schémas (Pour DESCRIBE TABLES).
     */
    private void handleDescribeTables() {
        System.out.println("--- Description des tables (" + tables.size() + ") ---");
        for (Map.Entry<String, Relation> entry : tables.entrySet()) {
            String name = entry.getKey();
            Relation rel = entry.getValue();

            // On reconstruit l'affichage style "Nom (Col1:Type, Col2:Type...)"
            StringBuilder sb = new StringBuilder(name).append(" (");
            List<ColInfo> cols = rel.getCols();

            for (int i = 0; i < cols.size(); i++) {
                ColInfo c = cols.get(i);
                sb.append(c.name).append(":").append(c.type);
                if (c.length > 0)
                    sb.append("(").append(c.length).append(")");

                if (i < cols.size() - 1)
                    sb.append(", ");
            }
            sb.append(")");
            System.out.println(sb.toString());
        }
    }

    /**
     * Supprime TOUTES les tables (Pour DROP TABLES).
     */
    private void handleDropAllTables() {
        // On fait une copie des noms pour ne pas modifier la map pendant qu'on la
        // parcourt
        List<String> tableNames = new ArrayList<>(tables.keySet());

        for (String name : tableNames) {
            // On réutilise la logique de suppression propre (libération disque, etc.)
            // Note: handleDrop attend une commande string, on va appeler la logique interne
            // directement
            // ou simuler la commande. Pour faire simple et propre, on extrait la logique :

            Relation rel = tables.get(name);
            if (rel != null) {
                // 1. Libérer les pages
                for (espaceDisque.PageId pid : rel.getDataPages()) {
                    diskManager.DeallocPage(pid);
                }
            }
        }
        // 2. Vider le catalogue
        tables.clear();
        System.out.println("Toutes les tables ont été supprimées.");
    }

    /**
     * Analyse une clause WHERE et retourne la liste des conditions.
     * Exemple : "WHERE t.Age > 18 AND t.Nom = \"Toto\""
     */
    private List<donnees.Condition> parseWhereClause(String wherePart, Relation rel, String alias) {
        List<donnees.Condition> conditions = new ArrayList<>();

        String cleanWhere = wherePart.trim();
        if (cleanWhere.toUpperCase().startsWith("WHERE")) {
            cleanWhere = cleanWhere.substring(5).trim();
        }

        // On sépare les conditions par " AND "
        String[] condsStr = cleanWhere.split("\\s+AND\\s+");

        for (String condStr : condsStr) {
            String opStr = null;
            donnees.Condition.Operator op = null;

            // Détection de l'opérateur
            if (condStr.contains("<=")) {
                opStr = "<=";
                op = donnees.Condition.Operator.LEQ;
            } else if (condStr.contains(">=")) {
                opStr = ">=";
                op = donnees.Condition.Operator.GEQ;
            } else if (condStr.contains("<>")) {
                opStr = "<>";
                op = donnees.Condition.Operator.NEQ;
            } else if (condStr.contains("=")) {
                opStr = "=";
                op = donnees.Condition.Operator.EQ;
            } else if (condStr.contains("<")) {
                opStr = "<";
                op = donnees.Condition.Operator.LT;
            } else if (condStr.contains(">")) {
                opStr = ">";
                op = donnees.Condition.Operator.GT;
            }

            if (op == null)
                continue;

            String[] terms = condStr.split(java.util.regex.Pattern.quote(opStr));
            if (terms.length < 2)
                continue;

            String left = terms[0].trim();
            String right = terms[1].trim();

            String colName = null;
            String valueStr = null;

            // 1. Déterminer qui est la colonne principale (Gauche ou Droite ?)
            boolean leftIsCol = false;
            String cleanLeft = left;
            if (!alias.isEmpty() && left.toUpperCase().startsWith(alias.toUpperCase() + "."))
                cleanLeft = left.substring(alias.length() + 1);

            for (donnees.ColInfo c : rel.getCols()) {
                if (c.name.equalsIgnoreCase(cleanLeft)) {
                    leftIsCol = true;
                    colName = c.name;
                    break;
                }
            }

            if (leftIsCol) {
                valueStr = right; // Cas standard : C1 > 10
            } else {
                // Cas inversé : 10 < C1
                colName = right;
                if (!alias.isEmpty() && right.toUpperCase().startsWith(alias.toUpperCase() + "."))
                    colName = right.substring(alias.length() + 1);
                valueStr = left;

                // Inversion de l'opérateur
                switch (op) {
                    case LEQ:
                        op = donnees.Condition.Operator.GEQ;
                        break;
                    case GEQ:
                        op = donnees.Condition.Operator.LEQ;
                        break;
                    case LT:
                        op = donnees.Condition.Operator.GT;
                        break;
                    case GT:
                        op = donnees.Condition.Operator.LT;
                        break;
                    default:
                        break;
                }
            }

            // 2. Trouver l'index de la première colonne
            int colIndex = -1;
            donnees.ColInfo colInfo = null;
            for (int i = 0; i < rel.getCols().size(); i++) {
                if (rel.getCols().get(i).name.equalsIgnoreCase(colName)) {
                    colIndex = i;
                    colInfo = rel.getCols().get(i);
                    break;
                }
            }

            if (colIndex != -1) {
                // 3. Vérifier si la partie "Valeur" est EN FAIT une autre colonne (TP : C4 >
                // C2)
                boolean valueIsCol = false;
                int otherColIndex = -1;

                String cleanVal = valueStr;
                if (!alias.isEmpty() && valueStr.toUpperCase().startsWith(alias.toUpperCase() + ".")) {
                    cleanVal = valueStr.substring(alias.length() + 1);
                }

                for (int k = 0; k < rel.getCols().size(); k++) {
                    if (rel.getCols().get(k).name.equalsIgnoreCase(cleanVal)) {
                        valueIsCol = true;
                        otherColIndex = k;
                        break;
                    }
                }

                try {
                    if (valueIsCol) {
                        // --- CAS COLONNE vs COLONNE ---
                        // On passe l'index de la 2ème colonne.
                        // ATTENTION: Il faut que ta classe Condition accepte ça !
                        // Si tu n'as pas de constructeur spécial, on passe l'index en String
                        // et on espère que ton Condition.java gère ça, ou tu devras le modifier.

                        // ICI : On suppose que tu as un constructeur ou que tu gères ça
                        // Pour le moment, on ajoute une condition spéciale
                        System.out.println(
                                "[DEBUG] Condition Col vs Col détectée : " + colName + " " + op + " " + cleanVal);

                        // HACK : Si ta classe Condition ne prend que (int, op, String),
                        // tu devras modifier Condition.java pour qu'elle sache comparer deux colonnes.
                        // Je te donne le code générique ici :
                        conditions.add(new donnees.Condition(colIndex, op, otherColIndex, true));

                    } else {
                        // --- CAS STANDARD (Valeur constante) ---
                        Object val = null;
                        switch (colInfo.type) {
                            case INT:
                                val = Integer.parseInt(valueStr);
                                break;
                            case FLOAT:
                                val = Float.parseFloat(valueStr);
                                break;
                            case CHAR:
                            case VARCHAR:
                                if (valueStr.startsWith("\"") && valueStr.endsWith("\""))
                                    val = valueStr.substring(1, valueStr.length() - 1);
                                else
                                    val = valueStr;
                                break;
                        }
                        // Constructeur standard
                        conditions.add(new donnees.Condition(colIndex, op, val.toString()));
                    }
                } catch (Exception e) {
                    System.out
                            .println("[DEBUG] Erreur conversion valeur (" + valueStr + ") pour la colonne " + colName);
                }
            }
        }
        return conditions;
    }
}