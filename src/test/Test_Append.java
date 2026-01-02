package test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import sgbd.DBManager;
import espaceDisque.DBConfig;
import donnees.Relation;

public class Test_Append {

    public static boolean runTests() {
        System.out.println("   [TEST] Lancement des tests TP7 (Append CSV)...");
        
        // 0. Nettoyage
        File testDir = new File("./BinData_Append");
        if (testDir.exists()) {
            for (File f : testDir.listFiles()) f.delete();
            testDir.delete();
        }

        // 1. Création d'un fichier CSV factice pour le test
        String csvName = "test_data.csv";
        try (FileWriter fw = new FileWriter(csvName)) {
            fw.write("\"Jean\",10,15.5\n");
            fw.write("\"Paul\",12,10.0\n");
            fw.write("\"Pierre\",08,05.0\n"); // Ligne valide
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        // 2. Setup SGBD
        DBConfig config = new DBConfig("./BinData_Append", 4096, 4, 16, "LRU");
        DBManager dbm = new DBManager(config);
        dbm.Init();

        boolean success = true;

        try {
            // 3. Création Table compatible
            dbm.ProcessCommand("CREATE TABLE Etudiants (Nom:VARCHAR(10), Note1:INT, Note2:FLOAT)");

            // 4. Commande APPEND
            String cmd = "APPEND INTO Etudiants ALLRECORDS (" + csvName + ")";
            System.out.println("      [CMD] " + cmd);
            dbm.ProcessCommand(cmd);

            // 5. Vérification
            Relation rel = dbm.GetRelation("Etudiants");
            int count = rel.GetAllRecords().size();
            
            if (count == 3) {
                // OK
            } else {
                System.out.println("      [KO] Nombre de records importés incorrect : " + count);
                success = false;
            }

        } catch (Exception e) {
            success = false;
            e.printStackTrace();
        }

        dbm.Finish();
        
        // Nettoyage du fichier CSV
        new File(csvName).delete();
        
        if (success) System.out.println("   [OK] Tests Append validés.");
        return success;
    }
}