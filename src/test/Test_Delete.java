package test;

import java.io.File;
import sgbd.DBManager;
import espaceDisque.DBConfig;

public class Test_Delete {

    public static boolean runTests() {
        System.out.println("   [TEST] Lancement des tests DELETE sur S...");
        
        File testDir = new File("./BinData_S_Real");
        if (!testDir.exists()) {
            System.out.println("      [SKIP] Données introuvables.");
            return true; 
        }

        DBConfig config = new DBConfig("./BinData_S_Real", 4096, 4, 16, "LRU");
        DBManager dbm = new DBManager(config);
        dbm.Init();

        try {
            // 1. On compte avant
            System.out.println("      [INFO] Suppression des records où C3 = 12...");
            
            // 2. DELETE
            dbm.ProcessCommand("DELETE S s WHERE s.C3 = 12");
            
            // 3. Vérification : On essaie de les sélectionner
            System.out.println("      [CMD] Vérification (SELECT)...");
            dbm.ProcessCommand("SELECT * FROM S s WHERE s.C3 = 12");
         

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

        dbm.Finish();
        System.out.println("   [OK] Test Delete terminé.");
        return true;
    }
}