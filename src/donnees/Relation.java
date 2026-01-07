package donnees;

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import espaceDisque.BufferManager;
import espaceDisque.DBConfig;
import espaceDisque.DiskManager;
import espaceDisque.PageId;

public class Relation implements Serializable {

    private String name;
    private List<ColInfo> cols;
    // 3. IMPORTANT : transient = "Ne pas sauvegarder ça dans le fichier"
    private transient DiskManager diskManager;
    private transient BufferManager bufferManager;
    private PageId headerPageId;
    private DBConfig dbConfig;
    private static final long serialVersionUID = 1L;
    // 2 PageId (Next + Prev) * 2 int * 4 octets = 16 octets
    private static final int HEADER_PAGE_SIZE = 16;

    /**
     * Constructeur
     * 
     * @param name         Nom de la table
     * @param cols         Liste des colonnes
     * @param dm           Référence au DiskManager
     * @param bm           Référence au BufferManager
     * @param headerPageId PageId de la HeaderPage (peut être null au début)
     * @param dbConfig     DBConfig
     */
    public Relation(String name, List<ColInfo> cols, DiskManager dm, BufferManager bm, PageId headerPageId,
            DBConfig config) {
        this.name = name;
        this.cols = cols;
        this.diskManager = dm;
        this.bufferManager = bm;
        this.headerPageId = headerPageId;
        this.dbConfig = config;
    }

    /**
     * Écrit les valeurs du Record dans le buffer à la position donnée.
     * 
     * @param record Le tuple à écrire.
     * @param buff   Le buffer cible (déjà alloué).
     * @param pos    La position (offset) où commencer à écrire dans le buffer.
     * @return La nouvelle position après écriture (utile pour enchaîner).
     */
    public int writeRecordToBuffer(Record record, ByteBuffer buff, int pos) {
        // On se place au bon endroit dans le buffer
        buff.position(pos);

        for (int i = 0; i < cols.size(); i++) {
            ColInfo col = cols.get(i);
            Object val = record.values.get(i);

            switch (col.type) {
                case INT:
                    buff.putInt((Integer) val); // Écrit 4 octets
                    break;

                case FLOAT:
                    buff.putFloat((Float) val); // Écrit 4 octets
                    break;

                case CHAR:
                    // CHAR(T) : Taille fixe. On écrit T caractères.
                    String sChar = (String) val;
                    for (int k = 0; k < col.length; k++) {
                        if (k < sChar.length()) {
                            buff.putChar(sChar.charAt(k));
                        } else {
                            buff.putChar(' '); // Padding (espace)
                        }
                    }
                    break;

                case VARCHAR:
                    // VARCHAR(T) : Taille variable.
                    String sVar = (String) val;
                    buff.putInt(sVar.length()); // Écriture de la taille
                    for (int k = 0; k < sVar.length(); k++) {
                        buff.putChar(sVar.charAt(k));
                    }
                    break;
            }
        }
        return buff.position(); // Retourne la position finale
    }

    // --- PARTIE F : LECTURE (Buffer -> Record) ---
    /**
     * Lit un Record depuis le buffer à la position donnée.
     * 
     * @param record Un record vide à remplir.
     * @param buff   Le buffer source.
     * @param pos    La position où lire.
     * @return La nouvelle position après lecture.
     */
    public int readFromBuffer(Record record, ByteBuffer buff, int pos) {
        buff.position(pos);
        record.values.clear(); // On vide le record au cas où

        for (int i = 0; i < cols.size(); i++) {
            ColInfo col = cols.get(i);

            switch (col.type) {
                case INT:
                    record.values.add(buff.getInt());
                    break;

                case FLOAT:
                    record.values.add(buff.getFloat());
                    break;

                case CHAR:
                    // Lecture de T caractères pour reconstruire la String
                    StringBuilder sb = new StringBuilder();
                    for (int k = 0; k < col.length; k++) {
                        sb.append(buff.getChar());
                    }
                    record.values.add(sb.toString().trim());
                    break;

                case VARCHAR:
                    // Lecture de la taille d'abord
                    int size = buff.getInt();
                    StringBuilder sbVar = new StringBuilder();
                    for (int k = 0; k < size; k++) {
                        sbVar.append(buff.getChar());
                    }
                    record.values.add(sbVar.toString());
                    break;
            }
        }
        return buff.position();
    }

    // Getters utiles
    public List<ColInfo> getCols() {
        return cols;
    }

    // --- TP5 : Gestion de la Taille et des Slots ---

