package open.dolphin.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.WebApplicationException;
import java.lang.reflect.Field;
import java.util.Map;
import open.dolphin.mbean.ServletContextHolder;
import open.dolphin.session.support.ChartEventSessionKeys;
import open.dolphin.testsupport.RuntimeDelegateTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ChartEventResourceTest extends RuntimeDelegateTestSupport {

    private ChartEventResource resource;
    private ServletContextHolder contextHolder;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() throws Exception {
        resource = new ChartEventResource();
        contextHolder = new ServletContextHolder();
        request = mock(HttpServletRequest.class);

        when(request.getRemoteUser()).thenReturn("facility01:user01");
        when(request.getRequestURI()).thenReturn("/resources/chartEvent/subscribe");

        setField(resource, "contextHolder", contextHolder);
        setField(resource, "servletReq", request);
    }

    @Test
    void subscribeReturns400WhenClientUuidMissing() {
        when(request.getHeader(ChartEventSessionKeys.CLIENT_UUID)).thenReturn("   ");

        WebApplicationException ex = assertThrows(WebApplicationException.class, resource::subscribe);

        assertEquals(400, ex.getResponse().getStatus());
        assertErrorCode(ex, "invalid_request");
        verify(request, never()).startAsync();
    }

    @Test
    void subscribeReturns400WhenClientUuidTooLong() {
        when(request.getHeader(ChartEventSessionKeys.CLIENT_UUID)).thenReturn("a".repeat(65));

        WebApplicationException ex = assertThrows(WebApplicationException.class, resource::subscribe);

        assertEquals(400, ex.getResponse().getStatus());
        assertErrorCode(ex, "invalid_request");
        verify(request, never()).startAsync();
    }

    @Test
    void subscribeReturns429WhenGlobalLimitReached() {
        when(request.getHeader(ChartEventSessionKeys.CLIENT_UUID)).thenReturn("client-new");
        for (int i = 0; i < 2000; i++) {
            addSubscriber("facility-" + i, "client-" + i);
        }

        WebApplicationException ex = assertThrows(WebApplicationException.class, resource::subscribe);

        assertEquals(429, ex.getResponse().getStatus());
        assertErrorCode(ex, "too_many_requests");
        verify(request, never()).startAsync();
    }

    @Test
    void subscribeReturns429WhenPerFacilityLimitReached() {
        when(request.getHeader(ChartEventSessionKeys.CLIENT_UUID)).thenReturn("client-new");
        for (int i = 0; i < 200; i++) {
            addSubscriber("facility01", "client-" + i);
        }

        WebApplicationException ex = assertThrows(WebApplicationException.class, resource::subscribe);

        assertEquals(429, ex.getResponse().getStatus());
        assertErrorCode(ex, "too_many_requests");
        verify(request, never()).startAsync();
    }

    @Test
    void subscribeReturns429WhenPerClientLimitReached() {
        when(request.getHeader(ChartEventSessionKeys.CLIENT_UUID)).thenReturn("client-01");
        addSubscriber("facility01", "client-01");
        addSubscriber("facility01", "client-01");
        addSubscriber("facility01", "client-01");

        WebApplicationException ex = assertThrows(WebApplicationException.class, resource::subscribe);

        assertEquals(429, ex.getResponse().getStatus());
        assertErrorCode(ex, "too_many_requests");
        verify(request, never()).startAsync();
    }

    @Test
    void subscribeRemovesAsyncContextOnComplete() throws Exception {
        when(request.getHeader(ChartEventSessionKeys.CLIENT_UUID)).thenReturn("client-01");

        AsyncContext started = mock(AsyncContext.class);
        ServletRequest startedRequest = mock(ServletRequest.class);
        when(request.startAsync()).thenReturn(started);
        when(started.getRequest()).thenReturn(startedRequest);

        resource.subscribe();
        assertEquals(1, contextHolder.getAsyncContextList().size());

        ArgumentCaptor<AsyncListener> listenerCaptor = ArgumentCaptor.forClass(AsyncListener.class);
        verify(started).addListener(listenerCaptor.capture());
        AsyncListener listener = listenerCaptor.getValue();
        listener.onComplete(mock(AsyncEvent.class));

        assertTrue(contextHolder.getAsyncContextList().isEmpty());
    }

    private void addSubscriber(String facilityId, String clientUuid) {
        AsyncContext context = mock(AsyncContext.class);
        ServletRequest subscriberRequest = mock(ServletRequest.class);
        when(context.getRequest()).thenReturn(subscriberRequest);
        when(subscriberRequest.getAttribute(ChartEventSessionKeys.FACILITY_ID)).thenReturn(facilityId);
        when(subscriberRequest.getAttribute(ChartEventSessionKeys.CLIENT_UUID)).thenReturn(clientUuid);
        contextHolder.addAsyncContext(context);
    }

    private static void assertErrorCode(WebApplicationException exception, String expectedCode) {
        Object entity = exception.getResponse().getEntity();
        assertTrue(entity instanceof Map<?, ?>);
        Map<?, ?> body = (Map<?, ?>) entity;
        assertEquals(expectedCode, body.get("errorCode"));
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
