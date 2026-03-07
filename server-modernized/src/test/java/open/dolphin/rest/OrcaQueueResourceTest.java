package open.dolphin.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import open.dolphin.rest.admin.AdminConfigSnapshot;
import open.dolphin.rest.admin.AdminConfigStore;
import open.dolphin.session.UserServiceBean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrcaQueueResourceTest {

    private static final String ALLOW_MOCK_ENV = "OPENDOLPHIN_ALLOW_MOCK_ORCA_QUEUE";

    @Mock
    AdminConfigStore adminConfigStore;

    @Mock
    OrcaQueueStore queueStore;

    @Mock
    UserServiceBean userServiceBean;

    @Mock
    HttpServletRequest request;

    @InjectMocks
    OrcaQueueResource resource;

    @BeforeEach
    void setUp() {
        when(request.getRemoteUser()).thenReturn("F001:admin");
    }

    @AfterEach
    void tearDown() {
        System.clearProperty(ALLOW_MOCK_ENV);
    }

    @Test
    void liveModeListReturnsCapabilitiesDisabled() {
        when(userServiceBean.isAdmin("F001:admin")).thenReturn(true);
        when(adminConfigStore.getSnapshot()).thenReturn(snapshot(false, true));

        Response response = resource.getQueue(request, null, null);

        assertThat(response.getStatus()).isEqualTo(200);
        Map<String, Object> body = castBody(response);
        assertThat(body.get("source")).isEqualTo("live");
        assertThat(body.get("retrySupported")).isEqualTo(false);
        assertThat(body.get("discardSupported")).isEqualTo(false);
        assertThat(body.get("adminOnly")).isEqualTo(true);
        assertThat(body.get("retryRequested")).isEqualTo(false);
    }

    @Test
    void mockModeListReturnsCapabilitiesEnabled() {
        System.setProperty(ALLOW_MOCK_ENV, "true");
        when(userServiceBean.isAdmin("F001:admin")).thenReturn(true);
        when(adminConfigStore.getSnapshot()).thenReturn(snapshot(true, true));
        when(queueStore.snapshot()).thenReturn(List.of(
                new OrcaQueueStore.QueueEntry("P001", "pending", true, "2026-03-07T00:00:00Z", null)
        ));

        Response response = resource.getQueue(request, null, null);

        assertThat(response.getStatus()).isEqualTo(200);
        Map<String, Object> body = castBody(response);
        assertThat(body.get("source")).isEqualTo("mock");
        assertThat(body.get("retrySupported")).isEqualTo(true);
        assertThat(body.get("discardSupported")).isEqualTo(true);
        assertThat(body.get("adminOnly")).isEqualTo(true);
        assertThat((List<?>) body.get("queue")).hasSize(1);
    }

    @Test
    void liveModeRetryReturnsNotImplemented() {
        when(userServiceBean.isAdmin("F001:admin")).thenReturn(true);
        when(adminConfigStore.getSnapshot()).thenReturn(snapshot(false, true));

        Response response = resource.getQueue(request, "P001", "1");

        assertThat(response.getStatus()).isEqualTo(501);
        Map<String, Object> body = castBody(response);
        assertThat(body.get("retryRequested")).isEqualTo(true);
        assertThat(body.get("retryApplied")).isEqualTo(false);
        assertThat(body.get("retryReason")).isEqualTo("not_implemented");
        assertThat(body.get("retrySupported")).isEqualTo(false);
    }

    @Test
    void retryWithoutPatientIdReturnsBadRequest() {
        when(userServiceBean.isAdmin("F001:admin")).thenReturn(true);
        when(adminConfigStore.getSnapshot()).thenReturn(snapshot(false, true));

        Response response = resource.getQueue(request, null, "1");

        assertThat(response.getStatus()).isEqualTo(400);
        Map<String, Object> body = castBody(response);
        assertThat(body.get("retryRequested")).isEqualTo(true);
        assertThat(body.get("retryApplied")).isEqualTo(false);
        assertThat(body.get("retryReason")).isEqualTo("patientId_required");
    }

    @Test
    void nonAdminIsRejected() {
        when(userServiceBean.isAdmin("F001:admin")).thenReturn(false);

        assertThatThrownBy(() -> resource.getQueue(request, null, null))
                .isInstanceOf(WebApplicationException.class)
                .satisfies(ex -> assertThat(((WebApplicationException) ex).getResponse().getStatus()).isEqualTo(403));
        verifyNoInteractions(adminConfigStore, queueStore);
    }

    private static AdminConfigSnapshot snapshot(boolean useMock, boolean verified) {
        AdminConfigSnapshot snapshot = new AdminConfigSnapshot();
        snapshot.setUseMockOrcaQueue(useMock);
        snapshot.setVerified(verified);
        return snapshot;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castBody(Response response) {
        return (Map<String, Object>) response.getEntity();
    }
}
