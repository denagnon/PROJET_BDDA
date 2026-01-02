package test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import donnees.ColInfo;
import donnees.Record;
import donnees.Relation;
import donnees.ColInfo.ColType;
import espaceDisque.DBConfig;
import espaceDisque.DiskManager;
import espaceDisque.BufferManager;
import espaceDisque.PageId;

public class RelationTests {

    public static boolean runTests() {
        System.out.println("   [TEST] Lancement des tests Relation (Sérialisation)...");
        boolean success = true;

        // --- 1. SETUP (Besoin de Mock/Null pour DM/BM car on teste juste la mémoire ici) ---
        // On crée une config pour avoir une taille de page définie (ex: 4096 octets)
        DBConfig config = new DBConfig("test_db", 4096, 4, 2, "LRU");
        // Pour ce test de sérialisation pure, on n'a pas besoin d'écrire sur le disque.
        // On peut passer null pour DM et BM, car writeRecordToBuffer n'utilise que le ByteBuffer.
        List<ColInfo> cols = new ArrayList<>();
        cols.add(new ColInfo("Age", ColType.INT));
        cols.add(new ColInfo("Moyenne", ColType.FLOAT));
        cols.add(new ColInfo("Code", ColType.CHAR, 3));      // CHAR(3)
        cols.add(new ColInfo("Nom", ColType.VARCHAR, 10));   // VARCHAR(10)

        // Relation "TestTable"
        Relation rel = new Relation("TestTable", cols, null, null, null, config);
        
        // --- 2. TEST DES CALCULS ---
        // Vérifions si le calcul de taille est cohérent
        
        int slotCount = rel.getSlotCount();
        
        // On vérifie juste que ce n'est pas 0 ou absurde (ex: plus grand que la page elle-même)
        if (slotCount > 0 && slotCount < 4096) {
             // C'est cohérent
        } else {
            System.out.println("      [KO] Calcul du nombre de slots incohérent : " + slotCount);
            success = false;
        }

        // --- 3. CRÉATION DU RECORD ---
        // On crée un tuple : (25, 14.5, "ABC", "Jean-Pierre")
        // Attention : "Jean-Pierre" fait 11 caractères, mais VARCHAR(10) limite ?
        // Ah non, VARCHAR(10) c'est la capacité max, on peut mettre moins. 
        // Si on met plus, on devrait tester la troncature, mais restons simples pour l'instant.
        Record recOriginal = new Record(25, 14.5f, "ABC", "Toto");
        
        // --- 4. PRÉPARATION DU BUFFER ---
        // On alloue un buffer de 100 octets (suffisant pour ce test)
        ByteBuffer buffer = ByteBuffer.allocate(100);

        // --- 5. ÉCRITURE (Sérialisation) ---
        try {
            int newPos = rel.writeRecordToBuffer(recOriginal, buffer, 0);
            // On vérifie qu'on a avancé. 
            // INT(4) + FLOAT(4) + CHAR(3*2=6) + VARCHAR(4+4*2=12) = 26 octets environ (dépend de l'implémentation char)
            // En Java, writeChar écrit 2 octets.
            if (newPos > 0) {
                // OK
            } else {
                System.out.println("      [KO] Le curseur du buffer n'a pas bougé après écriture.");
                success = false;
            }
        } catch (Exception e) {
            System.out.println("      [KO] Exception à l'écriture : " + e.getMessage());
            e.printStackTrace();
            return false;
        }

        // --- 6. LECTURE (Désérialisation) ---
        Record recRelu = new Record(); // Vide au début
        try {
            rel.readFromBuffer(recRelu, buffer, 0);
        } catch (Exception e) {
            System.out.println("      [KO] Exception à la lecture : " + e.getMessage());
            e.printStackTrace();
            return false;
        }

        // --- 7. COMPARAISON ---
        // On compare les valeurs une par une
        // A. Age
        if (!recOriginal.values.get(0).equals(recRelu.values.get(0))) {
            System.out.println("      [KO] Valeur INT incorrecte. Attendu: " + recOriginal.values.get(0) + ", Reçu: " + recRelu.values.get(0));
            success = false;
        }
        
        // B. Moyenne (Float)
        if (!recOriginal.values.get(1).equals(recRelu.values.get(1))) {
            System.out.println("      [KO] Valeur FLOAT incorrecte.");
            success = false;
        }

        // C. Code (CHAR)
        String sCharOrg = (String) recOriginal.values.get(2);
        String sCharRelu = (String) recRelu.values.get(2);
        // Attention au padding ! Si on a écrit "ABC" dans CHAR(3), c'est bon.
        // Mais dans CHAR(5), on aurait "ABC  ".
        if (!sCharOrg.equals(sCharRelu)) {
             System.out.println("      [KO] Valeur CHAR incorrecte. Attendu: '" + sCharOrg + "', Reçu: '" + sCharRelu + "'");
             success = false;
        }

        // D. Nom (VARCHAR)
        if (!recOriginal.values.get(3).equals(recRelu.values.get(3))) {
            System.out.println("      [KO] Valeur VARCHAR incorrecte.");
            success = false;
        }

        if (success) {
            System.out.println("   [OK] Tests Relation (Sérialisation/Désérialisation) validés.");
        }
        return success;
    }
}