package test;

import java.io.File;
import espaceDisque.DBConfig;
import sgbd.DBManager;
import donnees.Relation;

public class TestInsertRobustness {

    public static boolean runTests() {
        System.out.println("   [TEST] Lancement du test de ROBUSTESSE INSERT...");

        // Nettoyage préalable
        File testDir = new File("./BinData_TestRob");
        if (testDir.exists()) {
            for (File f : testDir.listFiles())
                f.delete();
            testDir.delete();
        }

        // Initialisation
        DBConfig config = new DBConfig("./BinData_TestRob", 4096, 4, 16, "LRU");
        DBManager dbm = new DBManager(config);
        dbm.Init();

        boolean allTestsPassed = true;

        try {
            // Création d'une table piège : A (INT), B (VARCHAR de taille 3)
            System.out.println("      [1] Création de la table TestRob (A:INT, B:VARCHAR(3))...");
            dbm.ProcessCommand("CREATE TABLE TestRob (A:INT, B:VARCHAR(3))");
            Relation rel = dbm.GetRelation("TestRob");

            // --- CAS 1 : INSERT VALIDE (Sans espace !) ---
            System.out.println("      [2] Tentative INSERT valide : (1,\"Oui\")");
            // CORRECTION ICI : Pas d'espace après la virgule
            dbm.ProcessCommand("INSERT INTO TestRob VALUES (1,\"Oui\")");

            // Vérification
            if (rel.GetAllRecords().size() != 1) {
                System.out.println("          -> ECHEC : L'insertion valide a échoué !");
                allTestsPassed = false;
            } else {
                System.out.println("          -> OK : Insertion réussie.");
            }

            // --- CAS 2 : MAUVAIS TYPE ---
            System.out.println("      [3] Tentative TYPE INVALIDE : (\"Non\",\"Non\")");
            dbm.ProcessCommand("INSERT INTO TestRob VALUES (\"Non\",\"Non\")");
            if (rel.GetAllRecords().size() != 1) {
                System.out.println("          -> ECHEC : L'insertion invalide est passée ! (Type)");
                allTestsPassed = false;
            } else {
                System.out.println("          -> OK : Rejeté correctement.");
            }

            // --- CAS 3 : TROP DE VALEURS ---
            System.out.println("      [4] Tentative NB VALEURS INCORRECT : (2,\"Non\",3)");
            dbm.ProcessCommand("INSERT INTO TestRob VALUES (2,\"Non\",3)");
            if (rel.GetAllRecords().size() != 1) {
                System.out.println("          -> ECHEC : L'insertion invalide est passée ! (Nb Val)");
                allTestsPassed = false;
            } else {
                System.out.println("          -> OK : Rejeté correctement.");
            }

            // --- CAS 4 : CHAÎNE TROP LONGUE ---
            System.out.println("      [5] Tentative CHAÎNE TROP LONGUE : (3,\"TropLong\")");
            dbm.ProcessCommand("INSERT INTO TestRob VALUES (3,\"TropLong\")");
            if (rel.GetAllRecords().size() != 1) {
                System.out.println("          -> ECHEC : La chaîne trop longue a été insérée !");
                allTestsPassed = false;
            } else {
                System.out.println("          -> OK : Rejeté correctement.");
            }

            // --- CAS 5 : FORMAT INCORRECT (Pas de guillemets) ---
            System.out.println("      [6] Tentative SANS GUILLEMETS : (4,PasDeQuotes)");
            dbm.ProcessCommand("INSERT INTO TestRob VALUES (4,PasDeQuotes)");
            if (rel.GetAllRecords().size() != 1) {
                System.out.println("          -> ECHEC : La chaîne sans guillemets est passée !");
                allTestsPassed = false;
            } else {
                System.out.println("          -> OK : Rejeté correctement.");
            }

        } catch (Exception e) {
            System.out.println("   [CRASH] Le test a provoqué une exception non gérée !");
            e.printStackTrace();
            allTestsPassed = false;
        }

        dbm.Finish();

        if (allTestsPassed) {
            System.out.println("   [OK] Test ROBUSTESSE validé : Toutes les erreurs ont été gérées.");
            return true;
        } else {
            System.out.println("   [KO] Certaines sécurités ne fonctionnent pas.");
            return false;
        }
    }
}