    /**
     * Calcule la taille MAXIMALE d'un record en octets (pour réserver l'espace).
     * 
     * @return Taille en octets.
     */
    private int getRecordMaxSize() {
        int size = 0;
        for (ColInfo col : cols) {
            switch (col.type) {
                case INT:
                case FLOAT:
                    size += 4; // 4 octets pour int/float
                    break;
                case CHAR:
                    size += col.length * 2;
                    break;
                case VARCHAR:
                    size += 4 + (col.length * 2);
                    break;
            }
        }
        return size;
    }

    /**
     * Calcule combien de slots (records) peuvent tenir dans une page.
     * Formule : SlotCount = PageSize / (RecordMaxSize + 1 octet de bytemap)
     * 
     * @return Nombre de slots par page.
     */
    public int getSlotCount() {
        return (dbConfig.pagesize - HEADER_PAGE_SIZE) / (getRecordMaxSize() + 1);
    }

    // --- TP5 PARTIE C : Gestion de la Page de Données ---

    /**
     * Tente d'écrire un record dans une page de données spécifique.
     */
    public RecordId writeRecordToDataPage(Record record, PageId pageId) {
        byte[] rawPage = bufferManager.GetPage(pageId);
        ByteBuffer pageBuffer = ByteBuffer.wrap(rawPage);

        int maxSlot = getSlotCount();
        int recordSize = getRecordMaxSize();
        int slotTrouve = -1;

        // Parcours de la Bytemap
        for (int i = 0; i < maxSlot; i++) {
            if (pageBuffer.get(HEADER_PAGE_SIZE + i) == 0) { // 0 = Libre
                slotTrouve = i;
                break;
            }
        }

        if (slotTrouve == -1) {
            bufferManager.FreePage(pageId, false);
            return null;
        }

        // A. Mise à jour de la Bytemap
        pageBuffer.position(HEADER_PAGE_SIZE + slotTrouve);
        pageBuffer.put((byte) 1); // On marque occupé

        // B. Calcul de la position du slot
        int offset = HEADER_PAGE_SIZE + maxSlot + (slotTrouve * recordSize);

        // C. Écriture des données
        writeRecordToBuffer(record, pageBuffer, offset);

        bufferManager.FreePage(pageId, true);

        return new RecordId(pageId, slotTrouve);
    }

    /**
     * Récupère tous les records présents dans une page.
     */
    public List<Record> getRecordsInDataPage(PageId pageId) {
        List<Record> resultList = new ArrayList<>();

        byte[] rawPage = bufferManager.GetPage(pageId);
        ByteBuffer pageBuffer = ByteBuffer.wrap(rawPage);

        int maxSlot = getSlotCount();
        int recordSize = getRecordMaxSize();

        // On parcourt la bytemap
        for (int i = 0; i < maxSlot; i++) {
            // Si l'octet vaut 1, il y a un record
            if (pageBuffer.get(HEADER_PAGE_SIZE + i) == 1) {
                int offset = HEADER_PAGE_SIZE + maxSlot + (i * recordSize);

                Record rec = new Record();
                readFromBuffer(rec, pageBuffer, offset);
                resultList.add(rec);
            }
        }

        bufferManager.FreePage(pageId, false);
        return resultList;
    }

    // --- TP5 : GESTION DU HEAP FILE (Multi-Pages) ---

    public void createHeaderPage() {
        this.headerPageId = diskManager.AllocPage();

        byte[] data = new byte[dbConfig.pagesize];
        ByteBuffer buffer = ByteBuffer.wrap(data);

        buffer.putInt(-1);
        buffer.putInt(-1); // FreePageId
        buffer.putInt(-1);
        buffer.putInt(-1); // FullPageId

        diskManager.WritePage(this.headerPageId, data);
    }

    /**
     * Ajoute une NOUVELLE page de données à la relation.
     * Cette page sera ajoutée en tête de la liste des pages libres ("Free List").
     */
    public PageId addDataPage() {
        PageId newPageId = diskManager.AllocPage();

        // 1. Lire la Header Page
        byte[] headerData = bufferManager.GetPage(this.headerPageId);
        ByteBuffer headerBuff = ByteBuffer.wrap(headerData);

        int oldFreeFile = headerBuff.getInt(0);
        int oldFreePage = headerBuff.getInt(4);

        // 2. Préparer la nouvelle page
        byte[] newPageData = bufferManager.GetPage(newPageId);
        ByteBuffer newPageBuff = ByteBuffer.wrap(newPageData);

        // --- Header de la page ---
        newPageBuff.putInt(0, -1); // Prev Page
        newPageBuff.putInt(4, -1);
        newPageBuff.putInt(8, oldFreeFile); // Next Page
        newPageBuff.putInt(12, oldFreePage);

        // --- FIX CRITIQUE : Nettoyage de la Bytemap ---
        // On remplit la zone de la bytemap avec des 0 pour éviter de lire du "garbage"
        int maxSlot = getSlotCount();
        for (int i = 0; i < maxSlot; i++) {
            newPageBuff.put(HEADER_PAGE_SIZE + i, (byte) 0);
        }
        // ----------------------------------------------

        bufferManager.FreePage(newPageId, true); // On sauve la nouvelle page

        // 3. Mise à jour de l'ancienne page (chainage)
        if (oldFreeFile != -1) {
            PageId oldPageId = new PageId(oldFreeFile, oldFreePage);
            byte[] oldPageData = bufferManager.GetPage(oldPageId);
            ByteBuffer oldBuff = ByteBuffer.wrap(oldPageData);

            oldBuff.putInt(0, newPageId.FileIdx);
            oldBuff.putInt(4, newPageId.PageIdx);

            bufferManager.FreePage(oldPageId, true);
        }

        // 4. Mettre à jour la Header Page
        headerBuff.putInt(0, newPageId.FileIdx);
        headerBuff.putInt(4, newPageId.PageIdx);

        bufferManager.FreePage(this.headerPageId, true);

        return newPageId;
    }

