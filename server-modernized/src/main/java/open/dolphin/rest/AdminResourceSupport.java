package open.dolphin.rest;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import open.dolphin.audit.AuditEventEnvelope;
import open.dolphin.security.audit.AuditEventPayload;
import open.dolphin.security.audit.SessionAuditDispatcher;
import open.dolphin.session.UserServiceBean;

/**
 * Cross-cutting support for admin resource authorization and audit logging.
 */
final class AdminResourceSupport {

    private AdminResourceSupport() {
    }

    static String requireAdminActor(AbstractResource resource,
                                    HttpServletRequest request,
                                    UserServiceBean userServiceBean) {
        String actor = request != null ? request.getRemoteUser() : null;
        if (actor == null || actor.isBlank()) {
            throw resource.restError(request, jakarta.ws.rs.core.Response.Status.UNAUTHORIZED,
                    "unauthorized", "Authentication required");
        }
        if (userServiceBean == null || !userServiceBean.isAdmin(actor)) {
            throw resource.restError(request, jakarta.ws.rs.core.Response.Status.FORBIDDEN,
                    "forbidden", "管理者権限が必要です。");
        }
        return actor;
    }

    static void recordAudit(AbstractResource resource,
                            SessionAuditDispatcher dispatcher,
                            HttpServletRequest request,
                            String action,
                            String actor,
                            String runId,
                            Map<String, Object> details,
                            AuditEventEnvelope.Outcome outcome,
                            String errorCode,
                            String errorMessage,
                            String defaultResource) {
        if (dispatcher == null) {
            return;
        }
        AuditEventPayload payload = new AuditEventPayload();
        payload.setAction(action);
        payload.setResource(request != null ? request.getRequestURI() : defaultResource);
        payload.setActorId(actor != null ? actor : (request != null ? request.getRemoteUser() : null));
        payload.setIpAddress(request != null ? request.getRemoteAddr() : null);
        payload.setUserAgent(request != null ? request.getHeader("User-Agent") : null);

        String traceId = resource.resolveTraceId(request);
        if (traceId != null && !traceId.isBlank()) {
            payload.setTraceId(traceId);
            payload.setRequestId(traceId);
        }

        Map<String, Object> merged = new LinkedHashMap<>();
        if (details != null) {
            merged.putAll(details);
        }
        if (runId != null) {
            merged.put("runId", runId);
        }
        merged.put("timestamp", Instant.now().toString());
        payload.setDetails(merged);

        dispatcher.record(payload, outcome, errorCode, errorMessage);
    }
}
