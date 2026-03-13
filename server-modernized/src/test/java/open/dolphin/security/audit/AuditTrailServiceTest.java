package open.dolphin.security.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;
import open.dolphin.infomodel.AuditEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AuditTrailServiceTest {

    @Test
    void recordDropsUnallowlistedSensitiveDetailsAndDoesNotBackfillPatientId() throws Exception {
        AuditTrailService service = new AuditTrailService();
        EntityManager em = mock(EntityManager.class);
        Query query = mock(Query.class);
        when(em.createNativeQuery(anyString(), eq(String.class))).thenReturn(query);
        when(query.getResultStream()).thenReturn(Stream.of("prev-hash"));
        inject(service, "em", em);

        AuditEventPayload payload = new AuditEventPayload();
        payload.setActorId("F001:doctor01");
        payload.setActorDisplayName("doctor01");
        payload.setAction("PATIENT_READ");
        payload.setResource("/patient");
        payload.setTraceId("trace-audit");
        payload.setRequestId("req-audit");
        payload.setOutcome("SUCCESS");

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("patientId", "P0001");
        details.put("consentToken", "raw-consent-token");
        details.put("tokenHash", "hash-value");
        payload.setDetails(details);

        service.record(payload);

        ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(em).persist(eventCaptor.capture());
        verify(em).flush();
        verify(em, never()).createQuery(startsWith("update"));

        AuditEvent event = eventCaptor.getValue();
        assertNotNull(event);
        assertEquals(null, event.getPatientId());
        assertNotNull(event.getEventHash());
        assertNotNull(event.getPayloadHash());
        assertEquals("prev-hash", event.getPreviousHash());
        assertFalse(event.getPayload().contains("\"patientId\""));
        assertFalse(event.getPayload().contains("raw-consent-token"));
        assertFalse(event.getPayload().contains("\"consentToken\""));
        assertTrueContains(event.getPayload(), "\"tokenHash\":\"hash-value\"");
    }

    private static void inject(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void assertTrueContains(String value, String token) {
        if (value == null || !value.contains(token)) {
            throw new AssertionError("Expected to contain token: " + token + ", actual=" + value);
        }
    }
}
