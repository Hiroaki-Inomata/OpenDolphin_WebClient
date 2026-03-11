package open.dolphin.rest.dto.orca;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PrescriptionRp {

    private String rpNumber;
    private String medicalClass;
    private String medicalClassNumber;
    private String usageCode;
    private String usageName;
    private String memo;
    private Boolean patientRequested;
    private List<PrescriptionDrug> drugs;
    private List<PrescriptionClaimComment> claimComments;

    public String getRpNumber() {
        return rpNumber;
    }

    public void setRpNumber(String rpNumber) {
        this.rpNumber = rpNumber;
    }

    public String getMedicalClass() {
        return medicalClass;
    }

    public void setMedicalClass(String medicalClass) {
        this.medicalClass = medicalClass;
    }

    public String getMedicalClassNumber() {
        return medicalClassNumber;
    }

    public void setMedicalClassNumber(String medicalClassNumber) {
        this.medicalClassNumber = medicalClassNumber;
    }

    public String getUsageCode() {
        return usageCode;
    }

    public void setUsageCode(String usageCode) {
        this.usageCode = usageCode;
    }

    public String getUsageName() {
        return usageName;
    }

    public void setUsageName(String usageName) {
        this.usageName = usageName;
    }

    public String getMemo() {
        return memo;
    }

    public void setMemo(String memo) {
        this.memo = memo;
    }

    public Boolean getPatientRequested() {
        return patientRequested;
    }

    public void setPatientRequested(Boolean patientRequested) {
        this.patientRequested = patientRequested;
    }

    public List<PrescriptionDrug> getDrugs() {
        return drugs;
    }

    public void setDrugs(List<PrescriptionDrug> drugs) {
        this.drugs = drugs;
    }

    public List<PrescriptionClaimComment> getClaimComments() {
        return claimComments;
    }

    public void setClaimComments(List<PrescriptionClaimComment> claimComments) {
        this.claimComments = claimComments;
    }
}
