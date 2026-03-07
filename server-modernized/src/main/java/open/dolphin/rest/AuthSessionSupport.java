package open.dolphin.rest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.ws.rs.core.Response;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import open.dolphin.touch.JsonTouchSharedService;

final class AuthSessionSupport {

    static final String AUTH_ACTOR_ID = AuthSessionSupport.class.getName() + ".AUTH_ACTOR_ID";
    static final String AUTH_FACILITY_ID = AuthSessionSupport.class.getName() + ".AUTH_FACILITY_ID";
    static final String AUTH_LOGIN_ID = AuthSessionSupport.class.getName() + ".AUTH_LOGIN_ID";
    static final String AUTH_CLIENT_UUID = AuthSessionSupport.class.getName() + ".AUTH_CLIENT_UUID";
    static final String AUTH_AUTHENTICATED_AT = AuthSessionSupport.class.getName() + ".AUTH_AUTHENTICATED_AT";

    private AuthSessionSupport() {
    }

    static HttpSession rotateSession(HttpServletRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }
        try {
            request.changeSessionId();
            return request.getSession(true);
        } catch (IllegalStateException ex) {
            HttpSession current = request.getSession(false);
            Map<String, Object> snapshot = snapshotAttributes(current);
            if (current != null) {
                try {
                    current.invalidate();
                } catch (IllegalStateException ignored) {
                    // already invalidated
                }
            }
            HttpSession next = request.getSession(true);
            snapshot.forEach(next::setAttribute);
            return next;
        }
    }

    static void populateAuthenticatedSession(HttpSession session,
            String actorId,
            String facilityId,
            String loginId,
            String clientUuid) {
        if (session == null) {
            throw new IllegalArgumentException("session is required");
        }
        session.setAttribute(AUTH_ACTOR_ID, actorId);
        session.setAttribute(AUTH_FACILITY_ID, facilityId);
        session.setAttribute(AUTH_LOGIN_ID, loginId);
        if (clientUuid != null && !clientUuid.isBlank()) {
            session.setAttribute(AUTH_CLIENT_UUID, clientUuid.trim());
        } else {
            session.removeAttribute(AUTH_CLIENT_UUID);
        }
        session.setAttribute(AUTH_AUTHENTICATED_AT, Instant.now().toString());
    }

    static void clearAuthenticatedSession(HttpSession session) {
        if (session == null) {
            return;
        }
        session.removeAttribute(AUTH_ACTOR_ID);
        session.removeAttribute(AUTH_FACILITY_ID);
        session.removeAttribute(AUTH_LOGIN_ID);
        session.removeAttribute(AUTH_CLIENT_UUID);
        session.removeAttribute(AUTH_AUTHENTICATED_AT);
    }

    static void clearSession(HttpSession session) {
        clearAuthenticatedSession(session);
    }

    static String resolveActorId(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        HttpSession session = request.getSession(false);
        return resolveActorId(session);
    }

    static String resolveActorId(HttpSession session) {
        if (session == null) {
            return null;
        }
        Object actor = session.getAttribute(AUTH_ACTOR_ID);
        return actor instanceof String text && !text.isBlank() ? text.trim() : null;
    }

    static String resolveClientUuid(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        Object clientUuid = session.getAttribute(AUTH_CLIENT_UUID);
        return clientUuid instanceof String text && !text.isBlank() ? text.trim() : null;
    }

    static SessionUserResponse toSessionUserResponse(JsonTouchSharedService.SafeUserResponse safeUser,
            String clientUuid,
            String runId) {
        if (safeUser == null) {
            return null;
        }
        String facilityId = safeUser.facility() != null ? safeUser.facility().facilityId() : null;
        String displayName = firstNonBlank(safeUser.commonName(), safeUser.givenName(), safeUser.sirName());
        java.util.List<String> roles = safeUser.roles() == null
                ? java.util.List.of()
                : safeUser.roles().stream()
                .map(role -> role != null ? role.role() : null)
                .filter(value -> value != null && !value.isBlank())
                .toList();
        return new SessionUserResponse(
                facilityId,
                safeUser.userId(),
                safeUser.id(),
                displayName,
                safeUser.commonName(),
                roles,
                clientUuid,
                runId
        );
    }

    static Response.ResponseBuilder noStore(Response.ResponseBuilder builder) {
        return builder
                .header("Cache-Control", "private, no-store, max-age=0, must-revalidate")
                .header("Pragma", "no-cache")
                .header("Expires", "0");
    }

    static void applyNoStore(HttpServletResponse response) {
        if (response == null) {
            return;
        }
        response.setHeader("Cache-Control", "private, no-store, max-age=0, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Expires", "0");
    }

    private static Map<String, Object> snapshotAttributes(HttpSession session) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        if (session == null) {
            return snapshot;
        }
        java.util.Enumeration<String> names = session.getAttributeNames();
        while (names != null && names.hasMoreElements()) {
            String name = names.nextElement();
            snapshot.put(name, session.getAttribute(name));
        }
        return snapshot;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    record SessionUserResponse(
            String facilityId,
            String userId,
            long userPk,
            String displayName,
            String commonName,
            java.util.List<String> roles,
            String clientUuid,
            String runId) {
    }
}
