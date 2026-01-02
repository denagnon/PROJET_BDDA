package donnees;

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import espaceDisque.BufferManager;
import espaceDisque.DBConfig;
import espaceDisque.DiskManager;
import espaceDisque.PageId;


public class Relation implements Serializable{

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
     * @param name Nom de la table
     * @param cols Liste des colonnes
     * @param dm Référence au DiskManager
     * @param bm Référence au BufferManager
     * @param headerPageId PageId de la HeaderPage (peut être null au début)
     * @param dbConfig DBConfig 
     */
    public Relation(String name, List<ColInfo> cols, DiskManager dm, BufferManager bm, PageId headerPageId, DBConfig config) {
        this.name = name;
        this.cols = cols;
        this.diskManager = dm;
        this.bufferManager = bm;
        this.headerPageId = headerPageId;
        this.dbConfig = config; 
    }

    /**
     * Écrit les valeurs du Record dans le buffer à la position donnée.
     * @param record Le tuple à écrire.
     * @param buff Le buffer cible (déjà alloué).
     * @param pos La position (offset) où commencer à écrire dans le buffer.
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
                    // Si la string est trop courte, on comble. Trop longue, on coupe.
                    String sChar = (String) val;
                    // On s'assure d'écrire exactement 'length' caractères
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
                    // On écrit d'abord la taille (int), puis les caractères.
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
     * @param record Un record vide à remplir.
     * @param buff Le buffer source.
     * @param pos La position où lire.
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
                    // Optionnel : on peut retirer les espaces de padding à la fin (.trim())
                    record.values.add(sb.toString().trim()); 
                    // record.values.add(sb.toString()); 
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
                    // En Java, un char fait 2 octets (UTF-16)
                    size += col.length * 2;
                    break;
                case VARCHAR:
                    // Taille (int = 4) + Contenu max (length * 2)
                    size += 4 + (col.length * 2);
                    break;
            }
        }
        return size;
    }

    /**
     * Calcule combien de slots (records) peuvent tenir dans une page.
     * Formule : SlotCount = PageSize / (RecordMaxSize + 1 octet de bytemap)
     * @return Nombre de slots par page.
     */
    public int getSlotCount() {
        // Formule : (PageSize - 16 octets de liens) / (RecordSize + 1 octet bytemap)
        return (dbConfig.pagesize - HEADER_PAGE_SIZE) / (getRecordMaxSize() + 1);
    }
    
 // --- TP5 PARTIE C : Gestion de la Page de Données ---

    /**
     * Tente d'écrire un record dans une page de données spécifique.
     * @param record Le record à écrire.
     * @param pageId L'identifiant de la page où on veut écrire.
     * @return Le RecordId si succès, ou null si la page est pleine.
     */
    public RecordId writeRecordToDataPage(Record record, PageId pageId) {
        // 1. On récupère la page via le BufferManager
        // Attention : on récupère un byte[] qu'on doit wrapper dans un ByteBuffer
        byte[] rawPage = bufferManager.GetPage(pageId);
        ByteBuffer pageBuffer = ByteBuffer.wrap(rawPage);

        // 2. Calculs préliminaires
        int maxSlot = getSlotCount();
        int recordSize = getRecordMaxSize();
        int slotTrouve = -1;

        // 3. Parcours de la Bytemap (les 'maxSlot' premiers octets DISPONIBLES)
        // CORRECTION IMPORTANTE : La Bytemap commence APRÈS le Header (16 octets)
        for (int i = 0; i < maxSlot; i++) {
            // On vérifie l'octet à la position (HEADER + i)
            if (pageBuffer.get(HEADER_PAGE_SIZE + i) == 0) { // 0 = Libre
                slotTrouve = i;
                break;
            }
        }

        if (slotTrouve == -1) {
            // Page pleine ! On libère la page sans la modifier (dirty=false)
            bufferManager.FreePage(pageId, false);
            return null; 
        }

        // 4. Écriture
        // A. Mise à jour de la Bytemap
        // On se place après le header, à l'index du slot trouvé
        pageBuffer.position(HEADER_PAGE_SIZE + slotTrouve);
        pageBuffer.put((byte) 1); // On marque occupé

        // B. Calcul de la position du slot pour les données
        // Offset = Taille Header + Taille Bytemap + (Index * Taille Record)
        int offset = HEADER_PAGE_SIZE + maxSlot + (slotTrouve * recordSize);
        
        // C. Écriture des données (on utilise ta méthode du TP4 !)
        writeRecordToBuffer(record, pageBuffer, offset);

        // 5. Libération de la page (Dirty = true car on a écrit !)
        bufferManager.FreePage(pageId, true);

        return new RecordId(pageId, slotTrouve);
    }

