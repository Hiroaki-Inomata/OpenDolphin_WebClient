package open.dolphin.security.integrity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class CanonicalJson {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private CanonicalJson() {
    }

    public static String canonicalize(String json) {
        if (json == null || json.isBlank()) {
            return "";
        }
        try {
            JsonNode node = OBJECT_MAPPER.readTree(json);
            return OBJECT_MAPPER.writeValueAsString(sort(node));
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Invalid JSON payload", ex);
        }
    }

    public static byte[] canonicalBytes(String json) {
        return canonicalize(json).getBytes(StandardCharsets.UTF_8);
    }

    private static JsonNode sort(JsonNode node) {
        if (node == null || node.isNull() || node.isValueNode()) {
            return node;
        }
        if (node.isArray()) {
            ArrayNode sorted = JsonNodeFactory.instance.arrayNode();
            for (JsonNode child : node) {
                sorted.add(sort(child));
            }
            return sorted;
        }
        ObjectNode sorted = JsonNodeFactory.instance.objectNode();
        List<String> fieldNames = new ArrayList<>();
        node.fieldNames().forEachRemaining(fieldNames::add);
        fieldNames.sort(Comparator.naturalOrder());
        for (String fieldName : fieldNames) {
            sorted.set(fieldName, sort(node.get(fieldName)));
        }
        return sorted;
    }
}
