package open.dolphin.rest;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.BadRequestException;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import open.dolphin.audit.AuditEventEnvelope;
import open.dolphin.orca.transport.StubOrcaTransport;
import open.dolphin.security.audit.AuditEventPayload;
import open.dolphin.security.audit.SessionAuditDispatcher;
import open.dolphin.testsupport.RuntimeDelegateTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OrcaAcceptanceListResourceTest extends RuntimeDelegateTestSupport {

    private OrcaAcceptanceListResource resource;
    private RecordingSessionAuditDispatcher auditDispatcher;
    private HttpServletRequest servletRequest;

    @BeforeEach
    void setUp() throws Exception {
        resource = new OrcaAcceptanceListResource();
        auditDispatcher = new RecordingSessionAuditDispatcher();

        injectField(resource, "orcaTransport", new StubOrcaTransport());
        injectField(resource, "sessionAuditDispatcher", auditDispatcher);

        servletRequest = (HttpServletRequest) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class[]{HttpServletRequest.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("getRemoteUser".equals(name)) {
                        return "F001:doctor01";
                    }
                    if ("getRemoteAddr".equals(name)) {
                        return "127.0.0.1";
                    }
                    if ("getHeader".equals(name) && args != null && args.length == 1) {
                        String header = String.valueOf(args[0]);
                        return switch (header) {
                            case "X-Request-Id" -> "req-acceptlst";
                            case "X-Trace-Id" -> "trace-acceptlst";
                            case "X-Run-Id" -> "run-acceptlst";
                            case "User-Agent" -> "JUnit";
                            default -> null;
                        };
                    }
                    return null;
                });
    }

    @Test
    void postAcceptList_returnsStubAndAudit() {
        var response = resource.postAcceptList(servletRequest, "01", "<xml/>");

        assertEquals(200, response.getStatus());
        assertEquals("application/xml", response.getMediaType().toString());
        assertEquals("run-acceptlst", response.getHeaderString("X-Run-Id"));

        String entity = (String) response.getEntity();
        assertTrue(entity.contains("<acceptlstres>"));
        assertTrue(entity.contains("<Api_Result>0000</Api_Result>"));

        assertNotNull(auditDispatcher.payload);
        assertEquals("ORCA_ACCEPT_LIST", auditDispatcher.payload.getAction());
        assertEquals("/api01rv2/acceptlstv2", auditDispatcher.payload.getResource());
        assertEquals("trace-acceptlst", auditDispatcher.payload.getTraceId());
        assertEquals("req-acceptlst", auditDispatcher.payload.getRequestId());
        assertEquals("F001:doctor01", auditDispatcher.payload.getActorId());
        assertEquals(AuditEventEnvelope.Outcome.SUCCESS, auditDispatcher.outcome);
        assertEquals("run-acceptlst", auditDispatcher.payload.getDetails().get("runId"));
    }

    @Test
    void postAcceptListWithApiPrefix_keepsLegacyRoute() {
        var response = resource.postAcceptListWithApiPrefix(servletRequest, "01", "<xml/>");

        assertEquals(200, response.getStatus());
        assertEquals("/api/api01rv2/acceptlstv2", auditDispatcher.payload.getResource());
    }

    @Test
    void postAcceptList_rejectsJsonPayloadAndRecordsFailureAudit() {
        BadRequestException exception = assertThrows(BadRequestException.class,
                () -> resource.postAcceptList(servletRequest, "01", "{\"acceptlstv2req\":{}}"));

        assertTrue(exception.getMessage().contains("xml2 payload"));
        assertNotNull(auditDispatcher.payload);
        assertEquals("ORCA_ACCEPT_LIST", auditDispatcher.payload.getAction());
        assertEquals("/api01rv2/acceptlstv2", auditDispatcher.payload.getResource());
        assertEquals(AuditEventEnvelope.Outcome.FAILURE, auditDispatcher.outcome);
        assertEquals("failed", auditDispatcher.payload.getDetails().get("status"));
        assertEquals(400, auditDispatcher.payload.getDetails().get("httpStatus"));
        assertEquals("orca.acceptlist.error", auditDispatcher.payload.getDetails().get("errorCode"));
    }

    private static void injectField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static final class RecordingSessionAuditDispatcher extends SessionAuditDispatcher {
        private AuditEventPayload payload;
        private AuditEventEnvelope.Outcome outcome;

        @Override
        public AuditEventEnvelope record(AuditEventPayload payload, AuditEventEnvelope.Outcome overrideOutcome,
                String errorCode, String errorMessage) {
            this.payload = payload;
            this.outcome = overrideOutcome;
            return null;
        }
    }
}