    /**
     * Récupère tous les records présents dans une page.
     * @param pageId L'identifiant de la page à scanner.
     * @return Une liste de records.
     */
    public List<Record> getRecordsInDataPage(PageId pageId) {
        List<Record> resultList = new ArrayList<>();
        
        byte[] rawPage = bufferManager.GetPage(pageId);
        ByteBuffer pageBuffer = ByteBuffer.wrap(rawPage);

        int maxSlot = getSlotCount();
        int recordSize = getRecordMaxSize();

        // On parcourt la bytemap
        for (int i = 0; i < maxSlot; i++) {
            // CORRECTION IMPORTANTE : On lit la Bytemap avec le décalage du Header
            // Si l'octet vaut 1, il y a un record
            if (pageBuffer.get(HEADER_PAGE_SIZE + i) == 1) {
                // Calcul de l'offset où se trouve ce record
                int offset = HEADER_PAGE_SIZE + maxSlot + (i * recordSize);
                
                Record rec = new Record();
                readFromBuffer(rec, pageBuffer, offset); // Méthode du TP4
                resultList.add(rec);
            }
        }

        // Lecture finie, on libère la page (pas de modif -> dirty=false)
        bufferManager.FreePage(pageId, false);
        
        return resultList;
    }
 // --- TP5 : GESTION DU HEAP FILE (Multi-Pages) ---

    /**
     * Crée la Header Page (appelé une seule fois à la création de la table).
     * Elle initialise les pointeurs à "null" (FileIdx = -1, PageIdx = -1).
     */
    public void createHeaderPage() {
        this.headerPageId = diskManager.AllocPage();
        
        byte[] data = new byte[dbConfig.pagesize];
        ByteBuffer buffer = ByteBuffer.wrap(data);
        
        // On écrit -1 partout pour dire "Aucune page pour l'instant"
        // Structure : [FreePageId (8o)] [FullPageId (8o)]
        buffer.putInt(-1); buffer.putInt(-1); // FreePageId
        buffer.putInt(-1); buffer.putInt(-1); // FullPageId
        
        diskManager.WritePage(this.headerPageId, data);
        // On libère la page (on n'utilise pas le BufferManager ici pour simplifier l'init)
    }

