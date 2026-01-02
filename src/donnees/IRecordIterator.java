package donnees;

public interface IRecordIterator {
    /**
     * Retourne le prochain record disponible ou null s'il n'y en a plus.
     * Avance le curseur interne.
     */
    Record GetNextRecord();

    /**
     * Ferme l'itérateur (libère les ressources, buffers, etc.).
     */
    void Close();

    /**
     * Recommence depuis le début.
     */
    void Reset();
}