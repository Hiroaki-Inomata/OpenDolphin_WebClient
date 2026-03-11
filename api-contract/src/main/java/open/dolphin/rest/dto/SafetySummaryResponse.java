package open.dolphin.rest.dto;

import java.util.List;

/**
 * Safety Summary Response DTO.
 * Aggregates Allergies, Active Diagnoses, and Routine Medications.
 */
public class SafetySummaryResponse {

    private List<AllergySummaryResponse> allergies;
    private List<DiagnosisSummaryResponse> diagnoses;
    private List<RoutineMedicationResponse> routineMeds;

    public SafetySummaryResponse() {
    }

    public SafetySummaryResponse(List<AllergySummaryResponse> allergies,
                                 List<DiagnosisSummaryResponse> diagnoses,
                                 List<RoutineMedicationResponse> routineMeds) {
        this.allergies = allergies;
        this.diagnoses = diagnoses;
        this.routineMeds = routineMeds;
    }

    public List<AllergySummaryResponse> getAllergies() {
        return allergies;
    }

    public void setAllergies(List<AllergySummaryResponse> allergies) {
        this.allergies = allergies;
    }

    public List<DiagnosisSummaryResponse> getDiagnoses() {
        return diagnoses;
    }

    public void setDiagnoses(List<DiagnosisSummaryResponse> diagnoses) {
        this.diagnoses = diagnoses;
    }

    public List<RoutineMedicationResponse> getRoutineMeds() {
        return routineMeds;
    }

    public void setRoutineMeds(List<RoutineMedicationResponse> routineMeds) {
        this.routineMeds = routineMeds;
    }

    public static class AllergySummaryResponse {

        private long observationId;
        private String factor;
        private String severity;
        private String identifiedDate;
        private String memo;

        public long getObservationId() {
            return observationId;
        }

        public void setObservationId(long observationId) {
            this.observationId = observationId;
        }

        public String getFactor() {
            return factor;
        }

        public void setFactor(String factor) {
            this.factor = factor;
        }

        public String getSeverity() {
            return severity;
        }

        public void setSeverity(String severity) {
            this.severity = severity;
        }

        public String getIdentifiedDate() {
            return identifiedDate;
        }

        public void setIdentifiedDate(String identifiedDate) {
            this.identifiedDate = identifiedDate;
        }

        public String getMemo() {
            return memo;
        }

        public void setMemo(String memo) {
            this.memo = memo;
        }
    }
}
