package open.dolphin.rest;

import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.LinkedHashMap;
import java.util.Map;
import open.dolphin.session.UserServiceBean;
import open.dolphin.touch.JsonTouchSharedService;

@Path("/api/session")
public class SessionAuthResource extends AbstractResource {

    @Inject
    private UserServiceBean userServiceBean;

    @POST
    @Path("/login")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response login(@Context HttpServletRequest request, LoginRequest body) {
        if (body == null) {
            throw restError(request, Response.Status.BAD_REQUEST, "invalid_request", "ログイン情報が必要です。");
        }
        String facilityId = trimToNull(body.facilityId());
        String loginId = trimToNull(body.userId());
        String password = body.password();
        String clientUuid = trimToNull(body.clientUuid());
        if (facilityId == null || loginId == null || password == null || password.isBlank()) {
            throw restError(request, Response.Status.BAD_REQUEST, "invalid_request", "施設ID、ユーザーID、パスワードを指定してください。");
        }
        String actorId = facilityId + ":" + loginId;
        UserServiceBean.AuthenticationResult result =
                userServiceBean.authenticateWithPolicy(actorId, password, resolveClientIp(request));
        if (result.ipThrottled()) {
            Response.ResponseBuilder response = Response.status(429)
                    .entity(buildLoginError("too_many_requests", "ログイン試行が多すぎます。時間をおいて再試行してください。"));
            response.header("Retry-After", Long.toString(Math.max(1L, result.retryAfterSeconds())));
            return AuthSessionSupport.noStore(response).build();
        }
        if (!result.authenticated()) {
            throw restError(request, Response.Status.UNAUTHORIZED, "unauthorized", "認証に失敗しました。");
        }

        JsonTouchSharedService.SafeUserResponse safeUser = loadSafeUser(actorId);
        if (safeUser == null) {
            throw restError(request, Response.Status.UNAUTHORIZED, "unauthorized", "認証ユーザーを取得できませんでした。");
        }

        HttpSession session = AuthSessionSupport.rotateSession(request);
        AuthSessionSupport.populateAuthenticatedSession(session, actorId, facilityId, loginId, clientUuid);

        String runId = normalizeRunIdValue(request);
        AuthSessionSupport.SessionUserResponse payload =
                AuthSessionSupport.toSessionUserResponse(safeUser, clientUuid, runId);
        return AuthSessionSupport.noStore(Response.ok(payload)).build();
    }

    @GET
    @Path("/me")
    @Produces(MediaType.APPLICATION_JSON)
    public Response me(@Context HttpServletRequest request) {
        String actorId = AuthSessionSupport.resolveActorId(request);
        if (actorId == null) {
            throw restError(request, Response.Status.UNAUTHORIZED, "unauthorized", "Authentication required.");
        }
        JsonTouchSharedService.SafeUserResponse safeUser = loadSafeUser(actorId);
        if (safeUser == null) {
            throw restError(request, Response.Status.UNAUTHORIZED, "unauthorized", "Authentication required.");
        }
        String runId = normalizeRunIdValue(request);
        String clientUuid = AuthSessionSupport.resolveClientUuid(request);
        AuthSessionSupport.SessionUserResponse payload =
                AuthSessionSupport.toSessionUserResponse(safeUser, clientUuid, runId);
        return AuthSessionSupport.noStore(Response.ok(payload)).build();
    }

    private JsonTouchSharedService.SafeUserResponse loadSafeUser(String actorId) {
        try {
            return JsonTouchSharedService.toSafeUserResponse(userServiceBean.getUser(actorId));
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String normalizeRunIdValue(HttpServletRequest request) {
        Object explicit = request != null ? request.getAttribute(LogFilter.RUN_ID_ATTRIBUTE) : null;
        if (explicit instanceof String text && !text.isBlank()) {
            return text.trim();
        }
        String headerRunId = request != null ? request.getHeader("X-Run-Id") : null;
        if (headerRunId != null && !headerRunId.isBlank()) {
            return headerRunId.trim();
        }
        return resolveTraceIdValue(request);
    }

    private static Map<String, Object> buildLoginError(String code, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", code);
        body.put("code", code);
        body.put("errorCode", code);
        body.put("message", message);
        body.put("status", 429);
        body.put("errorCategory", "too_many_requests");
        return body;
    }

    public record LoginRequest(String facilityId, String userId, String password, String clientUuid) {
    }
}
