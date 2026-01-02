package donnees;

import java.util.List;

public class SelectOperator implements IRecordIterator {

    private IRecordIterator childIterator; // D'où viennent les données (ex: RelationScanner)
    private List<Condition> conditions;    // La liste des critères (WHERE ...)

    public SelectOperator(IRecordIterator child, List<Condition> conditions) {
        this.childIterator = child;
        this.conditions = conditions;
    }

    @Override
    public Record GetNextRecord() {
        // On boucle tant que le fils a des records
        Record rec;
        while ((rec = childIterator.GetNextRecord()) != null) {
            
            // On vérifie si le record respecte TOUTES les conditions
            boolean isMatch = true;
            if (conditions != null) {
            	// DEBUG TEMPORAIRE
            	//System.out.println("Test RecordVal (" + rec.getValues().get(0).getClass().getSimpleName() + "): " + rec.getValues().get(0) 
            	                 //+ " VS ConditionVal: " + conditions.get(0).getValueStr());
                for (Condition cond : conditions) {
                    if (!cond.evaluate(rec)) {
                        isMatch = false;
                        break; // Pas la peine de continuer, ce record est rejeté
                    }
                }
            }

            // Si c'est bon, on le retourne
            if (isMatch) {
                return rec;
            }
            // Sinon, la boucle continue et on demande le suivant au fils
        }
        
        return null; // Plus rien à lire
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