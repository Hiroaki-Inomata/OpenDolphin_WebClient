package open.dolphin.rest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * CSRF token utility bound to HttpSession.
 */
public final class CsrfTokenSupport {

    public static final String CSRF_HEADER_NAME = "X-CSRF-Token";
    public static final String CSRF_PLACEHOLDER = "__CSRF_TOKEN__";

    private static final String CSRF_SESSION_KEY = CsrfTokenSupport.class.getName() + ".TOKEN";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private CsrfTokenSupport() {
    }

    public static boolean isUnsafeMethod(String method) {
        if (method == null) {
            return false;
        }
        return switch (method.toUpperCase(java.util.Locale.ROOT)) {
            case "POST", "PUT", "PATCH", "DELETE" -> true;
            default -> false;
        };
    }

    public static String getOrCreateToken(HttpServletRequest request) {
        if (request == null) {
            return generateToken();
        }
        HttpSession session = request.getSession(true);
        Object existing = session.getAttribute(CSRF_SESSION_KEY);
        if (existing instanceof String value && !value.isBlank()) {
            return value;
        }
        String generated = generateToken();
        session.setAttribute(CSRF_SESSION_KEY, generated);
        return generated;
    }

    public static String getToken(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        Object value = session.getAttribute(CSRF_SESSION_KEY);
        if (!(value instanceof String token)) {
            return null;
        }
        String normalized = token.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    public static boolean matches(String expectedToken, String providedToken) {
        if (expectedToken == null || expectedToken.isBlank() || providedToken == null || providedToken.isBlank()) {
            return false;
        }
        byte[] expected = expectedToken.trim().getBytes(StandardCharsets.UTF_8);
        byte[] provided = providedToken.trim().getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, provided);
    }

    private static String generateToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
