package open.dolphin.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import open.dolphin.security.audit.SessionAuditDispatcher;
import open.dolphin.session.UserServiceBean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AdminAccessResourceTest {

    private AdminAccessResource resource;
    private HttpServletRequest request;
    private UserServiceBean userServiceBean;

    @BeforeEach
    void setUp() throws Exception {
        resource = new AdminAccessResource();
        request = mock(HttpServletRequest.class);
        userServiceBean = mock(UserServiceBean.class);

        setField(resource, "em", mock(EntityManager.class));
        setField(resource, "userServiceBean", userServiceBean);
        setField(resource, "secondFactorSecurityConfig", mock(open.dolphin.security.SecondFactorSecurityConfig.class));
        setField(resource, "sessionAuditDispatcher", mock(SessionAuditDispatcher.class));
    }

    @Test
    void listUsersRejectsWhenUnauthenticated() {
        when(request.getRemoteUser()).thenReturn(null);
        try {
            resource.listUsers(request);
            fail("Expected WebApplicationException");
        } catch (WebApplicationException ex) {
            assertEquals(401, ex.getResponse().getStatus());
        }
    }

    @Test
    void listUsersRejectsWhenNotAdmin() {
        when(request.getHeader("X-Run-Id")).thenReturn("RUN-TEST");
        when(request.getRemoteUser()).thenReturn("FACILITY:testuser");
        when(userServiceBean.isAdmin("FACILITY:testuser")).thenReturn(false);
        try {
            resource.listUsers(request);
            fail("Expected WebApplicationException");
        } catch (WebApplicationException ex) {
            assertEquals(403, ex.getResponse().getStatus());
        }
    }

    @Test
    void listUsersReturnsEmptyListForAdmin() {
        when(request.getHeader("X-Run-Id")).thenReturn("RUN-TEST");
        when(request.getRemoteUser()).thenReturn("FACILITY:admin");
        when(userServiceBean.isAdmin("FACILITY:admin")).thenReturn(true);
        when(userServiceBean.getAllUser("FACILITY")).thenReturn(List.of());

        Response response = resource.listUsers(request);
        assertEquals(200, response.getStatus());

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getEntity();
        assertNotNull(body);
        assertEquals("RUN-TEST", body.get("runId"));
        assertEquals("FACILITY", body.get("facilityId"));
        @SuppressWarnings("unchecked")
        List<Object> users = (List<Object>) body.get("users");
        assertNotNull(users);
        assertTrue(users.isEmpty());
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }
}

