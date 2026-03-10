package open.dolphin.infomodel;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Column;

/**
 * MemoModel
 *
 * @author Minagawa, Kazushi
 *
 */
@Entity
@Table(name = "d_patient_memo")
public class PatientMemoModel extends KarteEntryBean implements java.io.Serializable {

    // DolphinPro と crala OpenDolphin -> @Lobアノテーションをつける
    // OpenDolphin ASP アノテーションなし
    private String memo;
//masuda^    
    @Column(name = "memo2", columnDefinition = "text")
    private String memo2;
    
    public String getMemo() {
        return memo2!=null ? memo2: memo;
    }
    
    public void setMemo(String memo) {
        this.memo2 = memo;
    }
//masuda$      
}
