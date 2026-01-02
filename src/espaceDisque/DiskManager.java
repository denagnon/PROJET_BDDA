package espaceDisque;

import java.io.*;
import java.util.*;

public class DiskManager {
    private DBConfig dbConfig;
    private LinkedList<PageId> freePages; // Stocke les pages désallouées pour réutilisation

    public DiskManager(DBConfig dbConfig) {
        this.dbConfig = dbConfig;
        this.freePages = new LinkedList<>();
    }

    /**
     * Initialise le système de fichiers (crée le dossier BinData si besoin).
     */
    public void Init() {
        File dir = new File(dbConfig.dbpath);
        if (!dir.exists()) {
            boolean created = dir.mkdirs();
            if (created) {
                System.out.println("   [Info] Dossier " + dbConfig.dbpath + " créé.");
            }
        }
    }

    /**
     * Alloue une page (réutilise une libre ou en crée une nouvelle).
     * @return Le PageId de la page allouée.
     */
    public PageId AllocPage() {
        // 1. Priorité : Réutiliser une page libérée
        if (!freePages.isEmpty()) {
            return freePages.pop();
        }

        // 2. Sinon : Créer une nouvelle page à la fin du fichier courant
        // (Simplification : on utilise le fichier 0 pour l'instant)
        int fileId = 0; 
        int pageIdx = 0;
        
        try {
            File f = new File(dbConfig.dbpath + File.separator + "Data" + fileId + ".bin");
            RandomAccessFile raf = new RandomAccessFile(f, "rw");
            
            // Calcul de l'index de la nouvelle page
            long length = raf.length();
            pageIdx = (int) (length / dbConfig.pagesize);
            
            // On agrandit le fichier pour "réserver" la place
            raf.setLength(length + dbConfig.pagesize);
            raf.close();
            
        } catch (IOException e) {
            System.err.println("Erreur AllocPage: " + e.getMessage());
            return null;
        }

        return new PageId(fileId, pageIdx);
    }

    /**
     * Lit le contenu d'une page disque dans le buffer fourni.
     */
    public void ReadPage(PageId pageId, byte[] buff) {
        try {
            File f = new File(dbConfig.dbpath + File.separator + "Data" + pageId.FileIdx + ".bin");
            RandomAccessFile raf = new RandomAccessFile(f, "r");
            
            long offset = (long) pageId.PageIdx * dbConfig.pagesize;
            raf.seek(offset);
            raf.readFully(buff); // Remplit tout le buffer
            raf.close();
        } catch (IOException e) {
            System.err.println("Erreur ReadPage: " + e.getMessage());
        }
    }

    /**
     * Écrit le contenu du buffer sur la page disque.
     */
    public void WritePage(PageId pageId, byte[] buff) {
        try {
            File f = new File(dbConfig.dbpath + File.separator + "Data" + pageId.FileIdx + ".bin");
            RandomAccessFile raf = new RandomAccessFile(f, "rw");
            
            long offset = (long) pageId.PageIdx * dbConfig.pagesize;
            raf.seek(offset);
            raf.write(buff);
            raf.close();
        } catch (IOException e) {
            System.err.println("Erreur WritePage: " + e.getMessage());
        }
    }

    /**
     * Désalloue une page (la rend disponible pour AllocPage).
     */
    public void DeallocPage(PageId pageId) {
        freePages.add(pageId);
    }
    
    /**
     * Fermeture propre du DiskManager (sauvegarde état si nécessaire).
     */
    public void Finish() {
        // Pour l'instant, rien de spécial à faire à la fermeture
    }
}