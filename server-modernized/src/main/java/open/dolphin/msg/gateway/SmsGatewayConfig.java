package open.dolphin.msg.gateway;

import com.plivo.api.models.base.LogLevel;
import jakarta.enterprise.context.ApplicationScoped;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import open.dolphin.runtime.RuntimeConfigurationSupport;

/**
 * Plivo SMS ゲートウェイ設定を読み込む。環境変数が最優先で、
 * 指定が無い場合は WildFly の custom.properties から補完する。
 */
@ApplicationScoped
public class SmsGatewayConfig {

    private static final Logger LOGGER = Logger.getLogger(SmsGatewayConfig.class.getName());

    private static final String ENV_AUTH_ID = "PLIVO_AUTH_ID";
    private static final String ENV_AUTH_TOKEN = "PLIVO_AUTH_TOKEN";
    private static final String ENV_SOURCE_NUMBER = "PLIVO_SOURCE_NUMBER";
    private static final String ENV_BASE_URL = "PLIVO_BASE_URL";
    private static final String ENV_ENVIRONMENT = "PLIVO_ENVIRONMENT";
    private static final String ENV_LOG_LEVEL = "PLIVO_LOG_LEVEL";
    private static final String ENV_LOG_CONTENT = "PLIVO_LOG_MESSAGE_CONTENT";
    private static final String ENV_DEFAULT_COUNTRY = "PLIVO_DEFAULT_COUNTRY";
    private static final String ENV_HTTP_CONNECT_TIMEOUT = "PLIVO_HTTP_CONNECT_TIMEOUT";
    private static final String ENV_HTTP_READ_TIMEOUT = "PLIVO_HTTP_READ_TIMEOUT";
    private static final String ENV_HTTP_WRITE_TIMEOUT = "PLIVO_HTTP_WRITE_TIMEOUT";
    private static final String ENV_HTTP_CALL_TIMEOUT = "PLIVO_HTTP_CALL_TIMEOUT";
    private static final String ENV_HTTP_RETRY_ON_FAILURE = "PLIVO_HTTP_RETRY_ON_CONNECTION_FAILURE";

    private static final String PROP_AUTH_ID = "plivo.auth.id";
    private static final String PROP_AUTH_TOKEN = "plivo.auth.token";
    private static final String PROP_SOURCE_NUMBER = "plivo.source.number";
    private static final String PROP_BASE_URL = "plivo.baseUrl";
    private static final String PROP_ENVIRONMENT = "plivo.environment";
    private static final String PROP_LOG_LEVEL = "plivo.log.level";
    private static final String PROP_LOG_CONTENT = "plivo.log.messageContent";
    private static final String PROP_DEFAULT_COUNTRY = "plivo.defaultCountry";
    private static final String PROP_HTTP_CONNECT_TIMEOUT = "plivo.http.connectTimeout";
    private static final String PROP_HTTP_READ_TIMEOUT = "plivo.http.readTimeout";
    private static final String PROP_HTTP_WRITE_TIMEOUT = "plivo.http.writeTimeout";
    private static final String PROP_HTTP_CALL_TIMEOUT = "plivo.http.callTimeout";
    private static final String PROP_HTTP_RETRY_ON_FAILURE = "plivo.http.retryOnConnectionFailure";

    private static final String ENVIRONMENT_SANDBOX = "sandbox";
    private static final String DEFAULT_PROD_BASE = "https://api.plivo.com/v1/";
    private static final String DEFAULT_SANDBOX_BASE = "https://api.sandbox.plivo.com/v1/";

    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration DEFAULT_WRITE_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration DEFAULT_CALL_TIMEOUT = Duration.ofSeconds(45);

    private volatile PlivoSettings cachedSettings;

    public PlivoSettings plivoSettings() {
        PlivoSettings settings = cachedSettings;
        if (settings == null) {
            settings = reload();
        }
        return settings;
    }