    // ==========================================
    // MÉTHODES TP5 : LECTURE (SCAN)
    // ==========================================

    public List<PageId> getDataPages() {
        List<PageId> pageIds = new ArrayList<>();

        byte[] headerData = bufferManager.GetPage(this.headerPageId);
        ByteBuffer headerBuff = ByteBuffer.wrap(headerData);

        int freeFile = headerBuff.getInt(0);
        int freePage = headerBuff.getInt(4);
        int fullFile = headerBuff.getInt(8);
        int fullPage = headerBuff.getInt(12);

        bufferManager.FreePage(this.headerPageId, false);

        traverseList(new PageId(freeFile, freePage), pageIds);
        traverseList(new PageId(fullFile, fullPage), pageIds);

        return pageIds;
    }

    private void traverseList(PageId startId, List<PageId> result) {
        PageId currentId = startId;

        while (currentId.FileIdx != -1 && currentId.PageIdx != -1) {
            result.add(currentId);

            byte[] data = bufferManager.GetPage(currentId);
            ByteBuffer buff = ByteBuffer.wrap(data);

            int nextFile = buff.getInt(8);
            int nextPage = buff.getInt(12);

            bufferManager.FreePage(currentId, false);

            currentId = new PageId(nextFile, nextPage);
        }
    }

    public List<Record> GetAllRecords() {
        List<Record> allRecords = new ArrayList<>();
        List<PageId> allPages = getDataPages();

        for (PageId pid : allPages) {
            allRecords.addAll(getRecordsInDataPage(pid));
        }
        return allRecords;
    }

    // ==========================================
    // MÉTHODES TP5 : INSERTION INTELLIGENTE
    // ==========================================

    public RecordId InsertRecord(Record record) {
        while (true) {
            byte[] headerData = bufferManager.GetPage(this.headerPageId);
            ByteBuffer headerBuff = ByteBuffer.wrap(headerData);

            int freeFile = headerBuff.getInt(0);
            int freePage = headerBuff.getInt(4);

            bufferManager.FreePage(this.headerPageId, false);

            if (freeFile == -1) {
                addDataPage();
                continue;
            }

            PageId freePageId = new PageId(freeFile, freePage);

            RecordId rid = writeRecordToDataPage(record, freePageId);

            if (rid != null) {
                return rid;
            } else {
                moveFirstFreePageToFullList();
            }
        }
    }

    private void moveFirstFreePageToFullList() {
        // 1. Lire le Header
        byte[] headerData = bufferManager.GetPage(this.headerPageId);
        ByteBuffer headerBuff = ByteBuffer.wrap(headerData);

        int pFile = headerBuff.getInt(0);
        int pPage = headerBuff.getInt(4);
        int fullFile = headerBuff.getInt(8);
        int fullPage = headerBuff.getInt(12);

        PageId pId = new PageId(pFile, pPage);

        // 2. Lire la page P
        byte[] pData = bufferManager.GetPage(pId);
        ByteBuffer pBuff = ByteBuffer.wrap(pData);

        int nextFile = pBuff.getInt(8);
        int nextPage = pBuff.getInt(12);

        // 3. Mettre à jour Header (Free -> Suivant de P)
        headerBuff.putInt(0, nextFile);
        headerBuff.putInt(4, nextPage);

        if (nextFile != -1) {
            PageId nextId = new PageId(nextFile, nextPage);
            byte[] nextData = bufferManager.GetPage(nextId);
            ByteBuffer nextBuff = ByteBuffer.wrap(nextData);
            nextBuff.putInt(0, -1);
            nextBuff.putInt(4, -1);
            bufferManager.FreePage(nextId, true);
        }

        // 4. Insérer P en tête de Full
        pBuff.putInt(8, fullFile); // P.Next = Ancien Full
        pBuff.putInt(12, fullPage);
        pBuff.putInt(0, -1); // P.Prev = null

        if (fullFile != -1) {
            PageId oldFullId = new PageId(fullFile, fullPage);
            byte[] oldFullData = bufferManager.GetPage(oldFullId);
            ByteBuffer oldFullBuff = ByteBuffer.wrap(oldFullData);
            oldFullBuff.putInt(0, pFile);
            oldFullBuff.putInt(4, pPage);
            bufferManager.FreePage(oldFullId, true);
        }

        // 5. Mettre à jour Header (Full -> P)
        headerBuff.putInt(8, pFile);
        headerBuff.putInt(12, pPage);

        bufferManager.FreePage(pId, true);
        bufferManager.FreePage(this.headerPageId, true);
    }

