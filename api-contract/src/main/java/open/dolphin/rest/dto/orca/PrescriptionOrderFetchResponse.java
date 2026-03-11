package open.dolphin.rest.dto.orca;

public class PrescriptionOrderFetchResponse {

    private String apiResult;
    private String apiResultMessage;
    private String runId;
    private String patientId;
    private String encounterId;
    private String encounterDate;
    private boolean found;
    private PrescriptionOrder order;

    public String getApiResult() {
        return apiResult;
    }

    public void setApiResult(String apiResult) {
        this.apiResult = apiResult;
    }

    public String getApiResultMessage() {
        return apiResultMessage;
    }

    public void setApiResultMessage(String apiResultMessage) {
        this.apiResultMessage = apiResultMessage;
    }

    public String getRunId() {
        return runId;
    }

    public void setRunId(String runId) {
        this.runId = runId;
    }

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

    public boolean isFound() {
        return found;
    }

    public void setFound(boolean found) {
        this.found = found;
    }

    public PrescriptionOrder getOrder() {
        return order;
    }

    public void setOrder(PrescriptionOrder order) {
        this.order = order;
    }
}
