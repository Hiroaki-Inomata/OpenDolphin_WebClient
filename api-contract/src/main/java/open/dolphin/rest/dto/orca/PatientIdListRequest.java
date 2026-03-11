package open.dolphin.rest.dto.orca;

import java.time.LocalDate;

/**
 * Request payload for POST /orca/patients/id-list.
 */
public class PatientIdListRequest {

    private LocalDate startDate;
    private LocalDate endDate;
    private String classCode = "01";
    private boolean includeTestPatient;

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public String getClassCode() {
        return classCode;
    }

    public void setClassCode(String classCode) {
        if (classCode == null || classCode.isBlank()) {
            this.classCode = "01";
        } else {
            this.classCode = classCode.trim();
        }
    }

    public boolean isIncludeTestPatient() {
        return includeTestPatient;
    }

    public void setIncludeTestPatient(boolean includeTestPatient) {
        this.includeTestPatient = includeTestPatient;
    }
}
