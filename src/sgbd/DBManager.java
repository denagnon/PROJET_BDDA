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
        if (parts.length == 0) return;

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
                    
                case "LIST":
                    // Gestion de LIST TABLE
                    if (parts.length > 1 && parts[1].equalsIgnoreCase("TABLES")) {
                        handleListTables();
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
        if (parts.length < 3) return;
        String tableName = parts[2];

        if (tables.containsKey(tableName)) {
            System.out.println("Erreur : La table " + tableName + " existe déjà.");
            return;
        }

        int openParen = command.indexOf('(');
        int closeParen = command.lastIndexOf(')');
        if (openParen == -1 || closeParen == -1) return;

        String schemaStr = command.substring(openParen + 1, closeParen);
        String[] colDefs = schemaStr.split(",");
        List<ColInfo> columns = new ArrayList<>();

        for (String colDef : colDefs) {
            String[] colParts = colDef.trim().split(":");
            String colName = colParts[0].trim();
            String typeStr = colParts[1].trim().toUpperCase();
            
            //Alias REAL -> FLOAT ---
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
        // On utilise le nom complet 'espaceDisque.PageId' pour éviter les erreurs d'import
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

        // 5. Création du Record
        donnees.Record record = new donnees.Record();
        try {
            for (int i = 0; i < rel.getCols().size(); i++) {
                donnees.ColInfo col = rel.getCols().get(i);
                
                // Cela enlève les espaces parasites (ex: " 20" devient "20")
                String token = valTokens[i].trim(); 
                // -----------------------------------------

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
     * Ou     : SELECT * FROM NomTable alias WHERE ...
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
                        System.out.println("[DEBUG] Colonne trouvée : " + rel.getCols().get(i).name + " (Index " + i + ")");
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
   
                if (i < rec.values.size() - 1) sb.append(" ; ");
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
                if (line.isEmpty()) continue;

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
     * Gère la commande UPDATE.
     * Format : UPDATE NomTable alias SET alias.Col = Val WHERE ...
     */
    private void handleUpdate(String command) {
        // Découpage
        int idxSet = command.toUpperCase().indexOf(" SET ");
        int idxWhere = command.toUpperCase().indexOf(" WHERE ");
        
        if (idxSet == -1 || idxWhere == -1) {
            System.out.println("Erreur syntaxe : UPDATE ... SET ... WHERE ...");
            return;
        }
        
        String tablePart = command.substring(7, idxSet).trim();
        String setPart = command.substring(idxSet + 5, idxWhere).trim(); // "alias.Col = Val"
        String wherePart = command.substring(idxWhere).trim();
        
        // 1. Table
        String[] tableTokens = tablePart.split("\\s+");
        String tableName = tableTokens[0];
        String alias = (tableTokens.length > 1) ? tableTokens[1] : "";
        
        if (!tables.containsKey(tableName)) return;
        Relation rel = tables.get(tableName);
        
        // 2. SET (alias.Col = Val)
        String[] setTokens = setPart.split("=");
        if (setTokens.length != 2) return;
        
        String colFullName = setTokens[0].trim();
        String valStr = setTokens[1].trim();
        
        String colName = colFullName;
        if (alias.length() > 0 && colFullName.startsWith(alias + ".")) {
            colName = colFullName.substring(alias.length() + 1);
        }
        
        // Trouver la colonne et convertir la valeur
        int colIndex = -1;
        Object newValue = null;
        
        for (int i = 0; i < rel.getCols().size(); i++) {
            if (rel.getCols().get(i).name.equals(colName)) {
                colIndex = i;
                donnees.ColInfo col = rel.getCols().get(i);
                try {
                    switch(col.type) {
                        case INT: newValue = Integer.parseInt(valStr); break;
                        case FLOAT: newValue = Float.parseFloat(valStr); break;
                        case CHAR:
                        case VARCHAR: 
                            if(valStr.startsWith("\"")) newValue = valStr.substring(1, valStr.length()-1);
                            else newValue = valStr;
                            break;
                    }
                } catch(Exception e) { return; }
                break;
            }
        }
        
        if (colIndex == -1) return;

        // 3. WHERE
        java.util.List<donnees.Condition> conditions = parseWhereClause(wherePart, rel, alias);
        
        // 4. Exécution
        int count = rel.UpdateRecords(conditions, colIndex, newValue);
        System.out.println("Total updated records = " + count);
    }
    /**
     * Affiche la liste des tables et leurs schémas (Pour LIST TABLES).
     */
    private void handleListTables() {
        System.out.println("--- Liste des tables (" + tables.size() + ") ---");
        for (Map.Entry<String, Relation> entry : tables.entrySet()) {
            String name = entry.getKey();
            Relation rel = entry.getValue();
            
            // On reconstruit l'affichage style "Nom (Col1:Type, Col2:Type...)"
            StringBuilder sb = new StringBuilder(name).append(" (");
            List<ColInfo> cols = rel.getCols();
            
            for (int i = 0; i < cols.size(); i++) {
                ColInfo c = cols.get(i);
                sb.append(c.name).append(":").append(c.type);
                if (c.length > 0) sb.append("(").append(c.length).append(")");
                
                if (i < cols.size() - 1) sb.append(", ");
            }
            sb.append(")");
            System.out.println(sb.toString());
        }
    }

    /**
     * Supprime TOUTES les tables (Pour DROP TABLES).
     */
    private void handleDropAllTables() {
        // On fait une copie des noms pour ne pas modifier la map pendant qu'on la parcourt
        List<String> tableNames = new ArrayList<>(tables.keySet());
        
        for (String name : tableNames) {
            // On réutilise la logique de suppression propre (libération disque, etc.)
            // Note: handleDrop attend une commande string, on va appeler la logique interne directement
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
        
        String[] condsStr = cleanWhere.split("\\s+AND\\s+");
        
        for (String condStr : condsStr) {
            String opStr = null;
            donnees.Condition.Operator op = null;
            
            if (condStr.contains("<=")) { opStr = "<="; op = donnees.Condition.Operator.LEQ; }
            else if (condStr.contains(">=")) { opStr = ">="; op = donnees.Condition.Operator.GEQ; }
            else if (condStr.contains("<>")) { opStr = "<>"; op = donnees.Condition.Operator.NEQ; }
            else if (condStr.contains("=")) { opStr = "="; op = donnees.Condition.Operator.EQ; }
            else if (condStr.contains("<")) { opStr = "<"; op = donnees.Condition.Operator.LT; }
            else if (condStr.contains(">")) { opStr = ">"; op = donnees.Condition.Operator.GT; }
            
            if (op == null) continue;
            
            String[] terms = condStr.split(java.util.regex.Pattern.quote(opStr));
            String left = terms[0].trim();
            String right = terms[1].trim();
            
            String colName = null;
            String valueStr = null;
            
            // Logique de détection flexible (Gauche ou Droite, Alias ou pas)
            boolean leftIsCol = false;
            String cleanLeft = left;
            if(!alias.isEmpty() && left.toUpperCase().startsWith(alias.toUpperCase() + ".")) 
                cleanLeft = left.substring(alias.length() + 1);
            
            for(donnees.ColInfo c : rel.getCols()) {
                if(c.name.equalsIgnoreCase(cleanLeft)) { leftIsCol = true; colName = c.name; break; }
            }

            if (leftIsCol) {
                valueStr = right;
            } else {
                // On suppose que c'est l'inverse (1 = C1)
                colName = right;
                if(!alias.isEmpty() && right.toUpperCase().startsWith(alias.toUpperCase() + "."))
                    colName = right.substring(alias.length() + 1);
                valueStr = left;
            }
            
            // Récupération de l'index et conversion
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
                try {
                    Object val = null;
                    switch (colInfo.type) {
                        case INT: val = Integer.parseInt(valueStr); break;
                        case FLOAT: val = Float.parseFloat(valueStr); break;
                        case CHAR:
                        case VARCHAR: 
                            if (valueStr.startsWith("\"")) val = valueStr.substring(1, valueStr.length()-1); 
                            else val = valueStr;
                            break;
                    }
                    conditions.add(new donnees.Condition(colIndex, op, val.toString()));
                    System.out.println("[DEBUG] Condition ajoutée : ColIndex=" + colIndex + " Op=" + op + " Val=" + val);
                } catch (Exception e) {
                    System.out.println("[DEBUG] Erreur conversion valeur : " + valueStr);
                }
            } else {
                 System.out.println("[DEBUG] Colonne introuvable pour la condition : " + condStr);
            }
        }
        return conditions;
    }
}