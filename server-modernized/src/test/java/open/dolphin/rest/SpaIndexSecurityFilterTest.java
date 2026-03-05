package open.dolphin.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SpaIndexSecurityFilterTest {

    private SpaIndexSecurityFilter filter;

    @BeforeEach
    void setUp() {
        filter = new SpaIndexSecurityFilter();
    }

    @Test
    void initFailsWhenIndexHtmlHasNoCsrfPlaceholder() {
        FilterConfig filterConfig = mock(FilterConfig.class);
        ServletContext servletContext = mock(ServletContext.class);
        when(filterConfig.getServletContext()).thenReturn(servletContext);
        when(servletContext.getResourceAsStream(SpaIndexSecurityFilter.INDEX_RESOURCE_PATH))
                .thenReturn(new ByteArrayInputStream("<!doctype html><html></html>".getBytes(StandardCharsets.UTF_8)));

        ServletException ex = assertThrows(ServletException.class, () -> filter.init(filterConfig));

        assertThat(ex.getMessage()).contains("missing CSRF placeholder");
    }

    @Test
    void injectsCsrfTokenIntoSpaIndexAndSetsNoStoreHeaders() throws Exception {
        initializeFilterWithTemplate("<!doctype html><html><head><meta name=\"csrf-token\" content=\"__CSRF_TOKEN__\" /></head><body>app</body></html>");

        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        HttpSession session = mock(HttpSession.class);
        ByteArrayOutputStream body = stubResponseBody(response);

        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/openDolphin/f/0001/m/images");
        when(request.getContextPath()).thenReturn("/openDolphin");
        when(request.getHeader("Accept")).thenReturn("text/html,application/xhtml+xml");
        when(request.getSession(true)).thenReturn(session);
        when(session.getAttribute(any())).thenReturn(null);

        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(any(), any());
        verify(response).setHeader("Cache-Control", "private, no-store, max-age=0, must-revalidate");
        verify(response).setHeader("Pragma", "no-cache");
        verify(response).setDateHeader("Expires", 0L);

        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(session).setAttribute(eq(CsrfTokenSupport.class.getName() + ".TOKEN"), tokenCaptor.capture());
        String issuedToken = tokenCaptor.getValue();
        assertThat(issuedToken).isNotBlank();

        String payload = body.toString(StandardCharsets.UTF_8);
        assertThat(payload).contains(issuedToken);
        assertThat(payload).doesNotContain("__CSRF_TOKEN__");
    }

    @Test
    void skipsApiRequests() throws Exception {
        initializeFilterWithTemplate("<!doctype html><html><head><meta name=\"csrf-token\" content=\"__CSRF_TOKEN__\" /></head><body>app</body></html>");

        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/openDolphin/resources/user/F001:user01");
        when(request.getContextPath()).thenReturn("/openDolphin");
        when(request.getHeader("Accept")).thenReturn("application/json");

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).setHeader(eq("Cache-Control"), any());
    }

    private void initializeFilterWithTemplate(String template) throws Exception {
        FilterConfig filterConfig = mock(FilterConfig.class);
        ServletContext servletContext = mock(ServletContext.class);
        when(filterConfig.getServletContext()).thenReturn(servletContext);
        when(servletContext.getResourceAsStream(SpaIndexSecurityFilter.INDEX_RESOURCE_PATH))
                .thenReturn(new ByteArrayInputStream(template.getBytes(StandardCharsets.UTF_8)));
        filter.init(filterConfig);
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
