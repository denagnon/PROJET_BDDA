package test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import espaceDisque.DBConfig;
import espaceDisque.DiskManager;
import espaceDisque.PageId;

public class DiskManagerTests {

    public static boolean runTests() {
        System.out.println("   [TEST] Lancement des tests DiskManager...");
        
        // --- 0. NETTOYAGE PRÉALABLE (Reset) ---
        // On supprime le dossier de test pour repartir de zéro à chaque lancement
        File testDir = new File("./BinData_Test");
        if (testDir.exists()) {
            File[] files = testDir.listFiles();
            if (files != null) {
                for (File f : files) f.delete(); // Supprime les .bin
            }
            testDir.delete(); // Supprime le dossier
            System.out.println("      [RESET] Dossier de test nettoyé.");
        }

        // --- 1. SETUP ---
        DBConfig config = new DBConfig("./BinData_Test", 4096, 4, 16, "LRU");
        DiskManager dm = new DiskManager(config);
        dm.Init();

        boolean success = true;

        // --- 2. TEST ALLOCATION UNIQUE ---
        PageId pid1 = dm.AllocPage();
        if (pid1 == null) {
            System.out.println("      [KO] AllocPage a retourné null");
            return false;
        }
        // Vérification qu'on commence bien à 0 maintenant
        if (pid1.PageIdx != 0) {
             System.out.println("      [KO] L'index ne commence pas à 0 (Reçu: " + pid1.PageIdx + ")");
             success = false;
        }

        // --- 3. TEST ÉCRITURE PAGE 1 ---
        byte[] bufferEcriture = new byte[config.pagesize];
        String messageP1 = "Page 1 : Bonjour !";
        byte[] msgBytes = messageP1.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(msgBytes, 0, bufferEcriture, 0, msgBytes.length);
        
        try {
            dm.WritePage(pid1, bufferEcriture);
        } catch (Exception e) {
            System.out.println("      [KO] Exception lors de WritePage : " + e.getMessage());
            success = false;
        }

        // --- 4. TEST LECTURE PAGE 1 ---
        byte[] bufferLecture = new byte[config.pagesize];
        try {
            dm.ReadPage(pid1, bufferLecture);
            String messageRelu = new String(bufferLecture, 0, msgBytes.length, StandardCharsets.UTF_8);
            if (!messageP1.equals(messageRelu)) {
                System.out.println("      [KO] Données lues incohérentes sur Page 1 !");
                success = false;
            }
        } catch (Exception e) {
            System.out.println("      [KO] Exception lecture : " + e.getMessage());
            success = false;
        }

        // --- 5. TEST ALLOCATIONS MULTIPLES ---
        PageId pid2 = dm.AllocPage();
        PageId pid3 = dm.AllocPage();
        
        // Comme on a reset, pid1=0, donc pid2 doit être 1 et pid3 doit être 2
        if (pid2.PageIdx == 1 && pid3.PageIdx == 2) {
             // Index OK
        } else {
            System.out.println("      [KO] L'allocation ne semble pas séquentielle (Index " + pid2.PageIdx + " et " + pid3.PageIdx + ")");
            success = false;
        }

        // --- 6. TEST DE L'OFFSET ---
        byte[] bufferP3 = new byte[config.pagesize];
        String msgP3 = "Page 3 : Je suis loin !";
        System.arraycopy(msgP3.getBytes(), 0, bufferP3, 0, msgP3.getBytes().length);
        
        try {
            dm.WritePage(pid3, bufferP3);
            
            // On relit la Page 1 pour vérifier qu'elle n'a pas bougé
            byte[] checkP1 = new byte[config.pagesize];
            dm.ReadPage(pid1, checkP1);
            String verifP1 = new String(checkP1, 0, msgBytes.length, StandardCharsets.UTF_8);
            
            if (messageP1.equals(verifP1)) {
                // Parfait
            } else {
                System.out.println("      [KO] INTERFÉRENCE : Écrire sur la Page 3 a modifié la Page 1 !");
                success = false;
            }
        } catch (Exception e) {
            System.out.println("      [KO] Erreur lors du test d'offset : " + e.getMessage());
            success = false;
        }

        // Arrêt du système
        dm.Finish();

        // --- 7. TEST DE PERSISTANCE (STOP & START) ---
        // Ici, on NE NETTOIE PAS, car on veut vérifier que les données sont restées !
        System.out.println("      [INFO] Redémarrage du DiskManager...");
        DiskManager dm2 = new DiskManager(config);
        dm2.Init(); 

        byte[] bufferCheck = new byte[config.pagesize];
        try {
            dm2.ReadPage(pid3, bufferCheck);
            String msgVerif = new String(bufferCheck, 0, msgP3.getBytes().length, StandardCharsets.UTF_8);
            
            if (msgP3.equals(msgVerif)) {
                System.out.println("      [OK] Persistance validée.");
            } else {
                System.out.println("      [KO] Persistance échouée : Données perdues.");
                success = false;
            }
        } catch (Exception e) {
            System.out.println("      [KO] Erreur lecture après redémarrage : " + e.getMessage());
            success = false;
        }
        
        // --- 8. TEST DÉSALLOCATION (Le petit dernier) ---
        // On alloue une page "extra"
        PageId pageExtra = dm2.AllocPage(); 
        // On la libère
        dm2.DeallocPage(pageExtra);
        // On demande une nouvelle page. Normalement, le DiskManager doit nous rendre celle qu'on vient de libérer
        PageId pageRecycle = dm2.AllocPage();

        if (pageExtra.FileIdx == pageRecycle.FileIdx && pageExtra.PageIdx == pageRecycle.PageIdx) {
            System.out.println("      [OK] Désallocation validée : Page réutilisée.");
        } else {
            System.out.println("      [WARN] Désallocation non optimale (Pas de réutilisation).");
            // Ce n'est pas bloquant, donc on ne met pas success = false
        }
        
        dm2.Finish();

        if (success) {
            System.out.println("   [OK] Tests DiskManager validés.");
        }
        return success;
    }
}