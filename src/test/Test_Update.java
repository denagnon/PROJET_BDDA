package test;

import java.io.File;
import sgbd.DBManager;
import espaceDisque.DBConfig;

public class Test_Update {

    public static boolean runTests() {
        System.out.println("   [TEST] Lancement des tests UPDATE sur S...");
        
        File testDir = new File("./BinData_S_Real");
        if (!testDir.exists()) return true;

        DBConfig config = new DBConfig("./BinData_S_Real", 4096, 4, 16, "LRU");
        DBManager dbm = new DBManager(config);
        dbm.Init();

        try {
            // 1. Mise à jour
            // On cherche C3=15 (il y en a 4 d'après le fichier) et on met 999
            System.out.println("      [CMD] UPDATE S s SET s.C3 = 999 WHERE s.C3 = 15");
            dbm.ProcessCommand("UPDATE S s SET s.C3 = 999 WHERE s.C3 = 15");
            
            // 2. Vérification
            System.out.println("      [CMD] Vérification (SELECT C3=999)...");
            // On doit retrouver nos 4 records modifiés
            dbm.ProcessCommand("SELECT * FROM S s WHERE s.C3 = 999");

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

        dbm.Finish();
        System.out.println("   [OK] Test Update terminé.");
        return true;
    }
}