package open.dolphin.security.xml;

import java.beans.XMLDecoder;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Safe wrapper for XMLDecoder with input and class loading restrictions.
 */
public final class SafeXmlDecoder {

    public static final int MAX_INPUT_BYTES = 1024 * 1024;

    private static final List<String> DENYLIST_PREFIXES = List.of(
            "java.lang.Runtime",
            "java.lang.ProcessBuilder",
            "java.lang.Class",
            "java.lang.ClassLoader",
            "java.lang.reflect.",
            "javax.script.",
            "java.rmi.",
            "org.codehaus.groovy."
    );

    private static final List<String> ALLOWLIST_PREFIXES = List.of(
            "open.dolphin.",
            "open.orca.",
            "java.lang.",
            "java.beans.",
            "java.util.",
            "java.time.",
            "java.math."
    );

    private static final String[] FORBIDDEN_XML_MARKERS = {
            "<!DOCTYPE",
            "<!ENTITY"
    };

    private SafeXmlDecoder() {
    }

    public static Object decode(byte[] bytes) {
        validateInput(bytes);
        ClassLoader parent = Thread.currentThread().getContextClassLoader();
        if (parent == null) {
            parent = SafeXmlDecoder.class.getClassLoader();
        }
        ClassLoader allowlistLoader = new AllowlistClassLoader(parent);
        try (XMLDecoder decoder = new XMLDecoder(
                new BufferedInputStream(new ByteArrayInputStream(bytes)),
                null,
                ex -> {
                    throw new IllegalArgumentException("XML decode failed", ex);
                },
                allowlistLoader)) {
            Object decoded = decoder.readObject();
            assertAllowlistedDecodedObject(decoded);
            return decoded;
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("XML decode failed", ex);
        }
    }

    public static <T> T decode(byte[] bytes, Class<T> targetType) {
        Object decoded = decode(bytes);
        if (decoded == null) {
            return null;
        }
        if (!targetType.isInstance(decoded)) {
            throw new IllegalArgumentException("Decoded type mismatch: expected " + targetType.getName()
                    + " but got " + decoded.getClass().getName());
        }
        return targetType.cast(decoded);
    }

    private static void validateInput(byte[] bytes) {
        if (bytes == null) {
            throw new IllegalArgumentException("XML input must not be null");
        }
        if (bytes.length == 0) {
            throw new IllegalArgumentException("XML input must not be empty");
        }
        if (bytes.length > MAX_INPUT_BYTES) {
            throw new IllegalArgumentException("XML input exceeds maximum allowed size");
        }
        String content = new String(bytes, StandardCharsets.UTF_8).toUpperCase(Locale.ROOT);
        for (String marker : FORBIDDEN_XML_MARKERS) {
            if (content.contains(marker)) {
                throw new IllegalArgumentException("XML contains forbidden declaration: " + marker);
            }
        }
    }

    private static final class AllowlistClassLoader extends ClassLoader {

        private AllowlistClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            ensureAllowedClassName(name);
            return super.loadClass(name, resolve);
        }
    }

    private static void assertAllowlistedDecodedObject(Object decoded) {
        if (decoded == null) {
            return;
        }
        List<Object> stack = new ArrayList<>();
        stack.add(decoded);
        while (!stack.isEmpty()) {
            Object current = stack.remove(stack.size() - 1);
            if (current == null) {
                continue;
            }
            String className = current.getClass().getName();
            ensureAllowedClassName(className);
            if (current instanceof Iterable<?> iterable) {
                for (Object value : iterable) {
                    stack.add(value);
                }
            } else if (current instanceof Map<?, ?> map) {
                for (Object key : map.keySet()) {
                    stack.add(key);
                }
                for (Object value : map.values()) {
                    stack.add(value);
                }
            } else if (current.getClass().isArray() && !current.getClass().getComponentType().isPrimitive()) {
                Object[] array = (Object[]) current;
                for (Object value : array) {
                    stack.add(value);
                }
            }
        }
    }

    private static void ensureAllowedClassName(String className) {
        String normalized = normalizeClassName(className);
        for (String denied : DENYLIST_PREFIXES) {
            if (normalized.startsWith(denied)) {
                throw new IllegalArgumentException("Denied XMLDecoder class: " + normalized);
            }
        }
        if (isPrimitiveTypeName(normalized)) {
            return;
        }
        for (String allowed : ALLOWLIST_PREFIXES) {
            if (normalized.startsWith(allowed)) {
                return;
            }
        }
        throw new IllegalArgumentException("Class not allowlisted for XMLDecoder: " + normalized);
    }

    private static String normalizeClassName(String name) {
        if (name == null) {
            return "";
        }
        String normalized = name;
        while (normalized.startsWith("[")) {
            normalized = normalized.substring(1);
        }
        if (normalized.startsWith("L") && normalized.endsWith(";")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        return normalized;
    }

    private static boolean isPrimitiveTypeName(String name) {
        return "byte".equals(name)
                || "short".equals(name)
                || "int".equals(name)
                || "long".equals(name)
                || "float".equals(name)
                || "double".equals(name)
                || "boolean".equals(name)
                || "char".equals(name)
                || "void".equals(name)
                || "B".equals(name)
                || "S".equals(name)
                || "I".equals(name)
                || "J".equals(name)
                || "F".equals(name)
                || "D".equals(name)
                || "Z".equals(name)
                || "C".equals(name)
                || "V".equals(name);
    }
}
