package donnees;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import espaceDisque.PageId;

public class RelationScanner implements IRecordIterator {

    private Relation relation;
    private List<PageId> dataPages; // La liste des IDs des pages (très léger en RAM)
    private int currentPageIndex; // Sur quelle page sommes-nous ?

    private Iterator<Record> currentBufferIterator; // L'itérateur sur les records de la page COURANTE seulement

    public RelationScanner(Relation relation) {
        this.relation = relation;
        // On ne récupère que les IDs des pages, pas les données !
        this.dataPages = relation.getDataPages();
        this.currentPageIndex = 0;
        this.currentBufferIterator = null;
    }

    @Override
    public Record GetNextRecord() {
        // Tant qu'on n'a pas trouvé de record valide...
        while (true) {
            // Cas 1 : On a un itérateur de page actif et il lui reste des records
            if (currentBufferIterator != null && currentBufferIterator.hasNext()) {
                return currentBufferIterator.next();
            }

            // Cas 2 : L'itérateur courant est fini (ou n'a jamais commencé)
            // On doit passer à la page suivante.
            if (currentPageIndex >= dataPages.size()) {
                return null; // Plus aucune page à lire, c'est fini.
            }

            // On charge la page suivante
            PageId nextPageId = dataPages.get(currentPageIndex);

            // On récupère les records de CETTE page seulement (via la méthode existante de
            // Relation)
            // Cette méthode doit lire la page via le BufferManager, parser les records, et
            // LIBÉRER la page.
            List<Record> pageRecords = relation.getRecordsInDataPage(nextPageId);

            // On prépare l'itérateur pour ces nouveaux records
            currentBufferIterator = pageRecords.iterator();

            // On avance l'index pour la prochaine fois
            currentPageIndex++;
        }
    }

    @Override
    public void Close() {
        // Pas de ressources critiques à fermer ici, car getRecordsInDataPage
        // s'occupe déjà de "unpin" la page après lecture.
        // Mais on peut nettoyer les références pour aider le Garbage Collector.
        dataPages = null;
        currentBufferIterator = null;
    }

    @Override
    public void Reset() {
        this.currentPageIndex = 0;
        this.currentBufferIterator = null;
        // On re-scanne la liste des pages au cas où la table a changé (optionnel)
        this.dataPages = relation.getDataPages();
    }
}