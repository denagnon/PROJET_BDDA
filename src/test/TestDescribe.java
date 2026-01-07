package test;

import java.io.File;
import espaceDisque.DBConfig;
import sgbd.DBManager;

public class TestDescribe {

    public static boolean runTests() {
        System.out.println("   [TEST] Lancement du test DESCRIBE TABLES...");

        // Nettoyage
        File testDir = new File("./BinData_TestDescribe");
        if (testDir.exists()) {
            for (File f : testDir.listFiles())
                f.delete();
            testDir.delete();
        }

        // 1. Initialisation
        DBConfig config = new DBConfig("./BinData_TestDescribe", 4096, 4, 16, "LRU");
        DBManager dbm = new DBManager(config);
        dbm.Init();

        boolean success = true;

        try {
            // 2. CREATE TABLE
            // System.out.println(" [SQL] CREATE TABLE Etudiants...");
            dbm.ProcessCommand("CREATE TABLE Etudiants (Nom:VARCHAR(10), Age:INT)");

            // 3. DESCRIBE TABLES (Doit fonctionner)
            // System.out.println(" [SQL] DESCRIBE TABLES...");
            dbm.ProcessCommand("DESCRIBE TABLES");

        } catch (Exception e) {
            success = false;
            e.printStackTrace();
        }

        dbm.Finish();

        if (success) {
            System.out.println("   [OK] Test DESCRIBE validé.");
            return true;
        } else {
            System.out.println("   [KO] Erreur durant le test DESCRIBE.");
            return false;
        }
    }
}
