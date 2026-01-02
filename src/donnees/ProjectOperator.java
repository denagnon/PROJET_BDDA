package donnees;

import java.util.ArrayList;
import java.util.List;

public class ProjectOperator implements IRecordIterator {

    private IRecordIterator childIterator;
    private List<Integer> colIndices; // Les indices des colonnes à garder (ex: 0, 2)

    public ProjectOperator(IRecordIterator child, List<Integer> colIndices) {
        this.childIterator = child;
        this.colIndices = colIndices;
    }

    @Override
    public Record GetNextRecord() {
        Record originalRec = childIterator.GetNextRecord();
        
        if (originalRec == null) return null;

        if (colIndices == null || colIndices.isEmpty()) {
            return originalRec;
        }

        // Sinon, on construit un nouveau record plus petit
        Record projectedRec = new Record();
        for (int index : colIndices) {
            // On récupère la valeur à l'index demandé
            if (index >= 0 && index < originalRec.values.size()) {
                projectedRec.values.add(originalRec.values.get(index));
            }
        }
        
        return projectedRec;
    }

    @Override
    public void Close() {
        childIterator.Close();
    }

    @Override
    public void Reset() {
        childIterator.Reset();
    }
}