package espaceDisque;

import java.io.Serializable;

public class PageId implements Serializable{
	
	private static final long serialVersionUID = 1L;
    public int FileIdx;
    public int PageIdx;

    public PageId(int fileIdx, int pageIdx) {
        this.FileIdx = fileIdx;
        this.PageIdx = pageIdx;
    }

    // Utile pour le debug
    public String toString() {
        return "PageId(" + FileIdx + ", " + PageIdx + ")";
    }
    
    // Utile pour comparer deux PageId (essentiel pour les tests et le BufferManager plus tard)
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PageId pageId = (PageId) o;
        return FileIdx == pageId.FileIdx && PageIdx == pageId.PageIdx;
    }
}