    public void setManagers(DiskManager dm, BufferManager bm) {
        this.diskManager = dm;
        this.bufferManager = bm;
    }

    // --- SUPPRESSION & UPDATE ---

    public int DeleteRecords(List<Condition> conditions) {
        int count = 0;
        List<PageId> pages = getDataPages();

        for (PageId pid : pages) {
            byte[] rawPage = bufferManager.GetPage(pid);
            ByteBuffer pageBuffer = ByteBuffer.wrap(rawPage);
            int maxSlot = getSlotCount();
            int recordSize = getRecordMaxSize();
            boolean pageModified = false;

            for (int i = 0; i < maxSlot; i++) {
                if (pageBuffer.get(HEADER_PAGE_SIZE + i) == 1) {
                    int offset = HEADER_PAGE_SIZE + maxSlot + (i * recordSize);
                    Record rec = new Record();
                    readFromBuffer(rec, pageBuffer, offset);

                    boolean match = true;
                    if (conditions != null) {
                        for (Condition cond : conditions) {
                            if (!cond.evaluate(rec)) {
                                match = false;
                                break;
                            }
                        }
                    }

                    if (match) {
                        pageBuffer.position(HEADER_PAGE_SIZE + i);
                        pageBuffer.put((byte) 0);
                        pageModified = true;
                        count++;
                    }
                }
            }
            bufferManager.FreePage(pid, pageModified);
        }
        return count;
    }

    /**
     * Met à jour les records correspondant aux conditions avec PLUSIEURS valeurs.
     * 
     * @param conditions Liste des conditions WHERE
     * @param updates    Map contenant {IndexColonne : NouvelleValeur}
     * @return Nombre de records modifiés
     */
    public int UpdateRecords(java.util.List<Condition> conditions, java.util.Map<Integer, Object> updates) {
        int count = 0;
        java.util.List<espaceDisque.PageId> pages = getDataPages();

        for (espaceDisque.PageId pid : pages) {
            byte[] rawPage = bufferManager.GetPage(pid);
            ByteBuffer pageBuffer = ByteBuffer.wrap(rawPage);
            int maxSlot = getSlotCount();
            int recordSize = getRecordMaxSize();
            boolean pageModified = false;

            for (int i = 0; i < maxSlot; i++) {
                if (pageBuffer.get(HEADER_PAGE_SIZE + i) == 1) {
                    int offset = HEADER_PAGE_SIZE + maxSlot + (i * recordSize);
                    Record rec = new Record();
                    readFromBuffer(rec, pageBuffer, offset);

                    boolean match = true;
                    if (conditions != null) {
                        for (Condition cond : conditions) {
                            if (!cond.evaluate(rec)) {
                                match = false;
                                break;
                            }
                        }
                    }

                    if (match) {
                        // On applique TOUTES les modifications demandées
                        for (java.util.Map.Entry<Integer, Object> entry : updates.entrySet()) {
                            rec.values.set(entry.getKey(), entry.getValue());
                        }

                        writeRecordToBuffer(rec, pageBuffer, offset);
                        pageModified = true;
                        count++;
                    }
                }
            }
            bufferManager.FreePage(pid, pageModified);
        }
        return count;
    }

    // --- MÉTHODE DE COMPATIBILITÉ (POUR LES VIEUX TESTS) ---
    // Permet d'appeler UpdateRecords avec une seule valeur comme avant.
    // Elle transforme l'appel simple en appel "Map" automatiquement.
    public int UpdateRecords(java.util.List<Condition> conditions, int colIndex, Object newValue) {
        java.util.Map<Integer, Object> singleUpdate = new java.util.HashMap<>();
        singleUpdate.put(colIndex, newValue);
        return UpdateRecords(conditions, singleUpdate);
    }
}