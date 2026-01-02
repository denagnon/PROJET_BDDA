package test;

import java.io.File;
import espaceDisque.DBConfig;
import sgbd.DBManager;
import donnees.Relation;

public class TestDBManager {
    public static boolean runTests() {
        System.out.println("   [TEST] Lancement des tests TP6 (SQL)...");

        // Nettoyage
        File testDir = new File("./BinData_TP6");
        if (testDir.exists()) {
            for (File f : testDir.listFiles()) f.delete();
            testDir.delete();
        }

        // 1. Initialisation
        DBConfig config = new DBConfig("./BinData_TP6", 4096, 4, 16, "LRU");
        DBManager dbm = new DBManager(config);
        dbm.Init();

        boolean success = true;

        try {
            // 2. CREATE
            System.out.println("      [SQL] CREATE TABLE Etudiants...");
            dbm.ProcessCommand("CREATE TABLE Etudiants (Nom:VARCHAR(10), Age:INT)");

            // 3. INSERT (CORRIGÉ AVEC GUILLEMETS)
            System.out.println("      [SQL] INSERT INTO...");
            // Alice devient \"Alice\"
            dbm.ProcessCommand("INSERT INTO Etudiants VALUES (\"Alice\",20)");
            dbm.ProcessCommand("INSERT INTO Etudiants VALUES (\"Bob\",22)");

            // 4. SELECT
            System.out.println("      [SQL] SELECT * ...");
            dbm.ProcessCommand("SELECT * FROM Etudiants");
            
            // Vérification
            Relation rel = dbm.GetRelation("Etudiants");
            if (rel == null || rel.GetAllRecords().size() != 2) {
                System.out.println("      [KO] Problème de contenu après insertion (Attendu: 2 records)");
                success = false;
            }

            // 5. DROP
            System.out.println("      [SQL] DROP TABLE...");
            dbm.ProcessCommand("DROP TABLE Etudiants");
            
            if (dbm.GetRelation("Etudiants") != null) success = false;

        } catch (Exception e) {
            success = false;
            e.printStackTrace();
        }

        dbm.Finish();
        
        if (success) System.out.println("   [OK] TP6 validé.");
        return success;
    }
}