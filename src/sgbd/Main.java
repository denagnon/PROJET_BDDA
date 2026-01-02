package sgbd;

import java.util.Scanner;
import espaceDisque.DBConfig;

public class Main {

    public static void main(String[] args) {
        DBConfig config;

        // 1. Chargement de la config
        if (args.length > 0) {
            config = DBConfig.LoadDBConfig(args[0]);
        } else {
            // Configuration par défaut si aucun fichier n'est donné
            config = new DBConfig("./BinData_Default", 4096, 100, 16, "LRU");
        }
        
        // 2. Initialisation
        DBManager dbManager = new DBManager(config);
        dbManager.Init();

        System.out.println("--- SGBD Démarré (Tapez EXIT pour quitter) ---");

        // 3. Boucle de lecture (Compatible Clavier ET Fichier .txt)
        Scanner scanner = new Scanner(System.in);
        
        while (scanner.hasNextLine()) {
            String command = scanner.nextLine().trim();

            // A. Ignorer les lignes vides
            if (command.isEmpty()) continue;
            
            // B. (Optionnel) Ignorer les commentaires (lignes commençant par # ou //)
            if (command.startsWith("#") || command.startsWith("//")) continue;

            // C. IMPORTANT : Afficher la commande lue !
            // Cela permet à l'examinateur de savoir ce qui est exécuté quand il utilise un fichier.
            System.out.println("\n>> " + command);

            // D. Gestion de la sortie
            if (command.equalsIgnoreCase("EXIT")) {
                break;
            }
            
            // E. Exécution
            try {
                dbManager.ProcessCommand(command);
            } catch (Exception e) {
                System.out.println("Erreur critique dans le Main : " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        scanner.close();
        dbManager.Finish();
        System.out.println("--- SGBD Arrêté ---");
    }
}