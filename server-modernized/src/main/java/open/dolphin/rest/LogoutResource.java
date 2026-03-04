package open.dolphin.rest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;

/**
 * Idempotent logout endpoint for server-side session invalidation.
 */
@Path("/api/logout")
public class LogoutResource extends AbstractResource {

    @POST
    public Response logout(@Context HttpServletRequest request) {
        if (request != null) {
            HttpSession session = request.getSession(false);
            if (session != null) {
                try {
                    session.invalidate();
                } catch (IllegalStateException ignored) {
                    // already invalidated by concurrent logout
                }
            }
        }
        return Response.noContent()
                .cookie(expiredSessionCookie(request))
                .build();
    }

    private static NewCookie expiredSessionCookie(HttpServletRequest request) {
        String path = "/";
        boolean secure = false;
        if (request != null) {
            secure = request.isSecure();
            String contextPath = request.getContextPath();
            if (contextPath != null && !contextPath.isBlank()) {
                path = contextPath;
            }
        }
        return new NewCookie.Builder("JSESSIONID")
                .value("")
                .path(path)
                .maxAge(0)
                .httpOnly(true)
                .secure(secure)
                .build();
    }
}
