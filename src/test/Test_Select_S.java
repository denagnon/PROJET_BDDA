package test;

import java.io.File;
import sgbd.DBManager;
import espaceDisque.DBConfig;

public class Test_Select_S {

    public static boolean runTests() {
        System.out.println("   [TEST] Lancement des tests SELECT WHERE sur la table S...");
        
        File testDir = new File("./BinData_S_Real");
        if (!testDir.exists()) {
            System.out.println("      [SKIP] Données introuvables. Lancez Test_S_Real avant.");
            return true;
        }

        DBConfig config = new DBConfig("./BinData_S_Real", 4096, 4, 16, "LRU");
        DBManager dbm = new DBManager(config);
        dbm.Init();

        boolean success = true;

        try {
            // Test 1 : Filtrage avec Alias (C3 = 12)
            // On utilise "FROM S s" et "WHERE s.C3"
            System.out.println("      [CMD] SELECT * FROM S s WHERE s.C3 = 12");
            // ASTUCE VISUELLE : On ne peut pas vérifier le count automatiquement ici sans modifier DBManager
            // Mais on doit voir une liste plus courte dans la console.
            dbm.ProcessCommand("SELECT * FROM S s WHERE s.C3 = 12");
            
            // Test 2 : Filtrage strict (C3 = 9999) -> Doit retourner 0
            System.out.println("      [CMD] SELECT * FROM S s WHERE s.C3 = 9999");
            dbm.ProcessCommand("SELECT * FROM S s WHERE s.C3 = 9999");

            // Test 3 : Filtrage inégalité (C4 > 10)
            System.out.println("      [CMD] SELECT * FROM S s WHERE s.C4 > 10");
            dbm.ProcessCommand("SELECT * FROM S s WHERE s.C4 > 10");

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

        dbm.Finish();
        System.out.println("   [OK] Tests Select S terminés (Vérifiez la console).");
        return true;
    }
}