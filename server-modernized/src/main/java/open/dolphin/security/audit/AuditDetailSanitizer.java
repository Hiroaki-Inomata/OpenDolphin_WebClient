package open.dolphin.security.audit;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Audit details の最小サニタイズと patientId 抽出ヘルパー。
 */
public final class AuditDetailSanitizer {

    private static final String REDACTED = "***";
    private static final Set<String> ALLOWED_TOKEN_KEYS = Set.of(
            "tokenpresent",
            "tokenhash",
            "tokenhashalg",
            "tokenalgorithm");
    private static final Set<String> PATIENT_ID_KEYS = Set.of(
            "patientid",
            "patient_id");

    private AuditDetailSanitizer() {
    }

    public static Map<String, Object> sanitizeDetails(Map<String, Object> details) {
        if (details == null || details.isEmpty()) {
            return details;
        }
        Map<String, Object> sanitized = new LinkedHashMap<>();
        details.forEach((key, value) -> {
            if (key == null) {
                return;
            }
            String normalizedKey = normalizeKey(key);
            if (isSensitiveKey(normalizedKey)) {
                sanitized.put(key, REDACTED);
                return;
            }
            sanitized.put(key, sanitizeValue(value));
        });
        return sanitized;
    }

    public static String resolvePatientId(String explicitPatientId, Map<String, Object> details) {
        String normalized = trimToNull(explicitPatientId);
        if (normalized != null) {
            return normalized;
        }
        return resolvePatientIdFromValue(details);
    }

    private static Object sanitizeValue(Object value) {
        if (value instanceof Map<?, ?> mapValue) {
            Map<String, Object> nested = new LinkedHashMap<>();
            mapValue.forEach((key, nestedValue) -> {
                if (key == null) {
                    return;
                }
                String stringKey = key.toString();
                String normalizedKey = normalizeKey(stringKey);
                if (isSensitiveKey(normalizedKey)) {
                    nested.put(stringKey, REDACTED);
                    return;
                }
                nested.put(stringKey, sanitizeValue(nestedValue));
            });
            return nested;
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> sanitized = new ArrayList<>();
            for (Object item : iterable) {
                sanitized.add(sanitizeValue(item));
            }
            return sanitized;
        }
        if (value != null && value.getClass().isArray()) {
            int length = Array.getLength(value);
            List<Object> sanitized = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                sanitized.add(sanitizeValue(Array.get(value, i)));
            }
            return sanitized;
        }
        return value;
    }

    private static String resolvePatientIdFromValue(Object value) {
        if (value instanceof Map<?, ?> mapValue) {
            for (Map.Entry<?, ?> entry : mapValue.entrySet()) {
                if (entry.getKey() == null) {
                    continue;
                }
                String normalizedKey = normalizeKey(entry.getKey().toString());
                Object nestedValue = entry.getValue();
                if (PATIENT_ID_KEYS.contains(normalizedKey)) {
                    String patientId = normalizePatientIdValue(nestedValue);
                    if (patientId != null) {
                        return patientId;
                    }
                }
                String nested = resolvePatientIdFromValue(nestedValue);
                if (nested != null) {
                    return nested;
                }
            }
            return null;
        }
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                String nested = resolvePatientIdFromValue(item);
                if (nested != null) {
                    return nested;
                }
            }
            return null;
        }
        if (value != null && value.getClass().isArray()) {
            int length = Array.getLength(value);
            for (int i = 0; i < length; i++) {
                String nested = resolvePatientIdFromValue(Array.get(value, i));
                if (nested != null) {
                    return nested;
                }
            }
            return null;
        }
        return null;
    }

    private static String normalizePatientIdValue(Object value) {
        if (value instanceof String text) {
            return trimToNull(text);
        }
        if (value instanceof Number number) {
            return trimToNull(number.toString());
        }
        return null;
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

    private static String normalizeKey(String key) {
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
