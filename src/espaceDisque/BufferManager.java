package espaceDisque;

public class BufferManager {

    // --- CLASSE INTERNE FRAME (Une case de mémoire) ---
    private class Frame {
        byte[] buffer;      // Le contenu de la page
        PageId pageId;      // L'identifiant de la page chargée (null si vide)
        int pinCount;       // Nombre d'utilisateurs actuels (0 = remplaçable)
        boolean isDirty;    // A-t-elle été modifiée ?
        long lastUsed;      // Pour le LRU : date du dernier accès

        public Frame(int pageSize) {
            this.buffer = new byte[pageSize];
            this.pageId = null;
            this.pinCount = 0;
            this.isDirty = false;
            this.lastUsed = 0;
        }
        
        // Vide la frame (remise à zéro)
        public void reset() {
            this.pageId = null;
            this.pinCount = 0;
            this.isDirty = false;
            this.lastUsed = 0;
        }
        
        // Vérifie si la frame est libre (vide)
        public boolean isEmpty() {
            return this.pageId == null;
        }
    }

    // --- VARIABLES MEMBRES ---
    private DBConfig dbConfig;
    private DiskManager diskManager;
    private Frame[] bufferPool;      // Notre mémoire RAM
    private long timer;      // Compteur logique pour le LRU
    private String currentPolicy;

    // --- CONSTRUCTEUR ---
    public BufferManager(DBConfig dbConfig, DiskManager diskManager) {
        this.dbConfig = dbConfig;
        this.diskManager = diskManager;
        
        // On crée le tableau de frames selon la config
        this.bufferPool = new Frame[dbConfig.bm_buffercount];
        for (int i = 0; i < this.bufferPool.length; i++) {
            this.bufferPool[i] = new Frame(dbConfig.pagesize);
        }
        this.timer = 0;
        this.currentPolicy = dbConfig.bm_policy; // On prend la config par défaut
    }

    /**
     * Méthode principale : Demande l'accès à une page.
     * La charge depuis le disque si nécessaire.
     */
    public byte[] GetPage(PageId pageId) {
        this.timer++; // On avance le temps (pour le LRU)

        // 1. Chercher si la page est déjà en mémoire
        for (Frame frame : bufferPool) {
            if (frame.pageId != null && frame.pageId.equals(pageId)) {
                // TROUVÉE !
                frame.pinCount++;       // On signale qu'on l'utilise
                frame.lastUsed = timer; // Mise à jour pour LRU
                return frame.buffer;
            }
        }

        // 2. Pas trouvée : Il faut la charger. Trouver une frame libre ou remplaçable.
        Frame victim = pickVictim();

        if (victim == null) {
            System.err.println("[BufferManager] ERREUR CRITIQUE : Toutes les frames sont utilisées (pin_count > 0) !");
            return null; // On ne peut rien faire, la mémoire est saturée
        }

        // 3. Si la victime est sale (dirty), on doit d'abord la sauvegarder !
        if (victim.pageId != null && victim.isDirty) {
            diskManager.WritePage(victim.pageId, victim.buffer);
        }

        // 4. On charge la nouvelle page dans la frame victime
        victim.reset(); // On nettoie les anciennes infos
        victim.pageId = pageId;
        diskManager.ReadPage(pageId, victim.buffer); // Lecture disque
        victim.pinCount = 1; // On l'utilise tout de suite
        victim.lastUsed = timer;

        return victim.buffer;
    }

    /**
     * Libère une page (décrémente le pin_count).
     * @param valdirty : true si la page a été modifiée par l'utilisateur
     */
    public void FreePage(PageId pageId, boolean valdirty) {
        for (Frame frame : bufferPool) {
            if (frame.pageId != null && frame.pageId.equals(pageId)) {
                if (frame.pinCount > 0) {
                    frame.pinCount--;
                }
                if (valdirty) {
                    frame.isDirty = true;
                }
                return;
            }
        }
        System.err.println("[BufferManager] Tentative de libérer une page non chargée : " + pageId);
    }

    /**
     * Écrit toutes les pages modifiées (dirty) sur le disque et vide le buffer.
     */
    public void FlushBuffers() {
        for (Frame frame : bufferPool) {
            if (!frame.isEmpty()) {
                if (frame.isDirty) {
                    diskManager.WritePage(frame.pageId, frame.buffer);
                }
                frame.reset(); // On vide la frame
            }
        }
    }

    /**
     * Choisit une frame pour le remplacement selon la politique LRU.
     * @return La frame choisie, ou null si aucune n'est disponible.
     */
    private Frame pickVictim() {
        // 1. D'abord, on cherche une frame VRAIMENT vide
        for (Frame frame : bufferPool) {
            if (frame.isEmpty()) return frame;
        }

        // 2. Sinon, on cherche une victime parmi celles avec pin_count == 0
        Frame bestVictim = null;
        
        // LRU : On cherche le plus PETIT lastUsed
        // MRU : On cherche le plus GRAND lastUsed
        long bestTime = ("LRU".equals(currentPolicy)) ? Long.MAX_VALUE : -1;

        for (Frame frame : bufferPool) {
            if (frame.pinCount == 0) {
                if ("LRU".equals(currentPolicy)) {
                    // Logique LRU (Le plus ancien)
                    if (frame.lastUsed < bestTime) {
                        bestTime = frame.lastUsed;
                        bestVictim = frame;
                    }
                } else {
                    // Logique MRU (Le plus récent)
                    if (frame.lastUsed > bestTime) {
                        bestTime = frame.lastUsed;
                        bestVictim = frame;
                    }
                }
            }
        }
        
        return bestVictim;
    }
    /**
     * Modifie dynamiquement la politique de remplacement de page.
     * Cette méthode permet de passer de LRU à MRU (et inversement) en cours d'exécution.
     * * @param policy La nouvelle politique à appliquer. Valeurs acceptées : "LRU" ou "MRU".
     * Si la valeur est incorrecte, la politique actuelle reste inchangée.
     */
    public void SetCurrentReplacementPolicy(String policy) {
        if ("LRU".equals(policy) || "MRU".equals(policy)) {
            this.currentPolicy = policy;
        } else {
            System.err.println("Politique inconnue : " + policy + ". On garde " + this.currentPolicy);
        }
    }
}