package open.dolphin.orca.adapter;

import java.util.List;
import java.util.Map;

/**
 * Domain-oriented ORCA boundary for patient/reception use cases.
 * Business services must depend on this contract instead of raw XML/HTTP concerns.
 */
public interface OrcaPatientAdapter {

    SearchResult searchPatients(PatientSearchQuery query);

    UpsertResult upsertPatient(PatientUpsertCommand command);

    ReceptionResult registerReception(ReceptionCommand command);

    record PatientSearchQuery(String facilityId,
                              String patientId,
                              String fullName,
                              String kanaName,
                              String birthDate) {
    }

    record PatientUpsertCommand(String facilityId,
                                String patientId,
                                Map<String, Object> patientPayload) {
    }

    record ReceptionCommand(String facilityId,
                            String patientId,
                            String departmentCode,
                            String doctorCode,
                            String visitDate,
                            Map<String, Object> payload) {
    }

    record SearchResult(List<Map<String, Object>> patients,
                        String requestId,
                        String runId,
                        String sourceSystem) {
    }

    record UpsertResult(String patientId,
                        String orcaPatientKey,
                        String requestId,
                        String runId,
                        boolean created) {
    }

    record ReceptionResult(String receptionId,
                           String patientId,
                           String requestId,
                           String runId,
                           String status) {
    }
}
