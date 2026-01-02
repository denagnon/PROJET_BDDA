package test;

public class MainTests {

    public static void main(String[] args) {
        System.out.println("==========================================");
        System.out.println("       SUITE DE TESTS - PROJET SGBD       ");
        System.out.println("==========================================\n");

        // --- TP1 & TP2 ---
        boolean configOK = DBConfigTests.runTests();
        boolean diskOK = DiskManagerTests.runTests();
        System.out.println("------------------------------------------");

        // --- TP3 ---
        boolean bufferOK = BufferManagerTests.runTests();
        System.out.println("------------------------------------------");

        // --- TP4 ---
        boolean relationOK = RelationTests.runTests();
        System.out.println("------------------------------------------");

        // --- TP5 ---
        boolean tp5OK = TestPageDonnees.runTests();
        boolean heapOK = TestHeapFile.runTests();
        System.out.println("------------------------------------------");

        // --- TP6 ---
        boolean tp6OK = TestDBManager.runTests();
        System.out.println("------------------------------------------");

        // --- TP7 (Insert / Append) ---
        boolean tp7InsertOK = Test_Insert.runTests();
        boolean tp7AppendOK = Test_Append.runTests();
        
        // --- TP7 (Import S.csv) ---
        boolean sRealOK = Test_S_Real.runTests();
        
        System.out.println("------------------------------------------");

        // --- TP7 (Select / Filtrage) ---
        boolean selectSOK = Test_Select_S.runTests();
        System.out.println("\n==========================================");
        boolean deleteOK = Test_Delete.runTests();
        System.out.println("------------------------------------------");
        boolean updateOK = Test_Update.runTests();

        // --- BILAN FINAL ---
        System.out.println("\n==========================================");
        
        if (configOK && diskOK && bufferOK && relationOK && 
            tp5OK && heapOK && tp6OK && 
            tp7InsertOK && tp7AppendOK && sRealOK && selectSOK && deleteOK && updateOK) {
            
            System.out.println("✅  SUCCÈS GLOBAL : TOUS LES SYSTÈMES SONT OPÉRATIONNELS");
            System.exit(0);
        } else {
            System.out.println("❌  ÉCHEC : CERTAINS MODULES SONT DÉFECTUEUX");
            System.exit(1);
        }
    }
}