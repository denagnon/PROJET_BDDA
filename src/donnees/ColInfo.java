package donnees;

import java.io.Serializable;

public class ColInfo implements Serializable {
	
    private static final long serialVersionUID = 1L;

    // Enumération des types possibles
    public enum ColType {
        INT,
        FLOAT,
        CHAR,
        VARCHAR
    }

    public String name;    // Nom de la colonne
    public ColType type;   // Type de donnée
    public int length;     // Taille (utile uniquement pour CHAR et VARCHAR)

    /**
     * Constructeur complet (pour les types à taille variable comme CHAR/VARCHAR)
     * @param name Nom de la colonne
     * @param type Type de la colonne
     * @param length Taille (ex: 10 pour CHAR(10))
     */
    public ColInfo(String name, ColType type, int length) {
        this.name = name;
        this.type = type;
        this.length = length;
    }
    
    /**
     * Constructeur simplifié (pour INT et FLOAT qui n'ont pas de taille variable)
     * @param name Nom de la colonne
     * @param type Type de la colonne (INT ou FLOAT)
     */
    public ColInfo(String name, ColType type) {
        this(name, type, 0);
    }
    
    @Override
    public String toString() {
        return name + ":" + type + (length > 0 ? "(" + length + ")" : "");
    }
}