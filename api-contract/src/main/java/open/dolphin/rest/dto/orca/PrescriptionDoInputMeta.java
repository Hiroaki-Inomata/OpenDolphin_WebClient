package open.dolphin.rest.dto.orca;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PrescriptionDoInputMeta {

    private Boolean importedFromDo;
    private String sourcePatientId;
    private String sourceEncounterId;
    private String sourceEncounterDate;
    private String sourceOrderId;
    private String importedBy;
    private String importedAt;
    private String policyVersion;
    private String runId;

    public Boolean getImportedFromDo() {
        return importedFromDo;
    }

    public void setImportedFromDo(Boolean importedFromDo) {
        this.importedFromDo = importedFromDo;
    }

    public String getSourcePatientId() {
        return sourcePatientId;
    }

    public void setSourcePatientId(String sourcePatientId) {
        this.sourcePatientId = sourcePatientId;
    }

    public String getSourceEncounterId() {
        return sourceEncounterId;
    }

    public void setSourceEncounterId(String sourceEncounterId) {
        this.sourceEncounterId = sourceEncounterId;
    }

    public String getSourceEncounterDate() {
        return sourceEncounterDate;
    }

    public void setSourceEncounterDate(String sourceEncounterDate) {
        this.sourceEncounterDate = sourceEncounterDate;
    }

    public String getSourceOrderId() {
        return sourceOrderId;
    }

    public void setSourceOrderId(String sourceOrderId) {
        this.sourceOrderId = sourceOrderId;
    }

    public String getImportedBy() {
        return importedBy;
    }

    public void setImportedBy(String importedBy) {
        this.importedBy = importedBy;
    }

    public String getImportedAt() {
        return importedAt;
    }

    public void setImportedAt(String importedAt) {
        this.importedAt = importedAt;
    }

    public String getPolicyVersion() {
        return policyVersion;
    }

    public void setPolicyVersion(String policyVersion) {
        this.policyVersion = policyVersion;
    }

    public String getRunId() {
        return runId;
    }

    public void setRunId(String runId) {
        this.runId = runId;
    }
}
