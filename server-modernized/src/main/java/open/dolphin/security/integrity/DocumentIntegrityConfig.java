package open.dolphin.security.integrity;

import java.util.Base64;
import java.util.Locale;
import java.util.Optional;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves runtime configuration for document integrity sealing and verification.
 */
@ApplicationScoped
public class DocumentIntegrityConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentIntegrityConfig.class);

    private static final String PROP_MODE = "document.integrity.mode";
    private static final String PROP_HMAC_KEY_B64 = "document.integrity.hmac.key.b64";
    private static final String PROP_KEY_ID = "document.integrity.key.id";

    private static final String ENV_MODE = "DOCUMENT_INTEGRITY_MODE";
    private static final String ENV_HMAC_KEY_B64 = "DOCUMENT_INTEGRITY_HMAC_KEY_B64";
    private static final String ENV_KEY_ID = "DOCUMENT_INTEGRITY_KEY_ID";

    private static final int MIN_HMAC_KEY_BYTES = 32;

    public Mode resolveMode() {
        String raw = firstNonBlank(
                configValue(PROP_MODE).orElse(null),
                configValue(ENV_MODE).orElse(null),
                System.getProperty(PROP_MODE),
                System.getProperty(ENV_MODE),
                System.getenv(ENV_MODE)
        );
        if (raw == null) {
            return Mode.PERMISSIVE;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "off" -> Mode.OFF;
            case "permissive" -> Mode.PERMISSIVE;
            case "enforce" -> Mode.ENFORCE;
            default -> {
                LOGGER.warn("Unknown {}='{}'. Fallback to permissive.", ENV_MODE, raw);
                yield Mode.PERMISSIVE;
            }
        };
    }

    public Settings resolveSettings() {
        Mode mode = resolveMode();
        if (mode == Mode.OFF) {
            return Settings.disabled(mode);
        }

        String hmacKeyBase64 = requireNonBlank(
                firstNonBlank(
                        configValue(PROP_HMAC_KEY_B64).orElse(null),
                        configValue(ENV_HMAC_KEY_B64).orElse(null),
                        System.getProperty(PROP_HMAC_KEY_B64),
                        System.getProperty(ENV_HMAC_KEY_B64),
                        System.getenv(ENV_HMAC_KEY_B64)
                ),
                ENV_HMAC_KEY_B64
        );

        byte[] hmacKey = decodeBase64(hmacKeyBase64, ENV_HMAC_KEY_B64);
        if (hmacKey.length < MIN_HMAC_KEY_BYTES) {
            throw new IllegalStateException(ENV_HMAC_KEY_B64 + " must decode to at least "
                    + MIN_HMAC_KEY_BYTES + " bytes");
        }

        String keyId = requireNonBlank(
                firstNonBlank(
                        configValue(PROP_KEY_ID).orElse(null),
                        configValue(ENV_KEY_ID).orElse(null),
                        System.getProperty(PROP_KEY_ID),
                        System.getProperty(ENV_KEY_ID),
                        System.getenv(ENV_KEY_ID)
                ),
                ENV_KEY_ID
        );

        return new Settings(mode, hmacKey, keyId);
    }

    private Optional<String> configValue(String key) {
        Config config = resolveConfig();
        if (config == null) {
            return Optional.empty();
        }
        try {
            return config.getOptionalValue(key, String.class)
                    .map(String::trim)
                    .filter(value -> !value.isEmpty());
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    private Config resolveConfig() {
        try {
            return ConfigProvider.getConfig();
        } catch (IllegalStateException ex) {
            return null;
        }
    }

    private String requireNonBlank(String value, String key) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(key + " is required when document integrity mode is not off");
        }
        return value.trim();
    }

    private byte[] decodeBase64(String value, String key) {
        try {
            return Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(key + " must be valid Base64", ex);
        }
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

    public enum Mode {
        OFF,
        PERMISSIVE,
        ENFORCE
    }

    public static final class Settings {

        private final Mode mode;
        private final byte[] hmacKey;
        private final String keyId;

        private Settings(Mode mode, byte[] hmacKey, String keyId) {
            this.mode = mode;
            this.hmacKey = hmacKey == null ? null : hmacKey.clone();
            this.keyId = keyId;
        }

        static Settings disabled(Mode mode) {
            return new Settings(mode, null, null);
        }

        public Mode getMode() {
            return mode;
        }

        public byte[] getHmacKey() {
            return hmacKey == null ? null : hmacKey.clone();
        }

        public String getKeyId() {
            return keyId;
        }
    }
}
