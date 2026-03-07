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

public class SecurityHeadersFilter implements Filter {

    static final String CONTENT_SECURITY_POLICY =
            "default-src 'self'; base-uri 'self'; object-src 'none'; frame-ancestors 'none'; "
                    + "img-src 'self' data: blob:; font-src 'self' data:; style-src 'self' 'unsafe-inline'; "
                    + "script-src 'self'; connect-src 'self'; worker-src 'self' blob:; "
                    + "form-action 'self'; frame-src 'none'; manifest-src 'self'";
    static final String HSTS_VALUE = "max-age=31536000; includeSubDomains";

    @Override
    public void init(FilterConfig filterConfig) {
        // no-op
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (request instanceof HttpServletRequest httpRequest && response instanceof HttpServletResponse httpResponse) {
            applyHeaders(httpRequest, httpResponse);
        }
        chain.doFilter(request, response);
    }

    static void applyHeaders(HttpServletRequest request, HttpServletResponse response) {
        if (response == null) {
            return;
        }
        response.setHeader("Content-Security-Policy", CONTENT_SECURITY_POLICY);
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", "same-origin");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Permissions-Policy",
                "accelerometer=(), camera=(), geolocation=(), gyroscope=(), magnetometer=(), "
                        + "microphone=(), payment=(), usb=()");
        if (request != null && RequestSecuritySupport.shouldAttachHsts(request)) {
            response.setHeader("Strict-Transport-Security", HSTS_VALUE);
        }
    }

    @Override
    public void destroy() {
        // no-op
    }
}
