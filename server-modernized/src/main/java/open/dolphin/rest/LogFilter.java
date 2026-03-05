package open.dolphin.rest;

import java.io.IOException;
import java.security.Principal;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import jakarta.inject.Inject;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.security.enterprise.SecurityContext;
import open.dolphin.audit.AuditEventEnvelope;
import open.dolphin.infomodel.IInfoModel;
import open.dolphin.security.audit.AuditDetailSanitizer;
import open.dolphin.security.audit.AuditEventPayload;
import open.dolphin.security.audit.SessionAuditDispatcher;
import open.dolphin.session.UserServiceBean;
import open.dolphin.session.framework.SessionTraceAttributes;
import open.dolphin.rest.orca.AbstractOrcaRestResource;
import org.jboss.logmanager.MDC;

/**
 *
 * @author Kazushi Minagawa, Digital Globe, Inc.
 */
@WebFilter(urlPatterns = {"/resources/*", "/orca/*"}, asyncSupported = true)
public class LogFilter implements Filter {

    private static final Logger SECURITY_LOGGER = Logger.getLogger(LogFilter.class.getName());
    private static final String UNAUTHORIZED_USER = "Unauthorized user: ";
    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String RUN_ID_HEADER = "X-Run-Id";
    public static final String TRACE_ID_ATTRIBUTE = LogFilter.class.getName() + ".TRACE_ID";
    public static final String REQUEST_ID_ATTRIBUTE = LogFilter.class.getName() + ".REQUEST_ID";
    public static final String RUN_ID_ATTRIBUTE = LogFilter.class.getName() + ".RUN_ID";
    private static final String MDC_TRACE_ID_KEY = "traceId";
    private static final String MDC_REQUEST_ID_KEY = "requestId";
    private static final String MDC_RUN_ID_KEY = "runId";
    private static final String ANONYMOUS_PRINCIPAL = "anonymous";
    private static final String AUTH_CHALLENGE = "Basic realm=\"OpenDolphin\"";
    private static final String ERROR_AUDIT_RECORDED_ATTR = LogFilter.class.getName() + ".ERROR_AUDIT_RECORDED";
    private static final String IP_THROTTLED_RETRY_AFTER_ATTR = LogFilter.class.getName() + ".IP_THROTTLED_RETRY_AFTER";
    private static final String PRINCIPAL_FACILITY_DETAILS_KEY = "facilityId";
    private static final Pattern SAFE_TOKEN = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");
    private static final int HTTP_TOO_MANY_REQUESTS = 429;

    @Inject
    private SecurityContext securityContext;

    @Inject
    private SessionAuditDispatcher sessionAuditDispatcher;

