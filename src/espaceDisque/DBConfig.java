package espaceDisque;

import java.io.*;

public class DBConfig implements Serializable {
    // --- Variables membres (Champs) ---
    public String dbpath;
    public int pagesize;
    public int dm_maxfilecount;
    public int bm_buffercount; // Champ ajouté pour le TP3
    public String bm_policy;   // Champ ajouté pour le TP3
    
    private static final long serialVersionUID = 1L;

    // --- Constructeur (5 arguments) ---
    public DBConfig(String dbpath, int pagesize, int dm_maxfilecount, int bm_buffercount, String bm_policy) {
        this.dbpath = dbpath;
        this.pagesize = pagesize;
        this.dm_maxfilecount = dm_maxfilecount;
        this.bm_buffercount = bm_buffercount;
        this.bm_policy = bm_policy;
    }

    // --- Méthode de chargement ---
    public static DBConfig LoadDBConfig(String fichier_config) {
        String path = "./BinData";
        int pSize = 4096;
        int maxFiles = 100;
        int buffCount = 100; 
        String policy = "LRU"; 

        try (BufferedReader br = new BufferedReader(new FileReader(fichier_config))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                
                String[] parts = line.split("[=:]");
                if (parts.length >= 2) {
                    String key = parts[0].trim();
                    String value = parts[1].trim();
                    
                    switch(key) {
                        case "dbpath": path = value; break;
                        case "pagesize": pSize = Integer.parseInt(value); break;
                        case "dm_maxfilecount": maxFiles = Integer.parseInt(value); break;
                        case "bm_buffercount": buffCount = Integer.parseInt(value); break;
                        case "bm_policy": policy = value; break;
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Info: Utilisation config défaut (" + e.getMessage() + ")");
            // On retourne null seulement si c'est vraiment critique, sinon on peut renvoyer la config par défaut
            // Pour le test "Erreur Fichier", on s'attend souvent à null ou une exception gérée.
            // Ici, on renvoie une config par défaut pour ne pas crasher le SGBD.
        }
        return new DBConfig(path, pSize, maxFiles, buffCount, policy);
    }
}