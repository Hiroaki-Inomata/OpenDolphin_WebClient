package open.dolphin.shared.converter;

import java.io.Serializable;
import java.util.List;

public class IAllergyPackage<T extends IAllergyModel> implements Serializable {

    private long ptPK;

    private List<T> added;

    private List<T> modified;

    private List<T> deleted;

    public List<T> getAdded() {
        return added;
    }

    public void setAdded(List<T> added) {
        this.added = added;
    }

    public List<T> getModified() {
        return modified;
    }

    public void setModified(List<T> modified) {
        this.modified = modified;
    }

    public List<T> getDeleted() {
        return deleted;
    }

    public void setDeleted(List<T> deleted) {
        this.deleted = deleted;
    }

    public long getPtPK() {
        return ptPK;
    }

    public void setPtPK(long ptPK) {
        this.ptPK = ptPK;
    }
}