    /**
     * Ajoute une NOUVELLE page de données à la relation.
     * Cette page sera ajoutée en tête de la liste des pages libres ("Free List").
     */
    public PageId addDataPage() {
        PageId newPageId = diskManager.AllocPage();
        
        // 1. Lire la Header Page pour savoir qui était l'ancienne "First Free"
        byte[] headerData = bufferManager.GetPage(this.headerPageId);
        ByteBuffer headerBuff = ByteBuffer.wrap(headerData);
        
        // On lit l'ancien FreePageId (pos 0)
        int oldFreeFile = headerBuff.getInt(0);
        int oldFreePage = headerBuff.getInt(4);
        
        // 2. Préparer la nouvelle page
        // Sa "Next Page" sera l'ancienne Free Page
        byte[] newPageData = bufferManager.GetPage(newPageId);
        ByteBuffer newPageBuff = ByteBuffer.wrap(newPageData);
        
        newPageBuff.putInt(0, -1);         // Prev Page = null (car elle devient la 1ère)
        newPageBuff.putInt(4, -1);
        newPageBuff.putInt(8, oldFreeFile); // Next Page = Ancienne 1ère
        newPageBuff.putInt(12, oldFreePage);
        
        bufferManager.FreePage(newPageId, true); // On sauve la nouvelle page
        
        // 3. Si l'ancienne page existait, il faut mettre à jour son "Prev Pointer"
        if (oldFreeFile != -1) {
            PageId oldPageId = new PageId(oldFreeFile, oldFreePage);
            byte[] oldPageData = bufferManager.GetPage(oldPageId);
            ByteBuffer oldBuff = ByteBuffer.wrap(oldPageData);
            
            // Le Prev Pointer est aux octets 0 et 4
            oldBuff.putInt(0, newPageId.FileIdx);
            oldBuff.putInt(4, newPageId.PageIdx);
            
            bufferManager.FreePage(oldPageId, true);
        }

        // 4. Mettre à jour la Header Page pour dire que la nouvelle est la 1ère
        headerBuff.putInt(0, newPageId.FileIdx);
        headerBuff.putInt(4, newPageId.PageIdx);
        
        bufferManager.FreePage(this.headerPageId, true);
        
        return newPageId;
    }
    
 // ==========================================
    // MÉTHODES TP5 : LECTURE (SCAN)
    // ==========================================

    /**
     * Récupère la liste de TOUTES les pages de données (Libres et Pleines).
     */
    public List<PageId> getDataPages() {
        List<PageId> pageIds = new ArrayList<>();
        
        // 1. On lit la Header Page
        byte[] headerData = bufferManager.GetPage(this.headerPageId);
        ByteBuffer headerBuff = ByteBuffer.wrap(headerData);
        
        // On récupère les têtes de liste
        int freeFile = headerBuff.getInt(0);
        int freePage = headerBuff.getInt(4);
        int fullFile = headerBuff.getInt(8);
        int fullPage = headerBuff.getInt(12);
        
        bufferManager.FreePage(this.headerPageId, false);
        
        // 2. On parcourt la liste des pages LIBRES
        traverseList(new PageId(freeFile, freePage), pageIds);
        
        // 3. On parcourt la liste des pages PLEINES
        traverseList(new PageId(fullFile, fullPage), pageIds);
        
        return pageIds;
    }

    /**
     * Helper pour parcourir une liste chaînée de pages.
     */
    private void traverseList(PageId startId, List<PageId> result) {
        PageId currentId = startId;
        
        while (currentId.FileIdx != -1 && currentId.PageIdx != -1) {
            result.add(currentId);
            
            // Lire la page courante pour trouver la suivante
            byte[] data = bufferManager.GetPage(currentId);
            ByteBuffer buff = ByteBuffer.wrap(data);
            
            // La "Next Page" est stockée aux octets 8 et 12
            int nextFile = buff.getInt(8);
            int nextPage = buff.getInt(12);
            
            bufferManager.FreePage(currentId, false);
            
            currentId = new PageId(nextFile, nextPage);
        }
    }

    /**
     * Récupère TOUS les records de la table (SELECT *).
     */
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

    /**
     * Insère un record en gérant automatiquement la recherche de place.
     */
    public RecordId InsertRecord(Record record) {
        while (true) {
            // 1. Lire la Header Page pour trouver la première page LIBRE
            byte[] headerData = bufferManager.GetPage(this.headerPageId);
            ByteBuffer headerBuff = ByteBuffer.wrap(headerData);
            
            int freeFile = headerBuff.getInt(0);
            int freePage = headerBuff.getInt(4);
            
            bufferManager.FreePage(this.headerPageId, false);
            
            // 2. Si aucune page libre, on en crée une nouvelle
            if (freeFile == -1) {
                addDataPage(); 
                continue; // On recommence pour utiliser cette nouvelle page
            }
            
            PageId freePageId = new PageId(freeFile, freePage);
            
            // 3. Tenter d'écrire
            RecordId rid = writeRecordToDataPage(record, freePageId);
            
            if (rid != null) {
                return rid; // Succès
            } else {
                // Échec : Page pleine -> On déplace et on réessaie
                moveFirstFreePageToFullList();
            }
        }
    }
    
