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

public class TestPageDonnees {

    public static boolean runTests() {
        System.out.println("   [TEST] Lancement des tests TP5 (Page de Données)...");
        
        // --- 0. NETTOYAGE ---
        File testDir = new File("./BinData_TP5");
        if (testDir.exists()) {
            for (File f : testDir.listFiles()) f.delete();
            testDir.delete();
        }

        // --- 1. SETUP COMPLET ---
        DBConfig config = new DBConfig("./BinData_TP5", 4096, 4, 16, "LRU");
        DiskManager dm = new DiskManager(config);
        dm.Init();
        BufferManager bm = new BufferManager(config, dm);

        // Définition de la table "Etudiants"
        List<ColInfo> cols = new ArrayList<>();
        cols.add(new ColInfo("Nom", ColType.VARCHAR, 20)); // Taille variable
        cols.add(new ColInfo("Age", ColType.INT));         // Entier
        cols.add(new ColInfo("Note", ColType.FLOAT));      // Float

        Relation rel = new Relation("Etudiants", cols, dm, bm, null, config);

        boolean success = true;

        // --- 2. TEST INSERTION DANS UNE PAGE ---
        
        // A. On alloue une page manuellement (au TP6, le DBManager fera ça pour nous)
        PageId pid = dm.AllocPage();
        
        // B. On crée un record
        Record etudiant1 = new Record("Alice", 20, 15.5f);
        
        // C. On tente l'écriture via la Relation
        RecordId rid = rel.writeRecordToDataPage(etudiant1, pid);
        
        if (rid != null && rid.pageId.equals(pid) && rid.slotIdx == 0) {
            // C'est bon ! Slot 0 car la page était vide
        } else {
            System.out.println("      [KO] Insertion échouée ou RID incorrect : " + rid);
            success = false;
        }

        // --- 3. TEST LECTURE DEPUIS LA PAGE ---
        // On demande à la Relation de nous rendre tous les records de cette page
        List<Record> recordsLus = rel.getRecordsInDataPage(pid);
        
        if (recordsLus.size() == 1) {
            Record rLu = recordsLus.get(0);
            // Vérifions le contenu
            if (rLu.values.get(0).equals("Alice") && rLu.values.get(1).equals(20)) {
                 // Parfait
            } else {
                System.out.println("      [KO] Données corrompues à la relecture : " + rLu);
                success = false;
            }
        } else {
            System.out.println("      [KO] Nombre de records lus incorrect : " + recordsLus.size());
            success = false;
        }

        // --- 4. TEST MULTIPLE (Remplissage) ---
        // Insérons un deuxième étudiant
        Record etudiant2 = new Record("Bob", 22, 10.0f);
        RecordId rid2 = rel.writeRecordToDataPage(etudiant2, pid);
        
        if (rid2 != null && rid2.slotIdx == 1) {
             // Slot 1 car le 0 est pris
        } else {
            System.out.println("      [KO] Insertion du 2ème record échouée (Slot attendu 1).");
            success = false;
        }

        // Arrêt propre
        dm.Finish(); // Important pour sauvegarder
        
        if (success) {
            System.out.println("   [OK] Tests TP5 (Insertion/Lecture Page) validés.");
        }
        return success;
    }
}