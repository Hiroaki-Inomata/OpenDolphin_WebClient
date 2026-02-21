package open.dolphin.touch.support;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import open.dolphin.audit.AuditEventEnvelope;
import open.dolphin.security.audit.AuditDetailSanitizer;
import open.dolphin.security.audit.AuditEventPayload;
import open.dolphin.security.audit.AuditTrailService;
import open.dolphin.security.audit.SessionAuditDispatcher;
import open.dolphin.session.framework.SessionTraceContext;
import open.dolphin.session.framework.SessionTraceManager;

/**
 * Touch API の監査ログ出力を補助する。
 */
@ApplicationScoped
public class TouchAuditHelper {

    private static final String TOKEN_HASH_SECRET_PROP = "touch.audit.token.hash.secret";
    private static final String TOKEN_HASH_SECRET_ENV = "TOUCH_AUDIT_TOKEN_HASH_SECRET";
    private static final String TOKEN_HASH_ALG_HMAC = "HMAC-SHA-256";
    private static final String TOKEN_HASH_ALG_SHA = "SHA-256";

    @Inject
    AuditTrailService auditTrailService;

    @Inject
    SessionAuditDispatcher sessionAuditDispatcher;

    @Inject
    SessionTraceManager sessionTraceManager;

    public Optional<AuditEventEnvelope> record(TouchRequestContext context,
                                               String action,
                                               String resource,
                                               Map<String, Object> additionalDetails) {
        return record(context, action, resource, "success", null, additionalDetails);
    }

    public Optional<AuditEventEnvelope> recordSuccess(TouchRequestContext context,
                                                      String action,
                                                      String resource,
                                                      Map<String, Object> additionalDetails) {
        return record(context, action, resource, "success", null, additionalDetails);
    }

    public Optional<AuditEventEnvelope> recordFailure(TouchRequestContext context,
                                                      String action,
                                                      String resource,
                                                      String reason,
                                                      Map<String, Object> additionalDetails) {
        return record(context, action, resource, "failed", reason, additionalDetails);
    }

    private Optional<AuditEventEnvelope> record(TouchRequestContext context,
                                                String action,
                                                String resource,
                                                String status,
                                                String reason,
                                                Map<String, Object> additionalDetails) {
        if ((sessionAuditDispatcher == null && auditTrailService == null) || context == null) {
            return Optional.empty();
        }
        AuditEventPayload payload = new AuditEventPayload();
        payload.setActorId(context.remoteUser());
        payload.setActorDisplayName(context.userId());
        payload.setActorRole(determineRole());
        payload.setAction(action);
        payload.setResource(resource);
        String requestId = context.requestId();
        String traceId = context.traceId();
        if (requestId == null || requestId.isBlank()) {
            requestId = traceId;
        }
        if (traceId == null || traceId.isBlank()) {
            traceId = requestId;
        }
        payload.setRequestId(requestId);
        payload.setTraceId(traceId);
        payload.setIpAddress(context.clientIp());
        payload.setUserAgent(context.userAgent());
        Map<String, Object> details = mergeDetails(context, status, reason, additionalDetails);
        payload.setPatientId(AuditDetailSanitizer.resolvePatientId(null, details));
        payload.setDetails(details);
        if (sessionAuditDispatcher != null) {
            return Optional.of(sessionAuditDispatcher.record(payload));
        }
        auditTrailService.record(payload);
        return Optional.empty();
    }

    private String determineRole() {
        if (sessionTraceManager == null) {
            return null;
        }
        SessionTraceContext traceContext = sessionTraceManager.current();
        if (traceContext == null) {
            return null;
        }
        return traceContext.getActorRole();
    }

    private Map<String, Object> mergeDetails(TouchRequestContext context,
                                             String status,
                                             String reason,
                                             Map<String, Object> additionalDetails) {
        Map<String, Object> details = new HashMap<>();
        details.put("status", status != null ? status : "success");
        if (context.accessReason() != null) {
            details.put("accessReason", context.accessReason());
        }
        details.put("tokenPresent", context.hasConsentToken());
        if (context.hasConsentToken()) {
            TokenDigest tokenDigest = digestConsentToken(context.consentToken());
            if (tokenDigest != null && tokenDigest.hash() != null) {
                details.put("tokenHash", tokenDigest.hash());
                details.put("tokenHashAlg", tokenDigest.algorithm());
            }
        }
        details.put("facilityId", context.facilityId());
        details.put("userId", context.userId());
        if (reason != null && !reason.isBlank()) {
            details.putIfAbsent("reason", reason);
            details.putIfAbsent("errorCode", reason);
        }
        if (additionalDetails != null && !additionalDetails.isEmpty()) {
            details.putAll(additionalDetails);
        }
        boolean traceCaptured = false;
        if (sessionTraceManager != null) {
            SessionTraceContext traceContext = sessionTraceManager.current();
            if (traceContext != null) {
                details.put("traceId", traceContext.getTraceId());
                details.put("sessionOperation", traceContext.getOperation());
                traceCaptured = true;
            }
        }
        if (!traceCaptured) {
            String traceId = context.traceId();
            if (traceId != null && !traceId.isBlank()) {
                details.put("traceId", traceId);
            }
        }
        return details;
    }

    private TokenDigest digestConsentToken(String consentToken) {
        if (consentToken == null || consentToken.isBlank()) {
            return null;
        }
        String secret = resolveTokenHashSecret();
        if (secret != null) {
            try {
                Mac mac = Mac.getInstance("HmacSHA256");
                mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
                byte[] digest = mac.doFinal(consentToken.getBytes(StandardCharsets.UTF_8));
                return new TokenDigest(HexFormat.of().formatHex(digest), TOKEN_HASH_ALG_HMAC);
            } catch (Exception ignored) {
                // Fall through to SHA-256 if HMAC generation fails.
            }
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(consentToken.getBytes(StandardCharsets.UTF_8));
            return new TokenDigest(HexFormat.of().formatHex(hashed), TOKEN_HASH_ALG_SHA);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Missing SHA-256 implementation", e);
        }
    }

    private String resolveTokenHashSecret() {
        String fromProperty = System.getProperty(TOKEN_HASH_SECRET_PROP);
        if (fromProperty != null && !fromProperty.isBlank()) {
            return fromProperty.trim();
        }
        String fromEnv = System.getenv(TOKEN_HASH_SECRET_ENV);
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv.trim();
        }
        return null;
    }

    private record TokenDigest(String hash, String algorithm) {
    }
}
