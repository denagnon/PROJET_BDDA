package test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import donnees.ColInfo;
import donnees.Record;
import donnees.RecordId;
import donnees.Relation;
import donnees.ColInfo.ColType;
import espaceDisque.BufferManager;
import espaceDisque.DBConfig;
import espaceDisque.DiskManager;
import espaceDisque.PageId;

public class TestHeapFile {

    public static boolean runTests() {
        System.out.println("   [TEST] Lancement des tests TP5 (Heap File / Multi-Pages)...");
        
        // --- 0. NETTOYAGE ---
        File testDir = new File("./BinData_Heap");
        if (testDir.exists()) {
            for (File f : testDir.listFiles()) f.delete();
            testDir.delete();
        }

        // --- 1. SETUP ---
        // Config : Page 4096 octets, 16 buffers
        DBConfig config = new DBConfig("./BinData_Heap", 4096, 4, 16, "LRU");
        DiskManager dm = new DiskManager(config);
        dm.Init();
        BufferManager bm = new BufferManager(config, dm);

        // Schéma : (Nom: VARCHAR(20), Val: INT)
        List<ColInfo> cols = new ArrayList<>();
        cols.add(new ColInfo("Nom", ColType.VARCHAR, 20));
        cols.add(new ColInfo("Val", ColType.INT));

        Relation rel = new Relation("TestHeap", cols, dm, bm, null, config);
        
        // IMPORTANT : Création du Header Page !
        rel.createHeaderPage();

        boolean success = true;

        // --- 2. INSERTION MASSIVE (Pour remplir plusieurs pages) ---
        // On va insérer 200 records.
        // Chaque record prend ~48 octets. Une page contient ~83 records.
        // 200 records devraient remplir 2 pages et entamer une 3ème.
        
        System.out.println("      [INFO] Insertion de 200 records...");
        int nbRecords = 200;
        
        for (int i = 0; i < nbRecords; i++) {
            Record r = new Record("Rec" + i, i);
            RecordId rid = rel.InsertRecord(r);
            
            if (rid == null) {
                System.out.println("      [KO] InsertRecord a échoué à l'index " + i);
                success = false;
                break;
            }
        }

     // --- 3. LECTURE TOTALE (GetAllRecords) ---
        System.out.println("      [INFO] Lecture de tous les records...");
        List<Record> allRecs = rel.GetAllRecords();
        
        // A. Vérification du nombre total
        if (allRecs.size() == nbRecords) {
            System.out.println("      [OK] Nombre de records correct (" + nbRecords + ").");

            // B. Vérification de présence (au lieu de vérifier le dernier)
            // Comme le HeapFile mélange les pages, l'ordre n'est pas garanti.
            // On cherche si le Record 199 est bien dedans.
            boolean foundLast = false;
            boolean foundFirst = false;
            
            for (Record r : allRecs) {
                int val = (Integer) r.values.get(1);
                if (val == 199) foundLast = true;
                if (val == 0) foundFirst = true;
            }

            if (foundLast && foundFirst) {
                // Parfait
            } else {
                System.out.println("      [KO] Il manque des records (0 ou 199 introuvables).");
                success = false;
            }
            
            // Debug : Affichage du nombre de pages
            List<?> pages = rel.getDataPages();
            System.out.println("           Pages de données utilisées : " + pages.size());

        } else {
            System.out.println("      [KO] Nombre incorrect. Attendu: " + nbRecords + ", Reçu: " + allRecs.size());
            success = false;
        }

        dm.Finish();
        
        if (success) {
            System.out.println("   [OK] Tests HeapFile (Multi-Pages) validés.");
        }
        return success;
    }
}