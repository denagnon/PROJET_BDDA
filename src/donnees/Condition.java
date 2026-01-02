package donnees;

public class Condition {
    
    public enum Operator {
        EQ, NEQ, GT, GEQ, LT, LEQ
    }

    private int colIndex;
    private Operator op;       
    private String valConst;   

    // --- CONSTRUCTEUR 1 : Si DBManager envoie un ENUM (Operator) ---
    public Condition(int colIndex, Operator op, String valConst) {
        this.colIndex = colIndex;
        this.op = op;
        if (this.op == null) this.op = Operator.EQ;
        this.valConst = valConst;
    }

    // --- CONSTRUCTEUR 2 : Si DBManager envoie un STRING ("=") ---
    public Condition(int colIndex, String opStr, String valConst) {
        this.colIndex = colIndex;
        this.valConst = valConst;
        if (opStr == null) opStr = "=";
        
        switch(opStr) {
            case "=":  this.op = Operator.EQ; break;
            case "<>": this.op = Operator.NEQ; break;
            case ">":  this.op = Operator.GT; break;
            case ">=": this.op = Operator.GEQ; break;
            case "<":  this.op = Operator.LT; break;
            case "<=": this.op = Operator.LEQ; break;
            default:   this.op = Operator.EQ; 
        }
    }

    public boolean evaluate(Record record) {
        // Utilise getValues() ou .values selon ta classe Record
        Object recordVal = record.getValues().get(colIndex); 
        
        try {
            if (recordVal instanceof Integer) {
                int v1 = (Integer) recordVal;
                int v2 = Integer.parseInt(valConst); 
                return compareInt(v1, v2);
            } 
            else if (recordVal instanceof Float) {
                float v1 = (Float) recordVal;
                float v2 = Float.parseFloat(valConst);
                return compareFloat(v1, v2);
            } 
            else {
                String v1 = recordVal.toString();
                String v2 = valConst;
                return compareString(v1, v2);
            }
        } catch (Exception e) {
            return false;
        }
    }

    // --- COMPARATORS ---
    private boolean compareInt(int v1, int v2) {
        switch (op) {
            case EQ: return v1 == v2;
            case NEQ: return v1 != v2;
            case GT: return v1 > v2;
            case GEQ: return v1 >= v2;
            case LT: return v1 < v2;
            case LEQ: return v1 <= v2;
            default: return false;
        }
    }

    private boolean compareFloat(float v1, float v2) {
        switch (op) {
            case EQ: return v1 == v2;
            case NEQ: return v1 != v2;
            case GT: return v1 > v2;
            case GEQ: return v1 >= v2;
            case LT: return v1 < v2;
            case LEQ: return v1 <= v2;
            default: return false;
        }
    }

    private boolean compareString(String v1, String v2) {
    	int comp = v1.trim().compareTo(v2.trim());
        switch (op) {
            case EQ: return comp == 0;
            case NEQ: return comp != 0;
            case GT: return comp > 0;
            case GEQ: return comp >= 0;
            case LT: return comp < 0;
            case LEQ: return comp <= 0;
            default: return false;
        }
    }
    
    // --- GETTERS ---
    
    public String getValueStr() { 
        return valConst; 
    }

    public String getValConstante() { 
        return valConst; 
    }
    
    public int getColIndex() { return colIndex; }
}