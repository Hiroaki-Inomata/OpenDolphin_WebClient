package open.dolphin.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.lang.reflect.Field;
import java.util.List;
import open.dolphin.infomodel.PatientVisitModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PVTServiceBeanPaginationTest {

    private PVTServiceBean service;
    private Query query;

    @BeforeEach
    void setUp() throws Exception {
        service = new PVTServiceBean();

        EntityManager em = mock(EntityManager.class);
        query = mock(Query.class);
        setField(service, "em", em);

        when(em.createQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.setFirstResult(anyInt())).thenReturn(query);
        when(query.setMaxResults(anyInt())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());
    }

    @Test
    void getPvt_withoutExplicitLimitDelegatesDefaultLimit() {
        RecordingPVTServiceBean recording = new RecordingPVTServiceBean();

        recording.getPvt("F001", "2026-03-10", 5, null, null);

        assertEquals(50, recording.lastMaxResult);
        assertTrue(recording.standardCalled);
    }

    @Test
    void getPvtWithDoctorFilter_withoutExplicitLimitDelegatesDefaultLimit() {
        RecordingPVTServiceBean recording = new RecordingPVTServiceBean();

        recording.getPvt("F001", "DOC01", "true", "2026-03-10", 5, null, null);

        assertEquals(50, recording.lastMaxResult);
        assertTrue(recording.doctorFilterCalled);
    }

    @Test
    void getPvt_withExplicitLimitClampsToConfiguredMaximum() {
        service.getPvt("F001", "2026-03-10", 0, 500, null, null);

        verify(query).setMaxResults(200);
    }

    @Test
    void getPvt_withNonPositiveLimitFallsBackToDefault() {
        service.getPvt("F001", "DOC01", "true", "2026-03-10", 0, 0, null, null);

        verify(query).setMaxResults(50);
    }

    @Test
    void getPvt_withNegativeFirstResultUsesZero() {
        service.getPvt("F001", "2026-03-10", -10, 20, null, null);

        verify(query).setFirstResult(0);
        verify(query).setMaxResults(20);
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static final class RecordingPVTServiceBean extends PVTServiceBean {
        private int lastMaxResult = -1;
        private boolean standardCalled;
        private boolean doctorFilterCalled;

        @Override
        public List<PatientVisitModel> getPvt(String fid, String date, int firstResult, int maxResult,
                String appoDateFrom, String appoDateTo) {
            lastMaxResult = maxResult;
            standardCalled = true;
            return List.of();
        }

        @Override
        public List<PatientVisitModel> getPvt(String fid, String did, String unassigned, String date, int firstResult,
                int maxResult, String appoDateFrom, String appoDateTo) {
            lastMaxResult = maxResult;
            doctorFilterCalled = true;
            return List.of();
        }
    }
}
