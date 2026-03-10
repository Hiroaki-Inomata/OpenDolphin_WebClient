package open.dolphin.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;
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
