package open.dolphin.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.lang.reflect.Field;
import open.dolphin.infomodel.KarteBean;
import open.dolphin.infomodel.PatientModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class PatientServiceBeanAddPatientTest {

    private PatientServiceBean service;
    private EntityManager em;
    private TypedQuery<KarteBean> karteQuery;

    @BeforeEach
    void setUp() throws Exception {
        service = new PatientServiceBean();
        em = mock(EntityManager.class);
        karteQuery = mock(TypedQuery.class);
        setField(service, "em", em);
    }

    @Test
    void addPatient_usesPersistAndFlush_withoutManualSequence() {
        PatientModel patient = new PatientModel();
        patient.setFacilityId("F001");
        patient.setPatientId("P001");
        patient.setFullName("Test Patient");
        patient.setGender("M");

        doAnswer(invocation -> {
            PatientModel persisted = invocation.getArgument(0);
            persisted.setId(101L);
            return null;
        }).when(em).persist(patient);
        when(em.createQuery("from KarteBean k where k.patient.id = :patientPk", KarteBean.class))
                .thenReturn(karteQuery);
        when(karteQuery.setParameter("patientPk", 101L)).thenReturn(karteQuery);
        when(karteQuery.setMaxResults(1)).thenReturn(karteQuery);
        when(karteQuery.getResultList()).thenReturn(java.util.List.of());

        long id = service.addPatient(patient);

        assertThat(id).isEqualTo(101L);
        InOrder order = inOrder(em);
        order.verify(em).persist(patient);
        order.verify(em).flush();
        order.verify(em).persist(org.mockito.ArgumentMatchers.any(KarteBean.class));
        order.verify(em).flush();
        verify(em, never()).createNativeQuery(org.mockito.ArgumentMatchers.anyString());
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }
}
