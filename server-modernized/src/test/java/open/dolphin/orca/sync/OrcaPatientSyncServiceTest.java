package open.dolphin.orca.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import open.dolphin.infomodel.PatientModel;
import open.dolphin.orca.service.OrcaWrapperService;
import open.dolphin.rest.dto.orca.PatientBatchRequest;
import open.dolphin.rest.dto.orca.PatientBatchResponse;
import open.dolphin.rest.dto.orca.PatientDetail;
import open.dolphin.rest.dto.orca.PatientIdListRequest;
import open.dolphin.rest.dto.orca.PatientIdListResponse;
import open.dolphin.rest.dto.orca.PatientIdListResponse.PatientSyncEntry;
import open.dolphin.rest.dto.orca.PatientImportResponse;
import open.dolphin.rest.dto.orca.PatientSummary;
import open.dolphin.rest.dto.orca.PatientSyncRequest;
import open.dolphin.session.PatientServiceBean;
import org.junit.jupiter.api.Test;

class OrcaPatientSyncServiceTest {

    @Test
    void splitsWhenPatientIdListOverLimit() {
        FakeWrapperService wrapper = new FakeWrapperService();
        StubPatientService patientService = new StubPatientService();
        OrcaPatientSyncService service = new OrcaPatientSyncService(wrapper, patientService, null);

        PatientSyncRequest request = new PatientSyncRequest();
        request.setStartDate(LocalDate.of(2026, 2, 1));
        request.setEndDate(LocalDate.of(2026, 2, 2));
        request.setClassCode("01");

        PatientImportResponse response = service.syncPatients("facility", request, "20260210T094238Z");
        assertEquals(3, wrapper.idListCalls);
        assertEquals(2, response.getRequestedCount());
        assertEquals(2, response.getFetchedCount());
        assertEquals(2, response.getCreatedCount());
        assertEquals(0, response.getUpdatedCount());
    }

    @Test
    void overLimitSingleDayThrows() {
        FakeWrapperService wrapper = new FakeWrapperService();
        wrapper.overLimitSingleDay = true;
        StubPatientService patientService = new StubPatientService();
        OrcaPatientSyncService service = new OrcaPatientSyncService(wrapper, patientService, null);

        PatientSyncRequest request = new PatientSyncRequest();
        request.setStartDate(LocalDate.of(2026, 2, 1));
        request.setEndDate(LocalDate.of(2026, 2, 1));
        request.setClassCode("01");

        assertThrows(RuntimeException.class, () -> service.syncPatients("facility", request, "20260210T094238Z"));
    }

    private static final class FakeWrapperService extends OrcaWrapperService {
        private int idListCalls;
        private boolean overLimitSingleDay;

        @Override
        public PatientIdListResponse getPatientIdList(PatientIdListRequest request) {
            idListCalls++;
            LocalDate start = request.getStartDate();
            LocalDate end = request.getEndDate();
            boolean multiDay = start != null && end != null && !start.equals(end);
            PatientIdListResponse response = new PatientIdListResponse();
            response.setApiResult("0000");
            response.setApiResultMessage("正常終了");
            if (multiDay || overLimitSingleDay) {
                // Signal "over limit" so sync service splits the range.
                response.setTargetPatientCount(1001);
                for (int i = 0; i < 1000; i++) {
                    // Returning 1000 items matches ORCA documented cap and triggers split even if target is absent.
                    PatientSyncEntry entry = new PatientSyncEntry();
                    PatientSummary summary = new PatientSummary();
                    summary.setPatientId(String.format("%06d", i + 1));
                    entry.setSummary(summary);
                    response.getPatients().add(entry);
                }
                return response;
            }
            response.setTargetPatientCount(1);
            PatientSyncEntry entry = new PatientSyncEntry();
            PatientSummary summary = new PatientSummary();
            summary.setPatientId(start != null && start.getDayOfMonth() == 1 ? "000001" : "000002");
            entry.setSummary(summary);
            response.getPatients().add(entry);
            return response;
        }

        @Override
        public PatientBatchResponse getPatientBatch(PatientBatchRequest request) {
            PatientBatchResponse response = new PatientBatchResponse();
            response.setApiResult("0000");
            response.setApiResultMessage("正常終了");
            if (request.getPatientIds() == null) {
                response.setTargetPatientCount(0);
                response.setNoTargetPatientCount(0);
                return response;
            }
            for (String pid : request.getPatientIds()) {
                PatientDetail detail = new PatientDetail();
                PatientSummary summary = new PatientSummary();
                summary.setPatientId(pid);
                summary.setWholeName("テスト患者" + pid);
                summary.setWholeNameKana("テスト");
                summary.setBirthDate("1970-01-01");
                summary.setSex("1");
                detail.setSummary(summary);
                detail.setZipCode("1000001");
                detail.setAddress("東京都");
                detail.setPhoneNumber1("0311112222");
                detail.setPhoneNumber2("09011112222");
                response.getPatients().add(detail);
            }
            response.setTargetPatientCount(response.getPatients().size());
            response.setNoTargetPatientCount(0);
            return response;
        }
    }

    private static final class StubPatientService extends PatientServiceBean {
        private final Map<String, PatientModel> store = new HashMap<>();
        private long seq = 1L;

        @Override
        public PatientModel getPatientById(String fid, String pid) {
            return store.get(fid + ":" + pid);
        }

        @Override
        public long addPatient(PatientModel patient) {
            patient.setId(seq++);
            store.put(patient.getFacilityId() + ":" + patient.getPatientId(), patient);
            return patient.getId();
        }

        @Override
        public int update(PatientModel patient) {
            store.put(patient.getFacilityId() + ":" + patient.getPatientId(), patient);
            return 1;
        }
    }
}

