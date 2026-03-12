package open.dolphin.runtime;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Shared runtime configuration helpers for environment/safety-sensitive settings.
 */
public final class RuntimeConfigurationSupport {

    public static final String PROP_ENVIRONMENT = "opendolphin.environment";
    public static final String ENV_ENVIRONMENT = "OPENDOLPHIN_ENVIRONMENT";
    public static final String PROP_SERVER_DATA_DIR = "jboss.server.data.dir";
    public static final String PROP_TIMEZONE = "opendolphin.timezone";
    public static final String ENV_TIMEZONE = "OPENDOLPHIN_TIMEZONE";
    public static final String PROP_CONFIG_DIR = "opendolphin.config.dir";
    public static final String ENV_CONFIG_DIR = "OPENDOLPHIN_CONFIG_DIR";
    public static final String PROP_CUSTOM_PROPERTIES_PATH = "opendolphin.custom.properties.path";
    public static final String PROP_JBOSS_HOME_DIR = "jboss.home.dir";
    public static final String PROP_JBOSS_SERVER_CONFIG_DIR = "jboss.server.config.dir";
    public static final String DEFAULT_TIMEZONE = "Asia/Tokyo";

    private static final Set<String> PRODUCTION_LIKE_PREFIXES = Set.of(
            "prod", "prd", "production", "stage", "stg", "staging", "it", "uat");

    private RuntimeConfigurationSupport() {
    }

    public static String resolveEnvironment() {
        return firstNonBlank(
                System.getProperty(PROP_ENVIRONMENT),
                System.getenv(ENV_ENVIRONMENT),
                System.getenv("ENVIRONMENT"),
                System.getenv("DEPLOY_ENV"),
                System.getenv("STAGE"),
                System.getenv("VITE_ENVIRONMENT"),
                System.getenv("VITE_STAGE"),
                System.getenv("VITE_DEPLOY_ENV"),
                System.getenv("NODE_ENV")
        );
    }

    public static boolean isProductionLikeEnvironment(String environment) {
        if (environment == null || environment.isBlank()) {
            return false;
        }
        String normalized = environment.trim().toLowerCase(Locale.ROOT);
        for (String prefix : PRODUCTION_LIKE_PREFIXES) {
            if (normalized.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    public static Path resolveServerDataDirectoryOrThrow(String component) {
        String raw = firstNonBlank(System.getProperty(PROP_SERVER_DATA_DIR));
        if (raw == null) {
            throw new IllegalStateException(component
                    + " requires -D" + PROP_SERVER_DATA_DIR
                    + ". Unsafe /tmp or user.home fallback is disabled.");
        }
        Path path = Paths.get(raw).toAbsolutePath().normalize();
        try {
            Files.createDirectories(path);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to create server data directory: " + path, ex);
        }
        return path;
    }

    public static String describeServerDataDirectory() {
        String raw = firstNonBlank(System.getProperty(PROP_SERVER_DATA_DIR));
        if (raw == null) {
            return "MISSING(-D" + PROP_SERVER_DATA_DIR + ")";
        }
        return Paths.get(raw).toAbsolutePath().normalize().toString();
    }

    public static String resolveTimezoneId() {
        String configured = firstNonBlank(System.getProperty(PROP_TIMEZONE), System.getenv(ENV_TIMEZONE));
        if (configured == null) {
            return DEFAULT_TIMEZONE;
        }
        try {
            return ZoneId.of(configured.trim()).getId();
        } catch (RuntimeException ex) {
            return DEFAULT_TIMEZONE;
        }
    }

    public static ZoneId resolveTimezone() {
        return ZoneId.of(resolveTimezoneId());
    }

    public static Path resolveConfigDirectory() {
        String raw = firstNonBlank(System.getProperty(PROP_CONFIG_DIR), System.getenv(ENV_CONFIG_DIR));
        if (raw != null) {
            return Paths.get(raw).toAbsolutePath().normalize();
        }
        String jbossServerConfigDir = firstNonBlank(System.getProperty(PROP_JBOSS_SERVER_CONFIG_DIR));
        if (jbossServerConfigDir != null) {
            return Paths.get(jbossServerConfigDir).toAbsolutePath().normalize();
        }
        String jbossHome = firstNonBlank(System.getProperty(PROP_JBOSS_HOME_DIR));
        if (jbossHome != null) {
            return Paths.get(jbossHome)
                    .resolve("standalone")
                    .resolve("configuration")
                    .toAbsolutePath()
                    .normalize();
        }
        String serverDataDir = firstNonBlank(System.getProperty(PROP_SERVER_DATA_DIR));
        if (serverDataDir != null) {
            return Paths.get(serverDataDir).resolve("config").toAbsolutePath().normalize();
        }
        return Paths.get("config").toAbsolutePath().normalize();
    }

    public static Path resolveConfigPath(String fileName) {
        return resolveConfigDirectory().resolve(fileName).toAbsolutePath().normalize();
    }

    public static Path resolveLegacyCustomPropertiesPath() {
        String explicit = firstNonBlank(System.getProperty(PROP_CUSTOM_PROPERTIES_PATH));
        if (explicit != null) {
            return Paths.get(explicit).toAbsolutePath().normalize();
        }
        String jbossHome = firstNonBlank(System.getProperty(PROP_JBOSS_HOME_DIR));
        if (jbossHome != null) {
            return Paths.get(jbossHome).resolve("custom.properties").toAbsolutePath().normalize();
        }
        return resolveConfigPath("custom.properties");
    }

    public static Properties loadLegacyCustomProperties() {
        return loadProperties(resolveLegacyCustomPropertiesPath());
    }

    public static Properties loadProperties(Path path) {
        Properties properties = new Properties();
        if (path == null || !Files.isRegularFile(path)) {
            return properties;
        }
        Charset charset = resolveLegacyPropertiesCharset();
        try (FileInputStream fis = new FileInputStream(path.toFile());
             InputStreamReader reader = new InputStreamReader(fis, charset)) {
            properties.load(reader);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load properties: " + path, ex);
        }
        return properties;
    }

    public static Optional<String> resolveUnifiedSetting(String envKey,
                                                         String propertyKey,
                                                         Supplier<String> jsonSupplier,
                                                         Supplier<String> yamlSupplier,
                                                         Properties legacyProperties,
                                                         String legacyPropertyKey) {
        String fromEnv = envKey != null ? System.getenv(envKey) : null;
        String fromProperty = propertyKey != null ? System.getProperty(propertyKey) : null;
        String fromJson = jsonSupplier != null ? jsonSupplier.get() : null;
        String fromYaml = yamlSupplier != null ? yamlSupplier.get() : null;
        String fromLegacy = null;
        if (legacyProperties != null && legacyPropertyKey != null) {
            fromLegacy = legacyProperties.getProperty(legacyPropertyKey);
        }
        return Optional.ofNullable(firstNonBlank(fromEnv, fromProperty, fromJson, fromYaml, fromLegacy));
    }

    private static Charset resolveLegacyPropertiesCharset() {
        try {
            return Charset.forName("JISAutoDetect");
        } catch (Exception ex) {
            return Charset.forName("UTF-8");
        }
    }

    public static Boolean parseBooleanFlag(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        switch (normalized) {
            case "1":
            case "true":
            case "yes":
            case "y":
            case "on":
                return Boolean.TRUE;
            case "0":
            case "false":
            case "no":
            case "n":
            case "off":
                return Boolean.FALSE;
            default:
                return null;
        }
    }

    public static String firstNonBlank(String... candidates) {
        if (candidates == null) {
            return null;
        }
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate.trim();
            }
        }
        return null;
    }
}
