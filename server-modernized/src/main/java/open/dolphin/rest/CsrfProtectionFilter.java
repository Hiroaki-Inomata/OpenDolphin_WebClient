package open.dolphin.rest;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Unified CSRF verification for all unsafe methods.
 */
public class CsrfProtectionFilter implements Filter {

    private static final String FORBIDDEN_MESSAGE = "CSRF validation failed";

    @Override
    public void init(FilterConfig filterConfig) {
        // no-op
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (!(request instanceof HttpServletRequest httpRequest) || !(response instanceof HttpServletResponse httpResponse)) {
            chain.doFilter(request, response);
            return;
        }

        if (!CsrfTokenSupport.isUnsafeMethod(httpRequest.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        String expectedToken = CsrfTokenSupport.getToken(httpRequest);
        String providedToken = httpRequest.getHeader(CsrfTokenSupport.CSRF_HEADER_NAME);
        if (!CsrfTokenSupport.matches(expectedToken, providedToken)) {
            request.setAttribute(AbstractResource.ERROR_CODE_ATTRIBUTE, "csrf_validation_failed");
            request.setAttribute(AbstractResource.ERROR_MESSAGE_ATTRIBUTE, FORBIDDEN_MESSAGE);
            request.setAttribute(AbstractResource.ERROR_STATUS_ATTRIBUTE, HttpServletResponse.SC_FORBIDDEN);
            Map<String, Object> details = new HashMap<>();
            details.put("reason", "csrf_validation_failed");
            details.put("status", "failed");
            request.setAttribute(AbstractResource.ERROR_DETAILS_ATTRIBUTE, details);
            writeForbidden(httpRequest, httpResponse, details);
            return;
        }

        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
        // no-op
    }

    private void writeForbidden(HttpServletRequest request, HttpServletResponse response, Map<String, Object> details)
            throws IOException {
        if (response.isCommitted()) {
            return;
        }
        AbstractResource.writeRestError(request, response, HttpServletResponse.SC_FORBIDDEN,
                "csrf_validation_failed", FORBIDDEN_MESSAGE, details);
    }
}
