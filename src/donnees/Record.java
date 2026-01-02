package donnees; 

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class Record {
    // On stocke les valeurs sous forme d'Objets (Integer, Float, String...)
    // Cela permet de mélanger les types dans la même liste.
    public List<Object> values;

    public Record() {
        this.values = new ArrayList<>();
    }
    
    // Constructeur pratique (ex: new Record(12, "Jean", 14.5f))
    public Record(Object... vals) {
        this.values = new ArrayList<>();
        for (Object o : vals) {
            this.values.add(o);
        }
    }
    

    public java.util.List<Object> getValues() {
        return this.values;
    }

    
    @Override
    public String toString() {
        return values.toString();
    }
}