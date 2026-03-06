package open.orca.rest;

import jakarta.servlet.http.HttpServletRequest;
import java.security.Principal;

/**
 * Shared ORCA master authorization helper.
 */
final class OrcaMasterAuthSupport {

    private OrcaMasterAuthSupport() {
    }

    static boolean isAuthorized(HttpServletRequest request) {
        return hasAuthenticatedPrincipal(request);
    }

    private static boolean hasAuthenticatedPrincipal(HttpServletRequest request) {
        if (request == null) {
            return false;
        }
        try {
            String remoteUser = request.getRemoteUser();
            if (remoteUser != null && !remoteUser.isBlank()) {
                return true;
            }
        } catch (IllegalStateException ignored) {
            // fall through and inspect user principal.
        }
        try {
            Principal principal = request.getUserPrincipal();
            return principal != null && principal.getName() != null && !principal.getName().isBlank();
        } catch (IllegalStateException ignored) {
            return false;
        }
    }

}
