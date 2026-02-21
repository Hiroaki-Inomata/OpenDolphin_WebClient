package open.dolphin.touch.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import open.dolphin.audit.AuditEventEnvelope;
import open.dolphin.security.audit.AuditEventPayload;
import open.dolphin.security.audit.SessionAuditDispatcher;
import org.junit.jupiter.api.Test;

class TouchAuditHelperTest {

    @Test
    void recordDoesNotPersistRawConsentToken() {
        TouchAuditHelper helper = new TouchAuditHelper();
        CapturingDispatcher dispatcher = new CapturingDispatcher();
        helper.sessionAuditDispatcher = dispatcher;
        helper.auditTrailService = null;
        helper.sessionTraceManager = null;

        TouchRequestContext context = new TouchRequestContext(
                "F001:touch-user",
                "F001",
                "touch-user",
                "trace-touch",
                "req-touch",
                "consent-check",
                "raw-consent-token",
                "192.0.2.10",
                "JUnit");

        helper.recordSuccess(context, "TOUCH_PATIENT_READ", "/touch/patient", Map.of("patientId", "P0009"));

        AuditEventPayload payload = dispatcher.payload;
        assertNotNull(payload);
        assertEquals("P0009", payload.getPatientId());
        assertEquals(Boolean.TRUE, payload.getDetails().get("tokenPresent"));
        assertTrue(payload.getDetails().containsKey("tokenHash"));
        assertTrue(payload.getDetails().containsKey("tokenHashAlg"));
        assertNotEquals("raw-consent-token", payload.getDetails().get("tokenHash"));
        assertFalse(payload.getDetails().containsKey("consentToken"));
    }

    private static final class CapturingDispatcher extends SessionAuditDispatcher {
        private AuditEventPayload payload;

        @Override
        public AuditEventEnvelope record(AuditEventPayload payload) {
            this.payload = payload;
            return AuditEventEnvelope.builder(payload.getAction(), payload.getResource())
                    .actorId("F001:touch-user")
                    .traceId("trace-touch")
                    .requestId("req-touch")
                    .build();
        }
    }
}
