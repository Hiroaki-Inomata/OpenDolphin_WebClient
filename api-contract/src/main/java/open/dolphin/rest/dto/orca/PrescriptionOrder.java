package open.dolphin.rest.dto.orca;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PrescriptionOrder {

    private String patientId;
    private String encounterId;
    private String encounterDate;
    private String performDate;
    private Boolean patientRequested;
    private PrescriptionDoInputMeta doInputMeta;
    private List<PrescriptionRp> rps;
    private List<PrescriptionClaimComment> claimComments;
    private List<PrescriptionRemark> remarks;
    private List<PrescriptionSetting> prescriptionSettings;
    private List<PrescriptionDoctorComment> doctorComments;

    public String getPatientId() {
        return patientId;
    }

    public void setPatientId(String patientId) {
        this.patientId = patientId;
    }

    public String getEncounterId() {
        return encounterId;
    }

    public void setEncounterId(String encounterId) {
        this.encounterId = encounterId;
    }

    public String getEncounterDate() {
        return encounterDate;
    }

    public void setEncounterDate(String encounterDate) {
        this.encounterDate = encounterDate;
    }

    public String getPerformDate() {
        return performDate;
    }

    public void setPerformDate(String performDate) {
        this.performDate = performDate;
    }

    public Boolean getPatientRequested() {
        return patientRequested;
    }

    public void setPatientRequested(Boolean patientRequested) {
        this.patientRequested = patientRequested;
    }

    public PrescriptionDoInputMeta getDoInputMeta() {
        return doInputMeta;
    }

    public void setDoInputMeta(PrescriptionDoInputMeta doInputMeta) {
        this.doInputMeta = doInputMeta;
    }

    public List<PrescriptionRp> getRps() {
        return rps;
    }

    public void setRps(List<PrescriptionRp> rps) {
        this.rps = rps;
    }

    public List<PrescriptionClaimComment> getClaimComments() {
        return claimComments;
    }

    public void setClaimComments(List<PrescriptionClaimComment> claimComments) {
        this.claimComments = claimComments;
    }

    public List<PrescriptionRemark> getRemarks() {
        return remarks;
    }

    public void setRemarks(List<PrescriptionRemark> remarks) {
        this.remarks = remarks;
    }

    public List<PrescriptionSetting> getPrescriptionSettings() {
        return prescriptionSettings;
    }

    public void setPrescriptionSettings(List<PrescriptionSetting> prescriptionSettings) {
        this.prescriptionSettings = prescriptionSettings;
    }

    public List<PrescriptionDoctorComment> getDoctorComments() {
        return doctorComments;
    }

    public void setDoctorComments(List<PrescriptionDoctorComment> doctorComments) {
        this.doctorComments = doctorComments;
    }
}
