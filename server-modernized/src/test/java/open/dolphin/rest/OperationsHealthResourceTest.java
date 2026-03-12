package open.dolphin.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import open.dolphin.mbean.PvtService;
import open.dolphin.orca.transport.RestOrcaTransport;
import open.dolphin.storage.attachment.AttachmentStorageManager;
import open.dolphin.storage.attachment.AttachmentStorageMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OperationsHealthResourceTest {

    @Mock
    private EntityManager em;

    @Mock
    private Query query;

    @Mock
    private RestOrcaTransport restOrcaTransport;

    @Mock
    private AttachmentStorageManager attachmentStorageManager;

    @Mock
    private PvtService pvtService;

    @InjectMocks
    private OperationsHealthResource resource;

    @Test
    void healthReturnsUp() {
        Response response = resource.health();

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(castBody(response).get("status")).isEqualTo("UP");
    }

    @Test
    void readinessReturnsOkWhenAllChecksAreUp() {
        when(em.createNativeQuery(anyString())).thenReturn(query);
        when(query.getSingleResult()).thenReturn(1);
        when(restOrcaTransport.auditSummary()).thenReturn("orca.host=trial.orca.local,orca.port=443");
        when(attachmentStorageManager.getMode()).thenReturn(AttachmentStorageMode.DATABASE);
        when(pvtService.workerHealthBody()).thenReturn(Map.of(
                "status", "UP",
                "reasons", List.of()));

        Response response = resource.readiness();

        assertThat(response.getStatus()).isEqualTo(200);
        Map<String, Object> body = castBody(response);
        assertThat(body.get("status")).isEqualTo("UP");
        assertThat(castChecks(body).keySet()).containsExactly(
                "database",
                "orca",
                "attachmentStorage",
                "pvtQueue");
    }

    @Test
    void readinessReturnsServiceUnavailableWhenCriticalCheckFails() {
        when(em.createNativeQuery(anyString())).thenThrow(new IllegalStateException("db unavailable"));
        when(restOrcaTransport.auditSummary()).thenReturn("orca.host=unknown");
        when(attachmentStorageManager.getMode()).thenReturn(AttachmentStorageMode.DATABASE);
        when(pvtService.workerHealthBody()).thenReturn(Map.of(
                "status", "DEGRADED",
                "reasons", List.of("poison_queue_non_empty")));

        Response response = resource.readiness();

        assertThat(response.getStatus()).isEqualTo(503);
        Map<String, Object> body = castBody(response);
        assertThat(body.get("status")).isEqualTo("DOWN");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castBody(Response response) {
        return (Map<String, Object>) response.getEntity();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castChecks(Map<String, Object> body) {
        return (Map<String, Object>) body.get("checks");
    }
}
