package open.dolphin.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import open.dolphin.mbean.PvtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PvtWorkerHealthResourceTest {

    @Mock
    private PvtService pvtService;

    @InjectMocks
    private PvtWorkerHealthResource resource;

    @Test
    void healthReturnsOkWhenStatusIsUp() {
        when(pvtService.workerHealthBody()).thenReturn(Map.of(
                "status", "UP",
                "reasons", List.of()));

        Response response = resource.health();

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(castBody(response).get("status")).isEqualTo("UP");
    }

    @Test
    void healthReturnsServiceUnavailableWhenStatusIsDegraded() {
        when(pvtService.workerHealthBody()).thenReturn(Map.of(
                "status", "DEGRADED",
                "reasons", List.of("poison_queue_non_empty")));

        Response response = resource.health();

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(castBody(response).get("status")).isEqualTo("DEGRADED");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castBody(Response response) {
        return (Map<String, Object>) response.getEntity();
    }
}
