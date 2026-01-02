package test;

import java.io.File;

import espaceDisque.BufferManager;
import espaceDisque.DBConfig;
import espaceDisque.DiskManager;
import espaceDisque.PageId;

public class BufferManagerTests {

    public static boolean runTests() {
        System.out.println("   [TEST] Lancement des tests BufferManager...");
        
        // --- 0. NETTOYAGE ---
        File testDir = new File("./BinData_BM_Test");
        if (testDir.exists()) {
            for (File f : testDir.listFiles()) f.delete();
            testDir.delete();
        }

        // --- 1. SETUP ---
        // ATTENTION : On configure SEULEMENT 2 BUFFERS pour faciliter le test de saturation !
        DBConfig config = new DBConfig("./BinData_BM_Test", 4096, 4, 2, "LRU");
        DiskManager dm = new DiskManager(config);
        dm.Init();
        BufferManager bm = new BufferManager(config, dm);

        boolean success = true;

        // --- 2. SCÉNARIO DE TEST ---
        
        // A. Allocation de 3 pages (P0, P1, P2) sur le disque
        PageId p0 = dm.AllocPage();
        PageId p1 = dm.AllocPage();
        PageId p2 = dm.AllocPage();

        // B. Chargement de P0 et P1 (Le buffer est plein : 2/2)
        byte[] buff0 = bm.GetPage(p0);
        byte[] buff1 = bm.GetPage(p1);
        
        // On écrit quelque chose dans P0 pour la rendre "Dirty"
        buff0[0] = (byte) 'A';
        bm.FreePage(p0, true); // P0 est libérée (pin=0) et Dirty
        bm.FreePage(p1, false); // P1 est libérée (pin=0) et Clean

        // C. Demande de P2 -> Doit déclencher un remplacement (LRU)
        // P0 a été accédée en premier, P1 en deuxième. P0 est la LRU (Least Recently Used) ? 
        // Non, GetPage met à jour le timer.
        // Ordre d'accès : Get(P0), Get(P1). 
        // P0 a un timer plus petit que P1. Donc P0 devrait être éjectée.
        // Comme P0 est Dirty, elle doit être écrite sur le disque !
        
        byte[] buff2 = bm.GetPage(p2);
        
        // À ce stade :
        // - P0 a dû être écrite sur le disque (car dirty) et éjectée de la RAM.
        // - P2 est en RAM à la place de P0.
        // - P1 est toujours en RAM.

        // D. Vérification : On relit P0 depuis le DISQUE via le DiskManager (pas le BM)
        // pour voir si la sauvegarde a bien eu lieu.
        byte[] verifP0 = new byte[config.pagesize];
        dm.ReadPage(p0, verifP0);
        
        if (verifP0[0] == (byte) 'A') {
            // OK
        } else {
            System.out.println("      [KO] La page P0 (Dirty) n'a pas été sauvegardée sur le disque lors de son remplacement !");
            success = false;
        }

        // Fin des tests
        bm.FlushBuffers(); // Juste pour tester que ça ne plante pas
        
        if (success) System.out.println("   [OK] Tests BufferManager (Remplacement & Dirty) validés.");
        return success;
    }
}