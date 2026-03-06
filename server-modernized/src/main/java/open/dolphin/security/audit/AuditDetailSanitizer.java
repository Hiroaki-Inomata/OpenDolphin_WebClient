package open.dolphin.security.audit;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Audit details の allowlist サニタイズと patientId 正規化ヘルパー。
 */
public final class AuditDetailSanitizer {

    private static final Set<String> ALLOWED_TOKEN_KEYS = Set.of(
            "tokenpresent",
            "tokenhash",
            "tokenhashalg",
            "tokenalgorithm");

    private AuditDetailSanitizer() {
    }

    public static Map<String, Object> sanitizeDetails(Map<String, Object> details) {
        return sanitizeDetails(null, details);
    }

    public static Map<String, Object> sanitizeDetails(String action, Map<String, Object> details) {
        if (details == null || details.isEmpty()) {
            return details;
        }
        Map<String, Object> sanitized = new LinkedHashMap<>();
        details.forEach((key, value) -> {
            if (key == null) {
                return;
            }
            String normalizedKey = normalizeKey(key);
            if (!AuditEventAllowlist.isAllowed(action, normalizedKey) || isSensitiveKey(normalizedKey)) {
                return;
            }
            Object sanitizedValue = sanitizeValue(action, value);
            if (sanitizedValue == null) {
                return;
            }
            if (sanitizedValue instanceof Map<?, ?> nested && nested.isEmpty()) {
                return;
            }
            if (sanitizedValue instanceof List<?> list && list.isEmpty()) {
                return;
            }
            sanitized.put(key, sanitizedValue);
        });
        return sanitized;
    }

    public static String resolvePatientId(String explicitPatientId, Map<String, Object> details) {
        return trimToNull(explicitPatientId);
    }

    private static Object sanitizeValue(String action, Object value) {
        if (value instanceof Map<?, ?> mapValue) {
            Map<String, Object> nested = new LinkedHashMap<>();
            mapValue.forEach((key, nestedValue) -> {
                if (key == null) {
                    return;
                }
                String stringKey = key.toString();
                String normalizedKey = normalizeKey(stringKey);
                if (!AuditEventAllowlist.isAllowed(action, normalizedKey) || isSensitiveKey(normalizedKey)) {
                    return;
                }
                Object sanitizedValue = sanitizeValue(action, nestedValue);
                if (sanitizedValue == null) {
                    return;
                }
                if (sanitizedValue instanceof Map<?, ?> sanitizedMap && sanitizedMap.isEmpty()) {
                    return;
                }
                if (sanitizedValue instanceof List<?> sanitizedList && sanitizedList.isEmpty()) {
                    return;
                }
                nested.put(stringKey, sanitizedValue);
            });
            return nested;
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> sanitized = new ArrayList<>();
            for (Object item : iterable) {
                Object sanitizedItem = sanitizeValue(action, item);
                if (sanitizedItem != null) {
                    sanitized.add(sanitizedItem);
                }
            }
            return sanitized;
        }
        if (value != null && value.getClass().isArray()) {
            int length = Array.getLength(value);
            List<Object> sanitized = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                Object sanitizedItem = sanitizeValue(action, Array.get(value, i));
                if (sanitizedItem != null) {
                    sanitized.add(sanitizedItem);
                }
            }
            return sanitized;
        }
        return value;
    }

    private static boolean isSensitiveKey(String normalizedKey) {
        if (normalizedKey == null || normalizedKey.isBlank()) {
            return false;
        }
        if (ALLOWED_TOKEN_KEYS.contains(normalizedKey)) {
            return false;
        }
        if (normalizedKey.contains("password")
                || normalizedKey.contains("authorization")
                || normalizedKey.contains("cookie")
                || normalizedKey.contains("secret")) {
            return true;
        }
        return normalizedKey.contains("token");
    }

    static String normalizeKey(String key) {
        if (key == null) {
            return "";
        }
        String lower = key.trim().toLowerCase(Locale.ROOT);
        StringBuilder normalized = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_') {
                normalized.append(c);
            }
        }
        return normalized.toString();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
