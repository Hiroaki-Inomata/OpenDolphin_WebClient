package open.dolphin.infomodel;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * HealthInsuranceModel
 *
 * @author Minagawa,kazushi.
 *
 */
@Entity
@Table(name = "d_health_insurance")
public class HealthInsuranceModel extends InfoModel implements java.io.Serializable {
    
    // PK
    @Id @GeneratedValue(strategy=GenerationType.AUTO)
    private long id;
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "bean_json", nullable = false, columnDefinition = "jsonb")
    private String beanJson;
    
    // 患者
    @ManyToOne
    @JoinColumn(name="patient_id", nullable=false)
    private PatientModel patient;
    
    /**
     * Idを返す。
     * @return Id
     */
    public long getId() {
        return id;
    }
    
    /**
     * Idを設定する。
     * @param id Id
     */
    public void setId(long id) {
        this.id = id;
    }
    
    /**
     * PVTHealthInsuranceModel の JSON データを設定する。
     * @param beanJson JSON
     */
    public void setBeanJson(String beanJson) {
        this.beanJson = beanJson;
    }
    
    /**
     * PVTHealthInsuranceModel の JSON データを返す。
     * @return JSON
     */
    public String getBeanJson() {
        return beanJson;
    }
    
    /**
     * 患者を返す。
     * @return 患者
     */
    public PatientModel getPatient() {
        return patient;
    }
    
    /**
     * 患者を設定する。
     * @param patient 患者
     */
    public void setPatient(PatientModel patient) {
        this.patient = patient;
    }
    
    @Override
    public int hashCode() {
        final int PRIME = 31;
        int result = 1;
        result = PRIME * result + (int) (id ^ (id >>> 32));
        return result;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        final HealthInsuranceModel other = (HealthInsuranceModel) obj;
        if (id != other.id)
            return false;
        return true;
    }
}
