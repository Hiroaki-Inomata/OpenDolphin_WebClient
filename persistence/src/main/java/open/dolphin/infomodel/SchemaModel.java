package open.dolphin.infomodel;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import javax.swing.ImageIcon;

/**
 * SchemaModel
 *
 * @author Kazushi Minagawa, Digital Globe, Inc.
 *
 */
@Entity
@Table(name = "d_image")
public class SchemaModel extends KarteEntryBean 
        implements java.io.Serializable, java.lang.Cloneable {
    
    // ExtRef
    @Embedded
    private ExtRefModel extRef;
    
    @Column(nullable = false)
    private String uri;

    @Column(nullable = false)
    private String digest;
    
    // Document
    @ManyToOne
    @JoinColumn(name="doc_id", nullable=false)
    private DocumentModel document;
    
    // Comaptible props
    @Transient
    private String fileName;
    
    @Transient
    @JsonIgnore
    private ImageIcon icon;
    
    @Transient
    private int imageNumber;
    
    @Transient
    private ModuleModel imageStamp; // ModuleInfoBean + model 

    @Transient
    private byte[] imageBytes;
    
    
    /** Creates new Schema */
    public SchemaModel() {
    }
    
    public ExtRefModel getExtRefModel() {
        return extRef;
    }
    
    public void setExtRefModel(ExtRefModel val) {
        extRef = val;
    }
    
    public DocumentModel getDocumentModel() {
        return document;
    }
    
    public void setDocumentModel(DocumentModel document) {
        this.document = document;
    }
    
    public String getUri() {
        return uri;
    }
    
    public void setUri(String uri) {
        this.uri = uri;
    }

    public String getDigest() {
        return digest;
    }

    public void setDigest(String digest) {
        this.digest = digest;
    }

    public byte[] getImageBytes() {
        return imageBytes;
    }

    public void setImageBytes(byte[] imageBytes) {
        this.imageBytes = imageBytes;
    }
    
    public ImageIcon getIcon() {
        return icon;
    }
    
    public void setIcon(ImageIcon val) {
        icon = val;
    }
    
    public int getImageNumber() {
        return imageNumber;
    }
    
    public void setImageNumber(int imageNumber) {
        this.imageNumber = imageNumber;
    }
    
    public String getFileName() {
        return fileName;
    }
    
    public void setFileName(String val) {
        fileName = val;
    }
    
    public IInfoModel getModel() {
        return (IInfoModel)getExtRefModel();
    }
    
    public void setModel(IInfoModel val) {
        setExtRefModel((ExtRefModel)val);
    }
    
    /**
     * 確定日及びイメージ番号で比較する。
     * @param other
     * @return
     */
    @Override
    public int compareTo(Object other) {
        int result = super.compareTo(other);
        if (result == 0) {
            // primittive なので比較はOK
            int no1 = getImageNumber();
            int no2 = ((SchemaModel) other).getImageNumber();
            result = no1 - no2;
        }
        return result;
    }

    @Override
    protected Object clone() throws CloneNotSupportedException {
        SchemaModel ret = new SchemaModel();
        ret.setConfirmed(this.getConfirmed());
        ret.setEnded(this.getEnded());
        ret.setExtRefModel((ExtRefModel)this.getExtRefModel().clone());
        ret.setFileName(this.getFileName());
        ret.setFirstConfirmed(this.getConfirmed());
        ret.setImageNumber(this.getImageNumber());
        ret.setLinkId(this.getLinkId());
        ret.setLinkRelation(this.getLinkRelation());
        ret.setRecorded(this.getRecorded());
        ret.setStarted(this.getStarted());
        ret.setStatus(this.getStatus());

        if (this.getIcon()!=null) {
            ret.setIcon(new ImageIcon(this.getIcon().getImage()));
        }

        ret.setUri(this.getUri());
        ret.setDigest(this.getDigest());

        if (this.getImageBytes()!=null) {
            byte[] dest = new byte[this.getImageBytes().length];
            System.arraycopy(this.getImageBytes(), 0, dest, 0, this.getImageBytes().length);
            ret.setImageBytes(dest);
        }

        return ret;
    }

    public ModuleModel getImageStamp() {
        return imageStamp;
    }

    public void setImageStamp(ModuleModel imageStamp) {
        this.imageStamp = imageStamp;
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("confirmed=").append(this.getConfirmed()).append("\n");
        sb.append("started=").append(this.getStarted()).append("\n");
        sb.append("recorded=").append(this.getRecorded()).append("\n");
        sb.append("status=").append(this.getStatus()).append("\n");
        sb.append("userModel PK=").append(this.getUserModel().getId()).append("\n");
        sb.append("karteBean PK=").append(this.getKarteBean().getId()).append("\n");
        sb.append("contentType=").append(this.getExtRefModel().getContentType()).append("\n");
        sb.append("medicalRole=").append(this.getExtRefModel().getMedicalRole()).append("\n");
        sb.append("title=").append(this.getExtRefModel().getTitle()).append("\n");
        sb.append("href=").append(this.getExtRefModel().getHref()).append("\n");
        sb.append("uri=").append(this.getUri()).append("\n");
        sb.append("digest=").append(this.getDigest()).append("\n");
        return sb.toString();
    }
}
