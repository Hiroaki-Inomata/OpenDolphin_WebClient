package open.dolphin.rest.dto;

import java.util.List;
import open.dolphin.infomodel.AllergyModel;

/**
 * Safety Summary Response DTO.
 * Aggregates Allergies, Active Diagnoses, and Routine Medications.
 */
public class SafetySummaryResponse {

    private List<AllergyModel> allergies;
    private List<DiagnosisSummaryResponse> diagnoses;
    private List<RoutineMedicationResponse> routineMeds;

    public SafetySummaryResponse() {
    }

    public SafetySummaryResponse(List<AllergyModel> allergies,
                                 List<DiagnosisSummaryResponse> diagnoses,
                                 List<RoutineMedicationResponse> routineMeds) {
        this.allergies = allergies;
        this.diagnoses = diagnoses;
        this.routineMeds = routineMeds;
    }

    public List<AllergyModel> getAllergies() {
        return allergies;
    }

    public void setAllergies(List<AllergyModel> allergies) {
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
}