    public synchronized PlivoSettings reload() {
        Properties legacy = RuntimeConfigurationSupport.loadLegacyCustomProperties();
        String authId = resolve(ENV_AUTH_ID, PROP_AUTH_ID, legacy, PROP_AUTH_ID);
        String authToken = resolve(ENV_AUTH_TOKEN, PROP_AUTH_TOKEN, legacy, PROP_AUTH_TOKEN);
        String sourceNumber = resolve(ENV_SOURCE_NUMBER, PROP_SOURCE_NUMBER, legacy, PROP_SOURCE_NUMBER);
        String environment = resolve(ENV_ENVIRONMENT, PROP_ENVIRONMENT, legacy, PROP_ENVIRONMENT);
        String baseUrl = determineBaseUrl(environment, resolve(ENV_BASE_URL, PROP_BASE_URL, legacy, PROP_BASE_URL));
        LogLevel logLevel = parseLogLevel(resolve(ENV_LOG_LEVEL, PROP_LOG_LEVEL, legacy, PROP_LOG_LEVEL));
        boolean logContent = parseBoolean(resolve(ENV_LOG_CONTENT, PROP_LOG_CONTENT, legacy, PROP_LOG_CONTENT), false);
        String defaultCountry = resolve(ENV_DEFAULT_COUNTRY, PROP_DEFAULT_COUNTRY, legacy, PROP_DEFAULT_COUNTRY);
        Duration connectTimeout = parseDuration(resolve(ENV_HTTP_CONNECT_TIMEOUT, PROP_HTTP_CONNECT_TIMEOUT, legacy, PROP_HTTP_CONNECT_TIMEOUT), DEFAULT_CONNECT_TIMEOUT);
        Duration readTimeout = parseDuration(resolve(ENV_HTTP_READ_TIMEOUT, PROP_HTTP_READ_TIMEOUT, legacy, PROP_HTTP_READ_TIMEOUT), DEFAULT_READ_TIMEOUT);
        Duration writeTimeout = parseDuration(resolve(ENV_HTTP_WRITE_TIMEOUT, PROP_HTTP_WRITE_TIMEOUT, legacy, PROP_HTTP_WRITE_TIMEOUT), DEFAULT_WRITE_TIMEOUT);
        Duration callTimeout = parseDuration(resolve(ENV_HTTP_CALL_TIMEOUT, PROP_HTTP_CALL_TIMEOUT, legacy, PROP_HTTP_CALL_TIMEOUT), DEFAULT_CALL_TIMEOUT);
        boolean retryOnConnectionFailure = parseBoolean(resolve(ENV_HTTP_RETRY_ON_FAILURE, PROP_HTTP_RETRY_ON_FAILURE, legacy, PROP_HTTP_RETRY_ON_FAILURE), true);

        PlivoSettings settings = new PlivoSettings(
                trim(authId),
                trim(authToken),
                trim(sourceNumber),
                baseUrl,
                environmentName(environment),
                logLevel,
                logContent,
                normalizeCountryCode(defaultCountry),
                connectTimeout,
                readTimeout,
                writeTimeout,
                callTimeout,
                retryOnConnectionFailure
        );
        cachedSettings = settings;
        return settings;
    }

    private String resolve(String envKey, String propertyKey, Properties legacy, String legacyKey) {
        return RuntimeConfigurationSupport.resolveUnifiedSetting(
                        envKey,
                        propertyKey,
                        null,
                        null,
                        legacy,
                        legacyKey)
                .orElse(null);
    }

    private String determineBaseUrl(String environment, String candidate) {
        String trimmed = trim(candidate);
        if (trimmed == null || trimmed.isEmpty()) {
            if (ENVIRONMENT_SANDBOX.equalsIgnoreCase(trim(environment))) {
                trimmed = DEFAULT_SANDBOX_BASE;
            } else {
                trimmed = DEFAULT_PROD_BASE;
            }
        }
        try {
            URI uri = new URI(trimmed);
            if (!"https".equalsIgnoreCase(uri.getScheme())) {
                throw new IllegalArgumentException("Plivo base URL must use HTTPS");
            }
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException("Plivo base URL is invalid: " + trimmed, ex);
        }
        return ensureTrailingSlash(trimmed);
    }

    private String ensureTrailingSlash(String value) {
        if (value.endsWith("/")) {
            return value;
        }
        return value + "/";
    }

    private LogLevel parseLogLevel(String value) {
        if (value == null || value.isBlank()) {
            return LogLevel.NONE;
        }
        try {
            return LogLevel.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            LOGGER.log(Level.WARNING, "Invalid PLIVO_LOG_LEVEL value: {0}", value);
            return LogLevel.NONE;
        }
    }

    private Duration parseDuration(String value, Duration defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        String trimmed = value.trim();
        try {
            return Duration.parse(trimmed);
        } catch (DateTimeParseException ex) {
            try {
                if (trimmed.endsWith("ms") || trimmed.endsWith("MS")) {
                    String numeric = trimmed.substring(0, trimmed.length() - 2).trim();
                    return Duration.ofMillis(Long.parseLong(numeric));
                }
                if (trimmed.endsWith("s") || trimmed.endsWith("S")) {
                    String numeric = trimmed.substring(0, trimmed.length() - 1).trim();
                    return Duration.ofSeconds(Long.parseLong(numeric));
                }
                if (trimmed.endsWith("m") || trimmed.endsWith("M")) {
                    String numeric = trimmed.substring(0, trimmed.length() - 1).trim();
                    return Duration.ofMinutes(Long.parseLong(numeric));
                }
                return Duration.ofSeconds(Long.parseLong(trimmed));
            } catch (NumberFormatException inner) {
                LOGGER.log(Level.WARNING, "Invalid Plivo timeout value: {0}", trimmed);
                return defaultValue;
            }
        }
    }

    private boolean parseBoolean(String value, boolean defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value.trim());
    }

    private String normalizeCountryCode(String value) {
        if (value == null || value.isBlank()) {
            return "+81";
        }
        String trimmed = value.trim();
        return trimmed.startsWith("+") ? trimmed : "+" + trimmed;
    }

    private String environmentName(String value) {
        String trimmed = trim(value);
        if (trimmed == null) {
            return "production";
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }

    private String trim(String value) {
        return value != null ? value.trim() : null;
    }

    public record PlivoSettings(
            String authId,
            String authToken,
            String sourceNumber,
            String baseUrl,
            String environment,
            LogLevel logLevel,
            boolean logMessageContent,
            String defaultCountryCode,
            Duration connectTimeout,
            Duration readTimeout,
            Duration writeTimeout,
            Duration callTimeout,
            boolean retryOnConnectionFailure
    ) {

        public boolean isConfigured() {
            return authId != null && !authId.isBlank()
                    && authToken != null && !authToken.isBlank()
                    && sourceNumber != null && !sourceNumber.isBlank();
        }
    }
}
