package open.dolphin.adm20.rest.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import open.dolphin.rest.LogFilter;
import org.junit.jupiter.api.Test;

class PhrRequestContextExtractorTest {

    @Test
    void fromUsesTraceAttributeAndDoesNotBlindlyTrustForwardedFor() {
        String previousTrustedProxies = System.getProperty("audit.trusted.proxies");
        System.setProperty("audit.trusted.proxies", "127.0.0.1/32");
        try {
            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getRemoteUser()).thenReturn("facility01:user01");
            when(request.getRemoteAddr()).thenReturn("203.0.113.10");
            when(request.getHeader("X-Forwarded-For")).thenReturn("198.51.100.25, 10.0.0.1");
            when(request.getAttribute(LogFilter.TRACE_ID_ATTRIBUTE)).thenReturn("trace-123");
            when(request.getHeader("X-Trace-Id")).thenReturn("trace-from-header");
            when(request.getHeader("User-Agent")).thenReturn("JUnit");
            when(request.getRequestURI()).thenReturn("/resources/20/adm/phr/export");
            when(request.getHeader("X-Request-Id")).thenReturn("req-123");

            PhrRequestContext context = PhrRequestContextExtractor.from(request);

            assertEquals("facility01:user01", context.remoteUser());
            assertEquals("facility01", context.facilityId());
            assertEquals("user01", context.userId());
            assertEquals("trace-123", context.traceId());
            assertEquals("203.0.113.10", context.clientIp());
            assertEquals("req-123", context.requestId());
        } finally {
            if (previousTrustedProxies == null) {
                System.clearProperty("audit.trusted.proxies");
            } else {
                System.setProperty("audit.trusted.proxies", previousTrustedProxies);
            }
        }
    }
}
