package open.dolphin.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.NotFoundException;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;
import open.dolphin.infomodel.HealthInsuranceModel;
import open.dolphin.converter.PatientVisitListConverter;
import open.dolphin.infomodel.PatientModel;
import open.dolphin.infomodel.PatientVisitModel;
import open.dolphin.session.PVTServiceBean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PVTResourceLimitTest {

    private PVTResource resource;
    private RecordingPVTServiceBean pvtServiceBean;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() throws Exception {
        resource = new PVTResource();
        pvtServiceBean = new RecordingPVTServiceBean();
        setField(resource, "pVTServiceBean", pvtServiceBean);

        request = mock(HttpServletRequest.class);
        when(request.getRemoteUser()).thenReturn("F001:doctor01");
    }

    @Test
    void getPvt_withoutLimitUsesDefaultPageSize() {
        PatientVisitListConverter response = resource.getPvt(request, "2026-03-10,0,2026-03-10,2026-03-10", null);

        assertTrue(pvtServiceBean.standardPageSizeCalled);
        assertEquals(50, pvtServiceBean.lastMaxResult);
        assertNotNull(response.getList());
        assertEquals(1, response.getList().size());
    }

    @Test
    void getPvt_withLimitPassesExplicitLimitToStandardRoute() {
        PatientVisitListConverter response = resource.getPvt(request, "2026-03-10,0,2026-03-10,2026-03-10", 25);

        assertTrue(pvtServiceBean.standardPageSizeCalled);
        assertEquals(25, pvtServiceBean.lastMaxResult);
        assertEquals("F001", pvtServiceBean.lastFacilityId);
        assertNotNull(response.getList());
        assertEquals(1, response.getList().size());
    }

    @Test
    void getPvt_withLimitPassesExplicitLimitToDoctorFilterRoute() {
        PatientVisitListConverter response =
                resource.getPvt(request, "DOC01,true,2026-03-10,0,2026-03-10,2026-03-10", 75);

        assertTrue(pvtServiceBean.doctorPageSizeCalled);
        assertEquals(75, pvtServiceBean.lastMaxResult);
        assertEquals("DOC01", pvtServiceBean.lastDoctorId);
        assertNotNull(response.getList());
        assertEquals(1, response.getList().size());
    }

    @Test
    void postPvt_setsFacilityAndInsuranceRelationBeforeAdd() throws Exception {
        String json = """
                {
                  "patientModel": {
                    "patientId": "00001",
                    "fullName": "テスト患者",
                    "healthInsurances": [
                      {"insuranceNumber": "H001"}
                    ]
                  }
                }
                """;

        String result = resource.postPvt(request, json);

        assertEquals("1", result);
        assertNotNull(pvtServiceBean.lastAdded);
        assertEquals("F001", pvtServiceBean.lastAdded.getFacilityId());
        assertEquals("F001", pvtServiceBean.lastAdded.getPatientModel().getFacilityId());
        HealthInsuranceModel insurance =
                pvtServiceBean.lastAdded.getPatientModel().getHealthInsurances().iterator().next();
        assertSame(pvtServiceBean.lastAdded.getPatientModel(), insurance.getPatient());
    }

    @Test
    void putPvtState_throwsNotFoundWhenUpdateCountIsZero() {
        pvtServiceBean.updateStateResult = 0;

        assertThrows(NotFoundException.class, () -> resource.putPvtState(request, "1001,1"));
        assertEquals("F001", pvtServiceBean.lastFacilityIdForUpdateState);
        assertEquals(1001L, pvtServiceBean.lastPvtPkForUpdateState);
        assertEquals(1, pvtServiceBean.lastStateForUpdateState);
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static final class RecordingPVTServiceBean extends PVTServiceBean {
        private boolean standardPageSizeCalled;
        private boolean doctorPageSizeCalled;
        private int lastMaxResult = -1;
        private String lastFacilityId;
        private String lastDoctorId;
        private PatientVisitModel lastAdded;
        private int updateStateResult = 1;
        private String lastFacilityIdForUpdateState;
        private long lastPvtPkForUpdateState;
        private int lastStateForUpdateState;

        @Override
        public List<PatientVisitModel> getPvt(String fid, String date, int firstResult, int maxResult,
                String appoDateFrom, String appoDateTo) {
            standardPageSizeCalled = true;
            lastFacilityId = fid;
            lastMaxResult = maxResult;
            return visitList(fid);
        }

        @Override
        public List<PatientVisitModel> getPvt(String fid, String did, String unassigned, String date, int firstResult,
                int maxResult, String appoDateFrom, String appoDateTo) {
            doctorPageSizeCalled = true;
            lastFacilityId = fid;
            lastDoctorId = did;
            lastMaxResult = maxResult;
            return visitList(fid);
        }

        @Override
        public int addPvt(PatientVisitModel pvt) {
            lastAdded = pvt;
            return 1;
        }

        @Override
        public int updatePvtStateForFacility(String fid, long pvtPK, int state) {
            lastFacilityIdForUpdateState = fid;
            lastPvtPkForUpdateState = pvtPK;
            lastStateForUpdateState = state;
            return updateStateResult;
        }

        private List<PatientVisitModel> visitList(String fid) {
            PatientModel patient = new PatientModel();
            patient.setPatientId("00001");
            patient.setFullName("テスト患者");

            PatientVisitModel visit = new PatientVisitModel();
            visit.setId(1L);
            visit.setFacilityId(fid);
            visit.setPatientModel(patient);
            visit.setPvtDate(LocalDateTime.of(2026, 3, 10, 9, 0));
            return List.of(visit);
        }
    }
}
