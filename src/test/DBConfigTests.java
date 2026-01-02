package test;

import java.io.*;

import espaceDisque.DBConfig;

public class DBConfigTests {
    
    /**
     * Lance tous les tests de la configuration.
     * @return true si tout est OK, false sinon.
     */
    public static boolean runTests() {
        System.out.println("   [TEST] Lancement des tests DBConfig...");
        boolean success = true;

        // --- TEST 1 : Constructeur en mémoire ---
        // On utilise les 5 arguments : path, pagesize, maxfiles, buffercount, policy
        DBConfig configMem = new DBConfig("test_path", 1024, 5, 10, "LRU");
        
        if (configMem.pagesize == 1024 && configMem.bm_policy.equals("LRU")) {
            // C'est bon, pas de message d'erreur
        } else {
            System.out.println("      [KO] Constructeur mémoire : valeurs incorrectes");
            System.out.println("           Attendu : 1024 / LRU");
            System.out.println("           Reçu    : " + configMem.pagesize + " / " + configMem.bm_policy);
            success = false;
        }

        // --- TEST 2 : Lecture depuis un fichier ---
        String filename = "config_test_temp.txt";
        
        // 2a. Création du fichier temporaire
        try (PrintWriter out = new PrintWriter(filename)) {
            out.println("dbpath = ./DB_Test_Folder");
            out.println("pagesize = 8192");
            out.println("dm_maxfilecount = 50");
            out.println("bm_buffercount = 25");
            out.println("bm_policy = MRU");
        } catch (FileNotFoundException e) {
            System.out.println("      [KO] Impossible de créer le fichier de test sur le disque");
            return false; // Impossible de continuer ce test
        }

        // 2b. Chargement
        DBConfig configFile = DBConfig.LoadDBConfig(filename);
        
        // 2c. Vérification des valeurs
        if (configFile == null) {
            System.out.println("      [KO] LoadDBConfig a retourné null");
            success = false;
        } else if (configFile.pagesize != 8192 
                || !configFile.bm_policy.equals("MRU")
                || !configFile.dbpath.equals("./DB_Test_Folder")) {
            
            System.out.println("      [KO] Lecture fichier : valeurs lues incorrectes");
            System.out.println("           Lu: Size=" + configFile.pagesize + ", Policy=" + configFile.bm_policy);
            success = false;
        }

        // Nettoyage : on supprime le fichier temporaire pour ne pas polluer
        new File(filename).delete();

        // --- RÉSULTAT FINAL ---
        if (success) {
            System.out.println("   [OK] Tests DBConfig validés.");
        }
        return success;
    }
}