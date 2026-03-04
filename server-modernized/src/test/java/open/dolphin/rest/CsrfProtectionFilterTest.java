package open.dolphin.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CsrfProtectionFilterTest {

    private CsrfProtectionFilter filter;

    @BeforeEach
    void setUp() {
        filter = new CsrfProtectionFilter();
    }

    @Test
    void safeMethodDoesNotRequireToken() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(request.getMethod()).thenReturn("GET");

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).setStatus(HttpServletResponse.SC_FORBIDDEN);
    }

    @Test
    void unsafeMethodWithMatchingTokenPasses() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        HttpSession session = mock(HttpSession.class);
        when(request.getMethod()).thenReturn("POST");
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute(any())).thenReturn("csrf-token-1");
        when(request.getHeader(CsrfTokenSupport.CSRF_HEADER_NAME)).thenReturn("csrf-token-1");

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).setStatus(HttpServletResponse.SC_FORBIDDEN);
    }

    @Test
    void unsafeMethodWithoutSessionTokenIsRejectedWith403() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(request.getMethod()).thenReturn("DELETE");
        when(request.getSession(false)).thenReturn(null);
        when(request.getHeader(CsrfTokenSupport.CSRF_HEADER_NAME)).thenReturn(null);
        ByteArrayOutputStream body = stubResponseBody(response);
        when(response.isCommitted()).thenReturn(false);

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
        verify(request, atLeastOnce()).setAttribute(AbstractResource.ERROR_CODE_ATTRIBUTE, "csrf_validation_failed");
        assertThat(body.toString(StandardCharsets.UTF_8)).contains("CSRF validation failed");
    }

    @Test
    void unsafeMethodWithMismatchedTokenIsRejectedWith403() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        HttpSession session = mock(HttpSession.class);
        when(request.getMethod()).thenReturn("PATCH");
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute(any())).thenReturn("csrf-token-1");
        when(request.getHeader(CsrfTokenSupport.CSRF_HEADER_NAME)).thenReturn("csrf-token-2");
        stubResponseBody(response);
        when(response.isCommitted()).thenReturn(false);

        Map<String, Object> attrs = new HashMap<>();
        doAnswer(invocation -> {
            attrs.put(invocation.getArgument(0, String.class), invocation.getArgument(1));
            return null;
        }).when(request).setAttribute(any(String.class), any());

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
        assertThat(attrs.get(AbstractResource.ERROR_CODE_ATTRIBUTE)).isEqualTo("csrf_validation_failed");
        assertThat(attrs.get(AbstractResource.ERROR_MESSAGE_ATTRIBUTE)).isEqualTo("CSRF validation failed");
        assertThat(attrs.get(AbstractResource.ERROR_STATUS_ATTRIBUTE)).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
        assertThat(attrs.get(AbstractResource.ERROR_DETAILS_ATTRIBUTE)).isEqualTo(Map.of("reason", "csrf_validation_failed", "status", "failed"));
    }

    @Test
    void xhrUploadRouteRequiresCsrfToken() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/openDolphin/resources/patients/00001/images");
        when(request.getSession(false)).thenReturn(null);
        when(request.getHeader(CsrfTokenSupport.CSRF_HEADER_NAME)).thenReturn(null);
        stubResponseBody(response);
        when(response.isCommitted()).thenReturn(false);

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
    }

    @Test
    void xhrUploadRoutePassesWithCsrfToken() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        HttpSession session = mock(HttpSession.class);

        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/openDolphin/resources/patients/00001/images");
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute(any())).thenReturn("csrf-token-upload");
        when(request.getHeader(CsrfTokenSupport.CSRF_HEADER_NAME)).thenReturn("csrf-token-upload");

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).setStatus(HttpServletResponse.SC_FORBIDDEN);
    }

    @Test
    void logoutRouteRequiresCsrfToken() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/openDolphin/resources/api/logout");
        when(request.getSession(false)).thenReturn(null);
        when(request.getHeader(CsrfTokenSupport.CSRF_HEADER_NAME)).thenReturn(null);
        stubResponseBody(response);
        when(response.isCommitted()).thenReturn(false);

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
    }

    @Test
    void logoutRoutePassesWithCsrfToken() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        HttpSession session = mock(HttpSession.class);

        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/openDolphin/resources/api/logout");
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute(any())).thenReturn("csrf-token-logout");
        when(request.getHeader(CsrfTokenSupport.CSRF_HEADER_NAME)).thenReturn("csrf-token-logout");

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).setStatus(HttpServletResponse.SC_FORBIDDEN);
    }

    private static ByteArrayOutputStream stubResponseBody(HttpServletResponse response) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        when(response.getOutputStream()).thenReturn(new ServletOutputStream() {
            @Override
            public void write(int b) {
                out.write(b);
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setWriteListener(WriteListener writeListener) {
                // no-op
            }
        });
        return out;
    }
}
