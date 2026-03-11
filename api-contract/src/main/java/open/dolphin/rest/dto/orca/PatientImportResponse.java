package open.dolphin.rest.dto.orca;

import java.util.ArrayList;
import java.util.List;

/**
 * Response payload for POST /orca/patients/import.
 */
public class PatientImportResponse extends OrcaApiResponse {

    private String facilityId;
    private int requestedCount;
    private int fetchedCount;
    private int createdCount;
    private int updatedCount;
    private int skippedCount;
    private final List<ImportError> errors = new ArrayList<>();

    public String getFacilityId() {
        return facilityId;
    }

    public void setFacilityId(String facilityId) {
        this.facilityId = facilityId;
    }

    public int getRequestedCount() {
        return requestedCount;
    }

    public void setRequestedCount(int requestedCount) {
        this.requestedCount = requestedCount;
    }

    public int getFetchedCount() {
        return fetchedCount;
    }

    public void setFetchedCount(int fetchedCount) {
        this.fetchedCount = fetchedCount;
    }

    public int getCreatedCount() {
        return createdCount;
    }

    public void setCreatedCount(int createdCount) {
        this.createdCount = createdCount;
    }

    public int getUpdatedCount() {
        return updatedCount;
    }

    public void setUpdatedCount(int updatedCount) {
        this.updatedCount = updatedCount;
    }

    public int getSkippedCount() {
        return skippedCount;
    }

    public void setSkippedCount(int skippedCount) {
        this.skippedCount = skippedCount;
    }

    public List<ImportError> getErrors() {
        return errors;
    }

    public static class ImportError {
        private String patientId;
        private String message;

        public String getPatientId() {
            return patientId;
        }

        public void setPatientId(String patientId) {
            this.patientId = patientId;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}

