package open.dolphin.adm20.rest.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PhrRequestContextExtractorTest {

    @Mock
    private HttpServletRequest request;

    @Test
    void from_ignoresSpoofedForwardedForWhenRemoteIsUntrusted() {
        when(request.getRemoteUser()).thenReturn("F001:user01");
        when(request.getRemoteAddr()).thenReturn("203.0.113.10");
        when(request.getHeader(anyString())).thenReturn(null);
        when(request.getHeader("X-Forwarded-For")).thenReturn("198.51.100.10");
        when(request.getRequestURI()).thenReturn("/resources/20/adm/phr");

        PhrRequestContext context = PhrRequestContextExtractor.from(request);

        assertThat(context.clientIp()).isEqualTo("203.0.113.10");
    }

    @Test
    void from_usesForwardedChainWhenRemoteIsTrustedProxy() {
        when(request.getRemoteUser()).thenReturn("F001:user01");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(request.getHeader(anyString())).thenReturn(null);
        when(request.getHeader("X-Forwarded-For")).thenReturn("198.51.100.10, 127.0.0.1");
        when(request.getHeader("X-Request-Id")).thenReturn("req-1");
        when(request.getHeader("X-Trace-Id")).thenReturn("trace-1");
        when(request.getRequestURI()).thenReturn("/resources/20/adm/phr");

        PhrRequestContext context = PhrRequestContextExtractor.from(request);

        assertThat(context.clientIp()).isEqualTo("198.51.100.10");
        assertThat(context.requestId()).isEqualTo("req-1");
        assertThat(context.traceId()).isEqualTo("trace-1");
    }
}
