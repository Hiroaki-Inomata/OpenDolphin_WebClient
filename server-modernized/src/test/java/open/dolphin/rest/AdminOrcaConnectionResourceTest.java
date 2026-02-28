package open.dolphin.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.lang.reflect.Field;
import java.util.Map;
import open.dolphin.orca.config.OrcaConnectionConfigRecord;
import open.dolphin.orca.config.OrcaConnectionConfigStore;
import open.dolphin.orca.transport.RestOrcaTransport;
import open.dolphin.security.audit.SessionAuditDispatcher;
import open.dolphin.session.UserServiceBean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AdminOrcaConnectionResourceTest {

    private AdminOrcaConnectionResource resource;
    private HttpServletRequest request;
    private UserServiceBean userServiceBean;
    private OrcaConnectionConfigStore configStore;

    @BeforeEach
    void setUp() throws Exception {
        resource = new AdminOrcaConnectionResource();
        request = mock(HttpServletRequest.class);
        userServiceBean = mock(UserServiceBean.class);
        configStore = mock(OrcaConnectionConfigStore.class);

        setField(resource, "orcaConnectionConfigStore", configStore);
        setField(resource, "restOrcaTransport", mock(RestOrcaTransport.class));
        setField(resource, "userServiceBean", userServiceBean);
        setField(resource, "sessionAuditDispatcher", mock(SessionAuditDispatcher.class));
    }

    @Test
    void getConfigRejectsWhenUnauthenticated() {
        when(request.getRemoteUser()).thenReturn(null);

        try {
            resource.getConfig(request);
            fail("Expected WebApplicationException");
        } catch (WebApplicationException ex) {
            assertEquals(401, ex.getResponse().getStatus());
        }
    }

    @Test
    void getConfigRejectsWhenNotAdmin() {
        when(request.getRemoteUser()).thenReturn("FACILITY:testuser");
        when(userServiceBean.isAdmin("FACILITY:testuser", null)).thenReturn(false);

        try {
            resource.getConfig(request);
            fail("Expected WebApplicationException");
        } catch (WebApplicationException ex) {
            assertEquals(403, ex.getResponse().getStatus());
        }
    }

    @Test
    void getConfigReturnsMaskedConfigForAdmin() {
        when(request.getHeader("X-Run-Id")).thenReturn("RUN-ORCA");
        when(request.getRemoteUser()).thenReturn("FACILITY:admin");
        when(userServiceBean.isAdmin("FACILITY:admin", null)).thenReturn(true);

        OrcaConnectionConfigRecord record = new OrcaConnectionConfigRecord();
        record.setUseWeborca(Boolean.TRUE);
        record.setServerUrl("https://weborca-trial.orca.med.or.jp");
        record.setPort(443);
        record.setUsername("trial");
        record.setPasswordEncrypted("encrypted-password");
        record.setPasswordUpdatedAt("2026-02-11T23:25:24Z");
        when(configStore.getSnapshot("FACILITY")).thenReturn(record);

        Response response = resource.getConfig(request);
        assertEquals(200, response.getStatus());

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getEntity();
        assertNotNull(body);
        assertEquals("RUN-ORCA", body.get("runId"));
        assertEquals("FACILITY", body.get("facilityId"));
        assertEquals(Boolean.TRUE, body.get("ok"));
        assertEquals("https://weborca-trial.orca.med.or.jp", body.get("serverUrl"));
        assertEquals(443, body.get("port"));
        assertEquals("trial", body.get("username"));
        assertEquals(Boolean.TRUE, body.get("passwordConfigured"));
        assertTrue(!body.containsKey("password"));
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }
}
