package open.dolphin.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import open.dolphin.mbean.UserCache;
import open.dolphin.security.audit.SessionAuditDispatcher;
import open.dolphin.session.UserServiceBean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AdminSecurityResourceTest {

    private AdminSecurityResource resource;
    private UserCache cache;
    private SessionAuditDispatcher dispatcher;
    private UserServiceBean userServiceBean;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() throws Exception {
        resource = new AdminSecurityResource();
        cache = new UserCache();
        dispatcher = mock(SessionAuditDispatcher.class);
        userServiceBean = mock(UserServiceBean.class);
        request = mock(HttpServletRequest.class);
        when(request.getRemoteUser()).thenReturn("F001:admin01");
        when(request.isUserInRole("ADMIN")).thenReturn(true);
        when(request.getRemoteAddr()).thenReturn("192.0.2.1");
        when(request.getHeader("User-Agent")).thenReturn("test-agent");
        when(userServiceBean.isAdmin("F001:admin01")).thenAnswer(invocation -> request.isUserInRole("ADMIN"));

        setField(resource, "userCache", cache);
        setField(resource, "sessionAuditDispatcher", dispatcher);
        setField(resource, "userServiceBean", userServiceBean);
    }

    @Test
    void getCacheHidesPasswords() {
        when(request.isUserInRole("ADMIN")).thenReturn(true);
        cache.cachePassword("doctor01", "secret1");
        cache.cachePassword("nurse02", "secret2");

        Response response = resource.getCache(request);

        assertEquals(200, response.getStatus());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getEntity();
        assertEquals(2, body.get("cachedUsers"));
        @SuppressWarnings("unchecked")
        List<String> masked = (List<String>) body.get("users");
        assertEquals(2, masked.size());
        assertTrue(masked.stream().allMatch(v -> v.contains("***")));
    }

    @Test
    void clearAllRemovesEntriesAndAudits() {
        when(request.isUserInRole("ADMIN")).thenReturn(true);
        cache.cachePassword("doctor01", "secret1");
        cache.cachePassword("nurse02", "secret2");

        Response response = resource.clearCache(request, null);

        assertEquals(200, response.getStatus());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getEntity();
        assertTrue((Boolean) body.get("clearedAll"));
        assertEquals(2, body.get("clearedCount"));
        assertEquals(0, cache.snapshot().size());
        verify(dispatcher, times(1)).record(any(), eq(open.dolphin.audit.AuditEventEnvelope.Outcome.SUCCESS), any(), any());
    }

    @Test
    void evictSingleUser() {
        when(request.isUserInRole("ADMIN")).thenReturn(true);
        cache.cachePassword("doctor01", "secret1");
        cache.cachePassword("nurse02", "secret2");

        Response response = resource.clearCache(request, "doctor01");

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getEntity();
        assertFalse((Boolean) body.get("clearedAll"));
        assertEquals(1, body.get("clearedCount"));
        assertFalse(cache.findPassword("doctor01").isPresent());
        assertTrue(cache.findPassword("nurse02").isPresent());
    }

    @Test
    void getCacheRejectsNonAdmin() {
        when(request.isUserInRole("ADMIN")).thenReturn(false);

        WebApplicationException ex = assertThrows(WebApplicationException.class, () -> resource.getCache(request));

        assertEquals(403, ex.getResponse().getStatus());
    }

    @Test
    void clearCacheRejectsNonAdmin() {
        when(request.isUserInRole("ADMIN")).thenReturn(false);

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> resource.clearCache(request, null));

        assertEquals(403, ex.getResponse().getStatus());
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }
}
