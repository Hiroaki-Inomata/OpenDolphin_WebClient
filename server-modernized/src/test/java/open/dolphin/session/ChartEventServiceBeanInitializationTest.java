package open.dolphin.session;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import open.dolphin.mbean.ServletContextHolder;
import open.dolphin.session.support.ChartEventStreamPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChartEventServiceBeanInitializationTest {

    private static final String QUERY_PVT_BY_DATE =
            "from PatientVisitModel p where p.pvtDate >= :fromDate and p.pvtDate < :toDate order by p.id";

    private ChartEventServiceBean service;
    private ServletContextHolder contextHolder;
    private EntityManager em;
    private Query query;

    @BeforeEach
    void setUp() throws Exception {
        service = new ChartEventServiceBean();
        contextHolder = new ServletContextHolder();
        contextHolder.setToday();
        em = mock(EntityManager.class);
        query = mock(Query.class);

        when(em.createQuery(QUERY_PVT_BY_DATE)).thenReturn(query);
        when(query.setParameter(eq("fromDate"), any())).thenReturn(query);
        when(query.setParameter(eq("toDate"), any())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());

        setField(service, "contextHolder", contextHolder);
        setField(service, "chartEventStreamPublisher", mock(ChartEventStreamPublisher.class));
        setField(service, "em", em);
    }

    @Test
    void ensureInitializedBindsPatientVisitWindowAsLocalDateTime() throws Exception {
        service.ensureInitialized();

        LocalDateTime expectedFromDate = LocalDate.ofInstant(
                contextHolder.getToday().toInstant(), ZoneId.systemDefault()).atStartOfDay();
        LocalDateTime expectedToDate = LocalDate.ofInstant(
                contextHolder.getTomorrow().toInstant(), ZoneId.systemDefault()).atStartOfDay();

        verify(query).setParameter("fromDate", expectedFromDate);
        verify(query).setParameter("toDate", expectedToDate);
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
