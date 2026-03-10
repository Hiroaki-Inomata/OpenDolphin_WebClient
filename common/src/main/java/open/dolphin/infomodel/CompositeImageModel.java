package open.dolphin.infomodel;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import javax.swing.ImageIcon;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 *
 * @author kazm
 */
@Entity
@Table(name = "d_composite_image")
public class CompositeImageModel extends KarteEntryBean implements java.io.Serializable {
    
    @Column(nullable=false)
    private String contentType;
    
    @Column(nullable=false)
    private String medicalRole;
    
    @Transient
    private String medicalRoleTableId;
    
    @Column(nullable=false)
    private String title;
    
    @Column(nullable=false)
    private String href;
    
    @Transient
    private int imageNumber;
    
    @Transient
    @JsonIgnore
    private ImageIcon icon;
    
    @Lob
    @Column(nullable=false)
    @JdbcTypeCode(SqlTypes.BINARY)
    private byte[] imageBytes;
    
    @Column(nullable=false)
    private long compositor;

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getMedicalRole() {
        return medicalRole;
    }

    public void setMedicalRole(String medicalRole) {
        this.medicalRole = medicalRole;
    }

    public String getMedicalRoleTableId() {
        return medicalRoleTableId;
    }

    public void setMedicalRoleTableId(String medicalRoleTableId) {
        this.medicalRoleTableId = medicalRoleTableId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getHref() {
        return href;
    }

    public void setHref(String href) {
        this.href = href;
    }

    public int getImageNumber() {
        return imageNumber;
    }

    public void setImageNumber(int imageNumber) {
        this.imageNumber = imageNumber;
    }

    public ImageIcon getIcon() {
        return icon;
    }

    public void setIcon(ImageIcon icon) {
        this.icon = icon;
    }

    public byte[] getImageBytes() {
        return imageBytes;
    }

    public void setImageBytes(byte[] imageBytes) {
        this.imageBytes = imageBytes;
    }

    public long getCompositor() {
        return compositor;
    }

    public void setCompositor(long compositor) {
        this.compositor = compositor;
    }
}