    @Inject
    private UserServiceBean userService;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // No runtime toggles; only principal and Basic authentication are supported.
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest)request;
        HttpServletResponse res = (HttpServletResponse) response;
        long startedNanos = System.nanoTime();

        String traceId = resolveTraceId(req);
        String requestId = resolveRequestId(req, traceId);
        String runId = resolveRunId(req);
        req.setAttribute(TRACE_ID_ATTRIBUTE, traceId);
        req.setAttribute(REQUEST_ID_ATTRIBUTE, requestId);
        if (runId != null && !runId.isBlank()) {
            req.setAttribute(RUN_ID_ATTRIBUTE, runId);
        }
        res.setHeader(TRACE_ID_HEADER, traceId);
        res.setHeader(REQUEST_ID_HEADER, requestId);
        if (runId != null && !runId.isBlank()) {
            res.setHeader(RUN_ID_HEADER, runId);
        }
        MdcSnapshot traceIdSnapshot = applyMdcValue(MDC_TRACE_ID_KEY, traceId);
        MdcSnapshot requestIdSnapshot = applyMdcValue(MDC_REQUEST_ID_KEY, requestId);
        MdcSnapshot runIdSnapshot = applyMdcValue(MDC_RUN_ID_KEY, runId);
        MdcSnapshot remoteUserSnapshot = null;
        BlockWrapper wrapper = null;

        try {
            Optional<String> principalUser = resolvePrincipalUser();
            if (principalUser.isEmpty()) {
                principalUser = authenticateWithBasicHeader(req);
            }
            if (principalUser.isEmpty()) {
                Long retryAfter = readRetryAfter(req);
                if (retryAfter != null && retryAfter > 0L) {
                    String candidateUser = extractBasicAuthUserCandidate(req);
                    logUnauthorized(req, candidateUser, traceId);
                    recordUnauthorizedAudit(req, traceId, candidateUser, "too_many_requests",
                            "Too many failed authentication attempts", "ip_throttled",
                            HTTP_TOO_MANY_REQUESTS);
                    sendTooManyRequests(req, res, retryAfter);
                    return;
                }
                String candidateUser = extractBasicAuthUserCandidate(req);
                logUnauthorized(req, candidateUser, traceId);
                recordUnauthorizedAudit(req, traceId, candidateUser, "unauthorized",
                        "Authentication required", "authentication_failed", HttpServletResponse.SC_UNAUTHORIZED);
                sendUnauthorized(req, res, "unauthorized", "Authentication required",
                        unauthorizedDetails("authentication_failed"));
                return;
            }

            String resolvedUser = normalize(principalUser.orElse(null));
            if (!isCompositePrincipal(resolvedUser)) {
                String candidateUser = resolvedUser;
                logUnauthorized(req, candidateUser, traceId);
                recordUnauthorizedAudit(req, traceId, candidateUser, "unauthorized",
                        "Authenticated principal must be composite",
                        "principal_not_composite", HttpServletResponse.SC_UNAUTHORIZED);
                sendUnauthorized(req, res, "unauthorized", "Authenticated principal must be composite",
                        unauthorizedDetails("principal_not_composite"));
                return;
            }

            wrapper = new BlockWrapper(req);
            wrapper.setRemoteUser(resolvedUser);
            wrapper.setHeader(TRACE_ID_HEADER, traceId);
            wrapper.setHeader(REQUEST_ID_HEADER, requestId);
            wrapper.setHeader(RUN_ID_HEADER, runId);
            remoteUserSnapshot = applyMdcValue(SessionTraceAttributes.ACTOR_ID_MDC_KEY, resolvedUser);

            StringBuilder sb = new StringBuilder();
            sb.append(wrapper.getRemoteAddr()).append(" ");
            sb.append(wrapper.getShortUser()).append(" ");
            sb.append(wrapper.getMethod()).append(" ");
//minagawa^ VisitTouch logを分ける        
            String uri = wrapper.getRequestURIForLog();
            sb.append(uri);
            sb.append(" traceId=").append(traceId);
            sb.append(" requestId=").append(requestId);
            if (runId != null && !runId.isBlank()) {
                sb.append(" runId=").append(runId);
            }
            if (uri.startsWith("/jtouch")) {
                Logger.getLogger("visit.touch").info(sb.toString());
            } else {
                Logger.getLogger("open.dolphin").info(sb.toString());
            }
//minagawa 

            chain.doFilter(wrapper, response);
            maybeLogFailedResponse(res, wrapper, traceId, requestId, runId, startedNanos);
            maybeRecordErrorAudit(wrapper, res, null);
        } catch (IOException | ServletException ex) {
            maybeRecordErrorAudit(wrapper != null ? wrapper : req, res, ex);
            throw ex;
        } catch (RuntimeException ex) {
            maybeRecordErrorAudit(wrapper != null ? wrapper : req, res, ex);
            throw ex;
        } finally {
            restoreMdcValue(traceIdSnapshot);
            restoreMdcValue(requestIdSnapshot);
            restoreMdcValue(runIdSnapshot);
            restoreMdcValue(remoteUserSnapshot);
        }
    }

    @Override
    public void destroy() {
    }

    private Optional<String> resolvePrincipalUser() {
        if (securityContext == null) {
            return Optional.empty();
        }
        try {
            Principal principal = securityContext.getCallerPrincipal();
            if (principal == null) {
                return Optional.empty();
            }
            String name = principal.getName();
            if (name == null || name.isBlank() || isAnonymousPrincipal(name)) {
                return Optional.empty();
            }
            return Optional.of(name);
        } catch (IllegalStateException ex) {
            SECURITY_LOGGER.log(Level.FINE, "SecurityContext unavailable; request will be rejected.", ex);
            return Optional.empty();
        }
    }

    private String resolveTraceId(HttpServletRequest req) {
        String traceHeader = safeHeader(req, TRACE_ID_HEADER);
        String normalizedTrace = normalizeToken(traceHeader);
        if (normalizedTrace != null) {
            return normalizedTrace;
        }
        if (normalize(traceHeader) != null) {
            return UUID.randomUUID().toString();
        }

        String requestHeader = safeHeader(req, REQUEST_ID_HEADER);
        String normalizedRequest = normalizeToken(requestHeader);
        if (normalizedRequest != null) {
            return normalizedRequest;
        }
        if (normalize(requestHeader) != null) {
            return UUID.randomUUID().toString();
        }
        return UUID.randomUUID().toString();
    }

    private String resolveRequestId(HttpServletRequest req, String traceId) {
        String requestHeader = safeHeader(req, REQUEST_ID_HEADER);
        String normalizedRequest = normalizeToken(requestHeader);
        if (normalizedRequest != null) {
            return normalizedRequest;
        }
        if (normalize(requestHeader) != null) {
            return UUID.randomUUID().toString();
        }

        String normalizedTrace = normalizeToken(traceId);
        return normalizedTrace != null ? normalizedTrace : UUID.randomUUID().toString();
    }

    private String resolveRunId(HttpServletRequest req) {
        if (!isOrcaRequest(req)) {
            return null;
        }
        String normalizedRunId = normalizeToken(AbstractOrcaRestResource.resolveRunIdValue(req));
        if (normalizedRunId != null) {
            return normalizedRunId;
        }
        String generatedRunId = normalizeToken(AbstractOrcaRestResource.resolveRunIdValue((String) null));
        return generatedRunId != null ? generatedRunId : UUID.randomUUID().toString();
    }

    private String normalizeToken(String candidate) {
        String normalized = normalize(candidate);
        if (normalized == null) {
            return null;
        }
        return SAFE_TOKEN.matcher(normalized).matches() ? normalized : null;
    }

    private boolean isOrcaRequest(HttpServletRequest request) {
        if (request == null) {
            return false;
        }
        String uri = request.getRequestURI();
        if (uri == null || uri.isBlank()) {
            return false;
        }
        return uri.contains("/orca/") || uri.endsWith("/orca");
    }

    private void maybeLogFailedResponse(HttpServletResponse response, BlockWrapper request, String traceId,
            String requestId, String runId, long startedNanos) {
        if (response == null || request == null) {
            return;
        }
        int status = response.getStatus();
        if (status < 400) {
            return;
        }
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(Math.max(0L, System.nanoTime() - startedNanos));
        String uri = request.getRequestURIForLog();
        Logger logger = uri != null && uri.startsWith("/jtouch") ? Logger.getLogger("visit.touch") : Logger.getLogger("open.dolphin");
        logger.warning(() -> String.format("REST %s %s status=%d elapsedMs=%d traceId=%s requestId=%s runId=%s",
                request.getMethod(), uri, status, elapsedMs,
                safe(traceId), safe(requestId), safe(runId)));
    }

    private static String safe(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return value.trim();
    }

    private MdcSnapshot applyMdcValue(String key, String value) {
        Object previousJboss = MDC.get(key);
        String previousSlf4j = org.slf4j.MDC.get(key);
        if (value == null || value.isBlank()) {
            MDC.remove(key);
            org.slf4j.MDC.remove(key);
        } else {
            MDC.put(key, value);
            org.slf4j.MDC.put(key, value);
        }
        return new MdcSnapshot(key, previousJboss, previousSlf4j);
    }

    private void restoreMdcValue(MdcSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        if (snapshot.previousJboss == null) {
            MDC.remove(snapshot.key);
        } else {
            MDC.put(snapshot.key, snapshot.previousJboss.toString());
        }
        if (snapshot.previousSlf4j == null) {
            org.slf4j.MDC.remove(snapshot.key);
        } else {
            org.slf4j.MDC.put(snapshot.key, snapshot.previousSlf4j);
        }
    }

    private static final class MdcSnapshot {
        private final String key;
        private final Object previousJboss;
        private final String previousSlf4j;

        private MdcSnapshot(String key, Object previousJboss, String previousSlf4j) {
            this.key = key;
            this.previousJboss = previousJboss;
            this.previousSlf4j = previousSlf4j;
        }
    }

    private static final class BasicCredentials {
        private final String user;
        private final String password;

        private BasicCredentials(String user, String password) {
            this.user = user;
            this.password = password;
        }

        private String user() {
            return user;
        }

        private String password() {
            return password;
        }
    }

    private void logUnauthorized(HttpServletRequest req, String user, String traceId) {
        StringBuilder sbd = new StringBuilder(UNAUTHORIZED_USER);
        sbd.append(user != null ? user : "unknown");
        sbd.append(": ").append(req.getRequestURI());
        if (traceId != null && !traceId.isBlank()) {
            sbd.append(" traceId=").append(traceId);
        }
        Logger.getLogger("open.dolphin").warning(sbd.toString());
    }

    private Optional<String> authenticateWithBasicHeader(HttpServletRequest request) {
        String auth = safeHeader(request, "Authorization");
        if (auth == null) {
            return Optional.empty();
        }
        String trimmed = auth.trim();
        if (!trimmed.regionMatches(true, 0, "Basic ", 0, 6)) {
            return Optional.empty();
        }
        String encoded = trimmed.substring(6).trim();
        String decoded;
        try {
            decoded = new String(Base64.getDecoder().decode(encoded), java.nio.charset.StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            SECURITY_LOGGER.log(Level.FINE, "Invalid Basic auth header", ex);
            return Optional.empty();
        }
        int sep = decoded.lastIndexOf(':');
        if (sep <= 0 || sep >= decoded.length() - 1) {
            SECURITY_LOGGER.fine("Basic auth header missing separator");
            return Optional.empty();
        }
        String compositeUser = credentials.user();
        String rawPass = credentials.password();
        if (compositeUser == null) {
            return Optional.empty();
        }
        String clientIp = AbstractResource.resolveClientIp(request);
        UserServiceBean.AuthenticationResult result = userService.authenticateWithPolicy(compositeUser, rawPass, clientIp);
        if (result.ipThrottled()) {
            request.setAttribute(IP_THROTTLED_RETRY_AFTER_ATTR, result.retryAfterSeconds());
            return Optional.empty();
        }
        if (result.authenticated()) {
            return Optional.of(compositeUser);
        }
        SECURITY_LOGGER.log(Level.FINE, "Basic authentication failed for user {0}", compositeUser);
        return Optional.empty();
    }

    private String extractBasicAuthUserCandidate(HttpServletRequest request) {
        String auth = safeHeader(request, "Authorization");
        if (auth == null) {
            return null;
        }
        String trimmed = auth.trim();
        if (!trimmed.regionMatches(true, 0, "Basic ", 0, 6)) {
            return null;
        }
        String encoded = trimmed.substring(6).trim();
        try {
            String decoded = new String(Base64.getDecoder().decode(encoded), java.nio.charset.StandardCharsets.UTF_8);
            int sep = decoded.lastIndexOf(':');
            if (sep <= 0) {
                return null;
            }
            String user = decoded.substring(0, sep);
            return normalize(user);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private BasicCredentials resolveCompositeCredentials(String decoded) {
        if (decoded == null) {
            return null;
        }
        int firstSeparator = decoded.indexOf(':');
        if (firstSeparator < 0) {
            return null;
        }
        BasicCredentials firstParsed = parseCredentials(decoded, firstSeparator);
        if (firstParsed != null && isCompositePrincipal(firstParsed.user())) {
            return firstParsed;
        }
        int lastSeparator = decoded.lastIndexOf(':');
        if (lastSeparator > firstSeparator) {
            BasicCredentials lastParsed = parseCredentials(decoded, lastSeparator);
            if (lastParsed != null && isCompositePrincipal(lastParsed.user())) {
                return lastParsed;
            }
        }
        return null;
    }

    private BasicCredentials parseCredentials(String decoded, int separatorIndex) {
        if (decoded == null || separatorIndex < 0 || separatorIndex >= decoded.length()) {
            return null;
        }
        String user = normalize(decoded.substring(0, separatorIndex));
        if (user == null) {
            return null;
        }
        String password = decoded.substring(separatorIndex + 1);
        return new BasicCredentials(user, password);
    }

    private void sendUnauthorized(HttpServletRequest request, HttpServletResponse response, String errorCode,
            String message, Map<String, Object> details) throws IOException {
        if (shouldAttachAuthChallenge(request)) {
            response.setHeader("WWW-Authenticate", AUTH_CHALLENGE);
        }
        AbstractResource.writeRestError(request, response, HttpServletResponse.SC_UNAUTHORIZED, errorCode, message, details);
    }

    private void sendTooManyRequests(HttpServletRequest request, HttpServletResponse response, long retryAfterSeconds)
            throws IOException {
        long retry = Math.max(1L, retryAfterSeconds);
        response.setHeader("Retry-After", Long.toString(retry));
        AbstractResource.writeRestError(request, response, HTTP_TOO_MANY_REQUESTS,
                "too_many_requests", "Too many failed authentication attempts",
                unauthorizedDetails("ip_throttled"));
    }

    private boolean shouldAttachAuthChallenge(HttpServletRequest request) {
        if (request == null) {
            return false;
        }
        // Browser fetch/XHR requests should not trigger the native Basic auth credential prompt.
        // Only attach the challenge when the request is likely a top-level navigation.
        String fetchDest = safeHeader(request, "Sec-Fetch-Dest");
        if (fetchDest != null && !fetchDest.isBlank()) {
            String normalized = fetchDest.trim().toLowerCase();
            return "document".equals(normalized) || "iframe".equals(normalized);
        }
        // Fallback: treat HTML navigations as eligible for Basic auth challenge.
        String accept = safeHeader(request, "Accept");
        return accept != null && accept.toLowerCase().contains("text/html");
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String safeHeader(HttpServletRequest req, String headerName) {
        String value = req.getHeader(headerName);
        if (value == null) {
            return null;
        }
        return value.trim();
    }

    private boolean isAnonymousPrincipal(String principalName) {
        return principalName != null && ANONYMOUS_PRINCIPAL.equalsIgnoreCase(principalName.trim());
    }

    private boolean isCompositePrincipal(String candidate) {
        if (candidate == null) {
            return false;
        }
        return candidate.contains(IInfoModel.COMPOSITE_KEY_MAKER);
    }

    private String extractFacilitySegment(String candidate) {
        if (candidate == null) {
            return null;
        }
        int separator = candidate.indexOf(IInfoModel.COMPOSITE_KEY_MAKER);
        if (separator > 0) {
            return candidate.substring(0, separator);
        }
        return null;
    }

    private Long readRetryAfter(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        Object value = request.getAttribute(IP_THROTTLED_RETRY_AFTER_ATTR);
        if (value instanceof Number number) {
            long retryAfter = number.longValue();
            return retryAfter > 0L ? retryAfter : null;
        }
        return null;
    }

    private Map<String, Object> unauthorizedDetails(String reason) {
        Map<String, Object> details = new HashMap<>();
        details.put("reason", reason);
        return details;
    }

    private void recordUnauthorizedAudit(HttpServletRequest request,
            String traceId,
            String principal,
            String errorCode,
            String errorMessage,
            String reason,
            int statusCode) {
        if (sessionAuditDispatcher == null) {
            return;
        }
        AuditEventPayload payload = new AuditEventPayload();
        payload.setAction("REST_UNAUTHORIZED_GUARD");
        payload.setResource(request != null ? request.getRequestURI() : "/resources");
        String actorId = principal == null || principal.isBlank() ? "anonymous" : principal;
        payload.setActorId(actorId);
        payload.setActorDisplayName(principal);
        payload.setActorRole("SYSTEM");
        payload.setIpAddress(AbstractResource.resolveClientIp(request));
        payload.setUserAgent(request != null ? request.getHeader("User-Agent") : null);
        String effectiveTrace = (traceId == null || traceId.isBlank()) ? UUID.randomUUID().toString() : traceId;
        payload.setRequestId(effectiveTrace);
        payload.setTraceId(effectiveTrace);
        Map<String, Object> details = new HashMap<>();
        details.put("status", "failed");
        details.put("reason", reason);
        if (errorCode != null && !errorCode.isBlank()) {
            details.put("errorCode", errorCode);
            details.putIfAbsent("reason", errorCode);
        }
        if (errorMessage != null && !errorMessage.isBlank()) {
            details.put("errorMessage", errorMessage);
        }
        details.put("httpStatus", statusCode);
        String facilityFromPrincipal = extractFacilitySegment(principal);
        if (facilityFromPrincipal != null) {
            details.put(PRINCIPAL_FACILITY_DETAILS_KEY, facilityFromPrincipal);
        }
        if (principal != null && !principal.isBlank()) {
            details.put("principal", principal);
        }
        Map<String, Object> sanitizedDetails = AuditDetailSanitizer.sanitizeDetails(details);
        payload.setPatientId(AuditDetailSanitizer.resolvePatientId(null, sanitizedDetails));
        payload.setDetails(sanitizedDetails);
        sessionAuditDispatcher.record(payload, AuditEventEnvelope.Outcome.FAILURE,
                errorCode != null && !errorCode.isBlank() ? errorCode : reason, errorMessage);
        if (request != null) {
            request.setAttribute(ERROR_AUDIT_RECORDED_ATTR, Boolean.TRUE);
        }
    }

    private void maybeRecordErrorAudit(HttpServletRequest request, HttpServletResponse response, Throwable failure) {
        if (sessionAuditDispatcher == null || request == null) {
            return;
        }
        if (Boolean.TRUE.equals(request.getAttribute(ERROR_AUDIT_RECORDED_ATTR))) {
            return;
        }
        int status = resolveErrorStatus(response);
        // If an exception occurred but the response status is still success (e.g. 200),
        // treat it as an Internal Server Error (500) to ensure it gets audited.
        if (failure != null && status < HttpServletResponse.SC_BAD_REQUEST) {
            status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
        }

        if (status < HttpServletResponse.SC_BAD_REQUEST) {
            return;
        }
        AuditEventPayload payload = new AuditEventPayload();
        String resource = request.getRequestURI();
        payload.setAction("REST_ERROR_RESPONSE");
        payload.setResource(resource != null ? resource : "/resources");
        String actorId = request.getRemoteUser();
        if (actorId == null || actorId.isBlank()) {
            actorId = ANONYMOUS_PRINCIPAL;
        }
        payload.setActorId(actorId);
        payload.setActorDisplayName(actorId);
        payload.setActorRole("SYSTEM");
        payload.setIpAddress(AbstractResource.resolveClientIp(request));
        payload.setUserAgent(request.getHeader("User-Agent"));
        String traceId = resolveTraceId(request);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }
        payload.setRequestId(traceId);
        payload.setTraceId(traceId);
        Map<String, Object> details = new HashMap<>();
        details.put("status", "failed");
        details.put("httpStatus", status);
        String errorCode = resolveErrorCode(request, status);
        String errorMessage = resolveErrorMessage(request, failure);
        mergeErrorDetails(details, request);
        if (errorCode != null && !errorCode.isBlank()) {
            details.put("errorCode", errorCode);
            details.putIfAbsent("reason", errorCode);
        }
        if (errorMessage != null && !errorMessage.isBlank()) {
            details.put("errorMessage", errorMessage);
        }
        if (!details.containsKey("validationError") && (status == 400 || status == 422)) {
            details.put("validationError", Boolean.TRUE);
        }
        String facilityFromPrincipal = extractFacilitySegment(request != null ? request.getRemoteUser() : null);
        if (facilityFromPrincipal != null) {
            details.put(PRINCIPAL_FACILITY_DETAILS_KEY, facilityFromPrincipal);
        }
        if (failure != null) {
            details.put("exception", failure.getClass().getName());
            if (failure.getMessage() != null && !failure.getMessage().isBlank()) {
                details.put("exceptionMessage", failure.getMessage());
            }
        }
        Map<String, Object> sanitizedDetails = AuditDetailSanitizer.sanitizeDetails(details);
        payload.setPatientId(AuditDetailSanitizer.resolvePatientId(null, sanitizedDetails));
        payload.setDetails(sanitizedDetails);
        sessionAuditDispatcher.record(payload, AuditEventEnvelope.Outcome.FAILURE, errorCode, errorMessage);
        request.setAttribute(ERROR_AUDIT_RECORDED_ATTR, Boolean.TRUE);
    }

    private int resolveErrorStatus(HttpServletResponse response) {
        if (response != null && response.getStatus() > 0) {
            return response.getStatus();
        }
        return HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
    }

    private String resolveErrorCode(HttpServletRequest request, int status) {
        if (request != null) {
            Object attribute = request.getAttribute(AbstractResource.ERROR_CODE_ATTRIBUTE);
            if (attribute instanceof String code && !code.isBlank()) {
                return code;
            }
        }
        return "http_" + status;
    }

    private String resolveErrorMessage(HttpServletRequest request, Throwable failure) {
        if (request != null) {
            Object attribute = request.getAttribute(AbstractResource.ERROR_MESSAGE_ATTRIBUTE);
            if (attribute instanceof String message && !message.isBlank()) {
                return message;
            }
        }
        if (failure != null && failure.getMessage() != null && !failure.getMessage().isBlank()) {
            return failure.getMessage();
        }
        return null;
    }

    private void mergeErrorDetails(Map<String, Object> target, HttpServletRequest request) {
        if (target == null || request == null) {
            return;
        }
        Object attribute = request.getAttribute(AbstractResource.ERROR_DETAILS_ATTRIBUTE);
        if (!(attribute instanceof Map<?, ?> details)) {
            return;
        }
        details.forEach((key, value) -> {
            if (key == null || value == null) {
                return;
            }
            String normalizedKey = key.toString();
            if (normalizedKey.isBlank()) {
                return;
            }
            target.putIfAbsent(normalizedKey, value);
        });
    }
}
