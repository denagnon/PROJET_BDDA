package donnees;

public class Condition {

    public enum Operator {
        EQ, NEQ, GT, GEQ, LT, LEQ
    }

    private int colIndex;
    private Operator op;
    private String valConst;

    // Attributs pour la comparaison Col vs Col
    public boolean isColCompare = false;
    public int otherColIndex = -1;

    // --- CONSTRUCTEUR 1 : Standard (Col OP Constante) ---
    public Condition(int colIndex, Operator op, String valConst) {
        this.colIndex = colIndex;
        this.op = op;
        if (this.op == null)
            this.op = Operator.EQ;
        this.valConst = valConst;
        this.isColCompare = false;
    }

    // --- CONSTRUCTEUR 2 : Colonne vs Colonne (NOUVEAU) ---
    public Condition(int colIndex, Operator op, int otherColIndex, boolean isColCompare) {
        this.colIndex = colIndex;
        this.op = op;
        this.otherColIndex = otherColIndex;
        this.isColCompare = isColCompare;
        this.valConst = null; // Pas de constante
    }

    // --- CONSTRUCTEUR 3 : Parsing String (Legacy) ---
    public Condition(int colIndex, String opStr, String valConst) {
        this.colIndex = colIndex;
        this.valConst = valConst;
        this.isColCompare = false;

        if (opStr == null)
            opStr = "=";

        switch (opStr) {
            case "=":
                this.op = Operator.EQ;
                break;
            case "<>":
                this.op = Operator.NEQ;
                break;
            case ">":
                this.op = Operator.GT;
                break;
            case ">=":
                this.op = Operator.GEQ;
                break;
            case "<":
                this.op = Operator.LT;
                break;
            case "<=":
                this.op = Operator.LEQ;
                break;
            default:
                this.op = Operator.EQ;
        }
    }

    /**
     * Evalue la condition pour un record donné.
     * Gère maintenant la comparaison Col vs Col.
     */
    public boolean evaluate(Record record) {
        // 1. On récupère la valeur de gauche (v1)
        Object recordVal = record.getValues().get(colIndex);

        try {
            // Cas INTEGER
            if (recordVal instanceof Integer) {
                int v1 = (Integer) recordVal;
                int v2;

                if (isColCompare) {
                    // On récupère la valeur de l'autre colonne
                    v2 = (Integer) record.getValues().get(otherColIndex);
                } else {
                    // On parse la constante
                    v2 = Integer.parseInt(valConst);
                }
                return compareInt(v1, v2);
            }
            // Cas FLOAT
            else if (recordVal instanceof Float) {
                float v1 = (Float) recordVal;
                float v2;

                if (isColCompare) {
                    Object val2Obj = record.getValues().get(otherColIndex);
                    // Petite sécurité si on compare un Float avec un Int
                    if (val2Obj instanceof Integer) {
                        v2 = ((Integer) val2Obj).floatValue();
                    } else {
                        v2 = (Float) val2Obj;
                    }
                } else {
                    v2 = Float.parseFloat(valConst);
                }
                return compareFloat(v1, v2);
            }
            // Cas STRING (VARCHAR/CHAR)
            else {
                String v1 = recordVal.toString();
                String v2;

                if (isColCompare) {
                    v2 = record.getValues().get(otherColIndex).toString();
                } else {
                    v2 = valConst;
                }
                return compareString(v1, v2);
            }
        } catch (Exception e) {
            // En cas d'erreur de conversion (ex: comparer un Int avec "Toto"), on rejette
            return false;
        }
    }

    // --- COMPARATEURS (Tes méthodes d'origine) ---

    private boolean compareInt(int v1, int v2) {
        switch (op) {
            case EQ:
                return v1 == v2;
            case NEQ:
                return v1 != v2;
            case GT:
                return v1 > v2;
            case GEQ:
                return v1 >= v2;
            case LT:
                return v1 < v2;
            case LEQ:
                return v1 <= v2;
            default:
                return false;
        }
    }

    private boolean compareFloat(float v1, float v2) {
        switch (op) {
            case EQ:
                return v1 == v2;
            case NEQ:
                return v1 != v2;
            case GT:
                return v1 > v2;
            case GEQ:
                return v1 >= v2;
            case LT:
                return v1 < v2;
            case LEQ:
                return v1 <= v2;
            default:
                return false;
        }
    }

    private boolean compareString(String v1, String v2) {
        // On trim pour éviter les problèmes d'espaces invisibles
        int comp = v1.trim().compareTo(v2.trim());
        switch (op) {
            case EQ:
                return comp == 0;
            case NEQ:
                return comp != 0;
            case GT:
                return comp > 0;
            case GEQ:
                return comp >= 0;
            case LT:
                return comp < 0;
            case LEQ:
                return comp <= 0;
            default:
                return false;
        }
    }

    // --- GETTERS ---
    public String getValueStr() {
        return valConst;
    }

    public String getValConstante() {
        return valConst;
    }

    public int getColIndex() {
        return colIndex;
    }
}