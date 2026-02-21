package open.dolphin.orca.transport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.SSLContext;
import open.dolphin.orca.config.OrcaConnectionConfigStore;
import open.dolphin.msg.gateway.ExternalServiceAuditLogger;
import open.dolphin.orca.OrcaGatewayException;
import open.dolphin.orca.transport.OrcaHttpClient.OrcaHttpResponse;
import open.dolphin.rest.OrcaApiProxySupport;
import open.dolphin.session.framework.SessionTraceContext;
import open.dolphin.session.framework.SessionTraceManager;

/**
 * HTTP transport for ORCA API endpoints using Basic auth.
 */
@ApplicationScoped
public class RestOrcaTransport implements OrcaTransport {

    private static final Logger LOGGER = Logger.getLogger(RestOrcaTransport.class.getName());
    private static final String ORCA_ACCEPT = "application/xml";
    private static final ObjectMapper JSON = new ObjectMapper();

    private OrcaHttpClient httpClient;
    private HttpClient rawHttpClient;
    private volatile OrcaTransportSettings cachedSettings;

    @Inject
    SessionTraceManager traceManager;

    @Inject
    OrcaConnectionConfigStore orcaConnectionConfigStore;

    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(5);

    @PostConstruct
    private void initialize() {
        this.rawHttpClient = HttpClient.newBuilder()
                .connectTimeout(DEFAULT_CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        this.httpClient = new OrcaHttpClient(rawHttpClient);
        OrcaTransportSettings settings = reloadSettings();
        if (settings != null) {
            LOGGER.log(Level.INFO, "ORCA transport settings loaded: {0}", settings.auditSummary());
        } else {
            LOGGER.log(Level.WARNING, "ORCA transport settings could not be loaded during initialization");
        }
    }

    @Override
    public String invoke(OrcaEndpoint endpoint, String requestXml) {
        OrcaTransportResult result = invokeDetailed(endpoint, OrcaTransportRequest.post(requestXml));
        return result != null ? result.getBody() : null;
    }

    @Override
    public OrcaTransportResult invokeDetailed(OrcaEndpoint endpoint, OrcaTransportRequest request) {
        OrcaTransportSettings resolved = currentSettings();
        if (resolved == null) {
            LOGGER.log(Level.WARNING, "ORCA transport settings unavailable; attempting reload (endpoint={0})",
                    endpoint != null ? endpoint.getPath() : "unknown");
            resolved = reloadCache();
        }
        String traceId = resolveTraceId();
        String action = "ORCA_HTTP";
        if (endpoint == null) {
            OrcaGatewayException failure = new OrcaGatewayException("Endpoint must not be null");
            ExternalServiceAuditLogger.logOrcaFailure(traceId, action, null, resolved.auditSummary(), failure);
            throw failure;
        }
        if (!resolved.isReady()) {
            OrcaGatewayException failure = new OrcaGatewayException("ORCA transport settings are incomplete");
            ExternalServiceAuditLogger.logOrcaFailure(traceId, action, endpoint.getPath(), resolved.auditSummary(), failure);
            throw failure;
        }
        String payload = request != null && request.getBody() != null ? request.getBody() : "";
        String method = resolveMethod(endpoint, request);
        boolean isGet = "GET".equalsIgnoreCase(method);
        if (endpoint.requiresBody() && payload.isBlank()) {
            logMissingBody(traceId, endpoint, resolved);
            OrcaGatewayException failure = new OrcaGatewayException("ORCA request body is required for " + endpoint.getPath());
            ExternalServiceAuditLogger.logOrcaFailure(traceId, action, endpoint.getPath(), resolved.auditSummary(), failure);
            throw failure;
        }
        List<String> missingFields = isGet ? List.of() : findMissingFields(endpoint, payload);
        if (!missingFields.isEmpty()) {
            logMissingFields(traceId, endpoint, resolved, missingFields);
            OrcaGatewayException failure = new OrcaGatewayException(
                    "ORCA request body is missing required fields: " + String.join(", ", missingFields));
            ExternalServiceAuditLogger.logOrcaFailure(traceId, action, endpoint.getPath(), resolved.auditSummary(), failure);
            throw failure;
        }
        String requestId = traceId;
        String query = resolveQuery(endpoint, payload, request);
        String url = resolved.buildUrl(endpoint, query);
        String accept = resolveAccept(endpoint, request);
        try {
            ExternalServiceAuditLogger.logOrcaRequest(traceId, action, endpoint.getPath(), resolved.auditSummary());
            OrcaHttpResponse response = isGet
                    ? httpClient.get(resolved, endpoint.getPath(), query, accept, requestId, traceId)
                    : httpClient.postXml2(resolved, endpoint.getPath(), payload, query, accept, requestId, traceId);
            ExternalServiceAuditLogger.logOrcaResponse(traceId, action, endpoint.getPath(), response.status(), resolved.auditSummary());
            java.util.Map<String, java.util.List<String>> headers = new java.util.LinkedHashMap<>(response.headers());
            if (response.apiResult() != null && response.apiResult().apiResult() != null) {
                String apiResult = response.apiResult().apiResult();
                String sanitizedApiResult = OrcaApiProxySupport.sanitizeHeaderValue("X-Orca-Api-Result", apiResult);
                if (sanitizedApiResult != null) {
                    headers.put("X-Orca-Api-Result", java.util.List.of(sanitizedApiResult));
                    headers.put("X-Orca-Api-Result-Success",
                            java.util.List.of(Boolean.toString(OrcaApiProxySupport.isApiResultSuccess(sanitizedApiResult))));
                }
                // Api_Result_Message can contain control characters; omit header to avoid invalid response headers.
                if (response.apiResult().warnings() != null && !response.apiResult().warnings().isEmpty()) {
                    String warnings = String.join(" | ", response.apiResult().warnings());
                    String sanitized = OrcaApiProxySupport.sanitizeHeaderValue("X-Orca-Warnings", warnings);
                    if (sanitized != null) {
                        headers.put("X-Orca-Warnings", java.util.List.of(sanitized));
                    }
                }
            }
            return new OrcaTransportResult(url, method, response.status(), response.body(), response.contentType(), headers);
        } catch (RuntimeException ex) {
            ExternalServiceAuditLogger.logOrcaFailure(traceId, action, endpoint.getPath(), resolved.auditSummary(), ex);
            throw ex;
        }
    }

    private static String resolveMethod(OrcaEndpoint endpoint, OrcaTransportRequest request) {
        if (request != null && request.getMethod() != null && !request.getMethod().isBlank()) {
            return request.getMethod().trim().toUpperCase(Locale.ROOT);
        }
        if (endpoint != null && endpoint.getMethod() != null && !endpoint.getMethod().isBlank()) {
            return endpoint.getMethod().trim().toUpperCase(Locale.ROOT);
        }
        return "POST";
    }

    private static String resolveAccept(OrcaEndpoint endpoint, OrcaTransportRequest request) {
        if (request != null && request.getAccept() != null && !request.getAccept().isBlank()) {
            return request.getAccept().trim();
        }
        return endpoint != null && endpoint.getAccept() != null ? endpoint.getAccept() : ORCA_ACCEPT;
    }

    private static String resolveQuery(OrcaEndpoint endpoint, String payload, OrcaTransportRequest request) {
        if (request != null && request.getQuery() != null && !request.getQuery().isBlank()) {
            return request.getQuery().trim();
        }
        return extractQueryFromMeta(endpoint, payload);
    }

    private String resolveTraceId() {
        if (traceManager == null) {
            return null;
        }
        SessionTraceContext context = traceManager.current();
        return context != null ? context.getTraceId() : null;
    }

    public HttpClient rawHttpClient() {
        return rawHttpClient;
    }

    public String buildOrcaUrl(String path) {
        OrcaTransportSettings settings = currentSettings();
        return settings != null ? settings.buildOrcaUrl(path) : null;
    }

    public String resolveBasicAuthHeader() {
        OrcaTransportSettings settings = currentSettings();
        if (settings == null || !settings.hasCredentials()) {
            return null;
        }
        return settings.basicAuthHeader();
    }

    public OrcaTransportSettings reloadSettings() {
        OrcaTransportSettings settings = reloadCache();
        if (settings != null) {
            LOGGER.log(Level.INFO, "ORCA transport settings reloaded: {0}", settings.auditSummary());
        } else {
            LOGGER.log(Level.WARNING, "ORCA transport settings reload failed: settings null");
        }
        return settings;
    }

    public OrcaTransportSettings currentSettingsInstance() {
        return currentSettings();
    }

    public String auditSummary() {
        OrcaTransportSettings settings = currentSettings();
        return settings != null ? settings.auditSummary() : "orca.host=unknown";
    }

    private OrcaTransportSettings currentSettings() {
        OrcaTransportSettings settings = cachedSettings;
        if (settings == null) {
            settings = reloadCache();
        }
        return settings;
    }

    private synchronized OrcaTransportSettings reloadCache() {
        OrcaTransportSettings settings = null;
        try {
            settings = loadSettingsFromAdminConfig();
        } catch (RuntimeException ex) {
            LOGGER.log(Level.WARNING, "Failed to load ORCA transport settings from admin config: " + ex.getMessage(), ex);
        }
        if (settings == null) {
            settings = OrcaTransportSettings.load();
            // Reset to default client when falling back.
            this.rawHttpClient = HttpClient.newBuilder()
                    .connectTimeout(DEFAULT_CONNECT_TIMEOUT)
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .build();
            this.httpClient = new OrcaHttpClient(rawHttpClient);
        }
        if (settings == null) {
            LOGGER.warning("ORCA transport settings load returned null");
        } else if (!settings.isReady()) {
            LOGGER.log(Level.WARNING, "ORCA transport settings not ready: {0}", settings.auditSummary());
        }
        cachedSettings = settings;
        return settings;
    }

    private OrcaTransportSettings loadSettingsFromAdminConfig() {
        if (orcaConnectionConfigStore == null) {
            return null;
        }
        OrcaConnectionConfigStore.ResolvedOrcaConnection resolved = orcaConnectionConfigStore.resolve();
        if (resolved == null) {
            return null;
        }
        OrcaTransportSettings settings = OrcaTransportSettings.fromAdminConfig(
                resolved.baseUrl(),
                resolved.useWeborca(),
                resolved.username(),
                resolved.password()
        );
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(DEFAULT_CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER);
        boolean hasCustomCa = resolved.caCertificate() != null && resolved.caCertificate().length > 0;
        if (resolved.clientAuthEnabled() || hasCustomCa) {
            SSLContext sslContext = OrcaTlsSupport.buildSslContext(
                    resolved.clientAuthEnabled() ? resolved.clientCertificateP12() : null,
                    resolved.clientAuthEnabled() ? resolved.clientCertificatePassphrase() : null,
                    resolved.caCertificate()
            );
            builder.sslContext(sslContext);
        }
        this.rawHttpClient = builder.build();
        this.httpClient = new OrcaHttpClient(rawHttpClient);
        return settings;
    }

    private static void logMissingBody(String traceId, OrcaEndpoint endpoint, OrcaTransportSettings settings) {
        java.util.List<String> fields = endpoint != null ? endpoint.requiredFields() : java.util.List.of();
        String fieldSummary = fields.isEmpty() ? "unknown" : String.join(",", fields);
        LOGGER.log(Level.WARNING, "ORCA request body is missing traceId={0} path={1} requiredFields={2} target={3}",
                new Object[]{traceId, endpoint != null ? endpoint.getPath() : "unknown", fieldSummary,
                        settings != null ? settings.auditSummary() : "orca.host=unknown"});
    }

    private static void logMissingFields(String traceId, OrcaEndpoint endpoint, OrcaTransportSettings settings,
            List<String> missingFields) {
        String fieldSummary = (missingFields == null || missingFields.isEmpty())
                ? "unknown"
                : String.join(",", missingFields);
        LOGGER.log(Level.WARNING, "ORCA request body missing required fields traceId={0} path={1} missing={2} target={3}",
                new Object[]{traceId, endpoint != null ? endpoint.getPath() : "unknown", fieldSummary,
                        settings != null ? settings.auditSummary() : "orca.host=unknown"});
    }

    private static List<String> findMissingFields(OrcaEndpoint endpoint, String payload) {
        if (endpoint == null) {
            return List.of();
        }
        List<String> required = endpoint.requiredFields();
        if (required == null || required.isEmpty()) {
            return List.of();
        }
        JsonNode jsonRoot = parseJsonPayload(payload);
        List<String> missing = new ArrayList<>();
        for (String spec : required) {
            if (spec == null || spec.isBlank()) {
                continue;
            }
            String trimmed = spec.trim();
            if (trimmed.contains("/")) {
                String[] options = trimmed.split("/");
                boolean found = false;
                for (String option : options) {
                    String candidate = option.trim();
                    if (candidate.isEmpty()) {
                        continue;
                    }
                    if (hasRequiredFieldWithValue(payload, jsonRoot, candidate)) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    missing.add(trimmed);
                }
            } else if (!hasRequiredFieldWithValue(payload, jsonRoot, trimmed)) {
                missing.add(trimmed);
            }
        }
        return missing;
    }

    private static JsonNode parseJsonPayload(String payload) {
        if (!OrcaApiProxySupport.isJsonPayload(payload)) {
            return null;
        }
        try {
            return JSON.readTree(payload);
        } catch (IOException ex) {
            return null;
        }
    }

    private static boolean hasRequiredFieldWithValue(String payload, JsonNode jsonRoot, String fieldName) {
        if (fieldName == null || fieldName.isBlank()) {
            return false;
        }
        if (jsonRoot != null) {
            return hasJsonFieldWithValue(jsonRoot, fieldName);
        }
        return hasXmlTagWithValue(payload, fieldName);
    }

    private static boolean hasJsonFieldWithValue(JsonNode node, String fieldName) {
        if (node == null || fieldName == null || fieldName.isBlank()) {
            return false;
        }
        if (node.isObject()) {
            java.util.Iterator<java.util.Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                java.util.Map.Entry<String, JsonNode> entry = fields.next();
                JsonNode value = entry.getValue();
                if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(fieldName)
                        && hasJsonValue(value)) {
                    return true;
                }
                if (hasJsonFieldWithValue(value, fieldName)) {
                    return true;
                }
            }
            return false;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                if (hasJsonFieldWithValue(child, fieldName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasJsonValue(JsonNode value) {
        if (value == null || value.isNull()) {
            return false;
        }
        if (value.isTextual()) {
            return !value.asText().isBlank();
        }
        if (value.isArray() || value.isObject()) {
            return value.size() > 0;
        }
        return true;
    }

    private static boolean hasXmlTagWithValue(String payload, String tag) {
        if (payload == null || payload.isBlank() || tag == null || tag.isBlank()) {
            return false;
        }
        String patternText = "<" + Pattern.quote(tag) + "(\\s[^>]*)?>(.*?)</" + Pattern.quote(tag) + ">";
        Pattern pattern = Pattern.compile(patternText, Pattern.DOTALL);
        Matcher matcher = pattern.matcher(payload);
        while (matcher.find()) {
            String content = matcher.group(2);
            if (content != null && !content.trim().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static String extractQueryFromMeta(OrcaEndpoint endpoint, String payload) {
        if (endpoint == null || !endpoint.usesQueryFromMeta()) {
            return null;
        }
        if (payload == null || payload.isBlank()) {
            return null;
        }
        int start = payload.indexOf("<!--");
        if (start < 0) {
            return null;
        }
        int metaIndex = payload.indexOf("orca-meta:", start);
        if (metaIndex < 0) {
            return null;
        }
        int end = payload.indexOf("-->", metaIndex);
        if (end < 0) {
            return null;
        }
        String content = payload.substring(metaIndex + "orca-meta:".length(), end).trim();
        String[] parts = content.split("\\s+");
        for (String part : parts) {
            if (part.startsWith("query=")) {
                return part.substring("query=".length());
            }
        }
        return null;
    }
}
