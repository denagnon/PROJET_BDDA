package donnees;

import espaceDisque.PageId;

public class RecordId {
    public PageId pageId; // Sur quelle page est-ce stocké ?
    public int slotIdx;   // Dans quel slot (0, 1, 2...) ?

    public RecordId(PageId pageId, int slotIdx) {
        this.pageId = pageId;
        this.slotIdx = slotIdx;
    }

    @Override
    public String toString() {
        return "RID(" + pageId + ", Slot:" + slotIdx + ")";
    }
}