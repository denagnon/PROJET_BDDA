package donnees;

import java.util.List;
import java.util.Iterator;

public class RelationScanner implements IRecordIterator {
    
    private List<Record> allRecords;
    private Iterator<Record> iterator;

    public RelationScanner(Relation relation) {
        // Version simple : on charge tout en mémoire
        // (Pour la version optimisée "Page par Page", il faudrait gérer le BufferManager ici)
        this.allRecords = relation.GetAllRecords();
        this.iterator = allRecords.iterator();
    }

    @Override
    public Record GetNextRecord() {
        if (iterator.hasNext()) {
            return iterator.next();
        }
        return null; // Fin du scan
    }

    @Override
    public void Close() {
        // Rien de spécial à fermer dans la version liste
    }

    @Override
    public void Reset() {
        this.iterator = allRecords.iterator();
    }
}