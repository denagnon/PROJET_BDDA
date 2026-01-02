package test;

import java.io.File;
import sgbd.DBManager;
import espaceDisque.DBConfig;
import donnees.Relation;

public class Test_Insert {

    public static boolean runTests() {
        System.out.println("   [TEST] Lancement des tests TP7 (Insertion)...");
        
        // 0. Nettoyage
        File testDir = new File("./BinData_Insert");
        if (testDir.exists()) {
            for (File f : testDir.listFiles()) f.delete();
            testDir.delete();
        }

        // 1. Setup
        DBConfig config = new DBConfig("./BinData_Insert", 4096, 4, 16, "LRU");
        DBManager dbm = new DBManager(config);
        dbm.Init();

        boolean success = true;

        try {
            // 2. Création table
            dbm.ProcessCommand("CREATE TABLE Profs (Nom:VARCHAR(20), Specialite:VARCHAR(20), Age:INT)");

            // 3. Test INSERT valide (Format strict du TP7 : "valeur",13)
            String cmd1 = "INSERT INTO Profs VALUES (\"Ileana\",\"BDDA\",13)";
            System.out.println("      [CMD] " + cmd1);
            dbm.ProcessCommand(cmd1);

            // Vérification
            Relation rel = dbm.GetRelation("Profs");
            if (rel.GetAllRecords().size() == 1) {
                // OK
            } else {
                System.out.println("      [KO] L'insertion a échoué (Table vide).");
                success = false;
            }

            // 4. Test INSERT invalide (Syntaxe incorrecte)
            System.out.println("      [CMD] INSERT invalide (pas de guillemets)...");
            dbm.ProcessCommand("INSERT INTO Profs VALUES (Ileana,BDDA,13)"); // Doit échouer
            
            if (rel.GetAllRecords().size() == 1) {
                // OK : Toujours 1 seul record, le 2ème n'est pas passé
            } else {
                System.out.println("      [KO] Une commande invalide a été acceptée !");
                success = false;
            }

        } catch (Exception e) {
            success = false;
            e.printStackTrace();
        }

        dbm.Finish();
        
        if (success) System.out.println("   [OK] Tests Insert validés.");
        return success;
    }
}