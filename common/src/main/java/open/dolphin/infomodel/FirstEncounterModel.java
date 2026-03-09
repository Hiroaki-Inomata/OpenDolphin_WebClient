package open.dolphin.infomodel;

import jakarta.persistence.*;

/**
 * 初診時情報クラス。
 */
@Entity
@Table(name = "d_first_encounter")
public class FirstEncounterModel extends KarteEntryBean implements java.io.Serializable {
        
    //@Lob ASP サーバへ配備する時、コメントアウトしてはいけない
    @Column(nullable=false)
    private byte[] payloadBytes;
    
    // discriminator 列を読み取り専用でマッピングする
    @Column(name = "docType", insertable = false, updatable = false)
    private String docType;
    
    /** Creates a new instance of FirstEncounterModel */
    public FirstEncounterModel() {
    }

    public byte[] getPayloadBytes() {
        return payloadBytes;
    }

    public void setPayloadBytes(byte[] payloadBytes) {
        this.payloadBytes = payloadBytes;
    }

    public String getDocType() {
        return docType;
    }
}