    /**
     * Déplace la première page de la liste "Free" vers la tête de la liste "Full".
     */
    private void moveFirstFreePageToFullList() {
        // 1. Lire le Header
        byte[] headerData = bufferManager.GetPage(this.headerPageId);
        ByteBuffer headerBuff = ByteBuffer.wrap(headerData);
        
        int pFile = headerBuff.getInt(0); // Tête Free actuelle
        int pPage = headerBuff.getInt(4);
        int fullFile = headerBuff.getInt(8); // Tête Full actuelle
        int fullPage = headerBuff.getInt(12);
        
        PageId pId = new PageId(pFile, pPage);
        
        // 2. Lire la page P
        byte[] pData = bufferManager.GetPage(pId);
        ByteBuffer pBuff = ByteBuffer.wrap(pData);
        
        int nextFile = pBuff.getInt(8); // Suivant de P dans Free
        int nextPage = pBuff.getInt(12);
        
        // 3. Mettre à jour Header (Free -> Suivant de P)
        headerBuff.putInt(0, nextFile);
        headerBuff.putInt(4, nextPage);
        
        // Mettre à jour le Prev du suivant (s'il existe)
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
        pBuff.putInt(0, -1);       // P.Prev = null
        
        // Mettre à jour le Prev de l'ancien Full (s'il existe)
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
    /**
     * Reconnecte la relation aux managers (nécessaire après désérialisation).
     */
    public void setManagers(DiskManager dm, BufferManager bm) {
        this.diskManager = dm;
        this.bufferManager = bm;
    }
    // --- SUPPRESSION ---

    /**
     * Supprime tous les records qui respectent une liste de conditions.
     * @return Le nombre de records supprimés.
     */
    public int DeleteRecords(List<Condition> conditions) {
        int count = 0;
        List<PageId> pages = getDataPages();
        
        for (PageId pid : pages) {
            // 1. Lire la page
            byte[] rawPage = bufferManager.GetPage(pid);
            ByteBuffer pageBuffer = ByteBuffer.wrap(rawPage);
            int maxSlot = getSlotCount();
            int recordSize = getRecordMaxSize();
            boolean pageModified = false;

            // 2. Parcourir tous les slots
            for (int i = 0; i < maxSlot; i++) {
                // Si le slot est occupé (Bytemap = 1)
                if (pageBuffer.get(HEADER_PAGE_SIZE + i) == 1) {
                    // On lit le record pour le tester
                    int offset = HEADER_PAGE_SIZE + maxSlot + (i * recordSize);
                    Record rec = new Record();
                    readFromBuffer(rec, pageBuffer, offset);
                    
                    // 3. Vérifier les conditions
                    boolean match = true;
                    if (conditions != null) {
                        for (Condition cond : conditions) {
                            if (!cond.evaluate(rec)) { match = false; break; }
                        }
                    }
                    
                    // 4. Si ça matche, on supprime !
                    if (match) {
                        // On met le bit à 0
                        pageBuffer.position(HEADER_PAGE_SIZE + i);
                        pageBuffer.put((byte) 0);
                        pageModified = true;
                        count++;
                    }
                }
            }
            
            // 5. Libérer la page (Dirty si modifiée)
            bufferManager.FreePage(pid, pageModified);
        }
        return count;
    }
    /**
     * Met à jour les records qui respectent les conditions.
     */
    public int UpdateRecords(java.util.List<Condition> conditions, int colIndex, Object newValue) {
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
                            if (!cond.evaluate(rec)) { match = false; break; }
                        }
                    }
                    
                    if (match) {
                        // Mise à jour en mémoire
                        rec.values.set(colIndex, newValue);
                        // Réécriture sur le buffer
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
}