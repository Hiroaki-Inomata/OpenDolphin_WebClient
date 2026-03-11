package open.dolphin.infomodel;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Utility to serialize/deserialize module payloads with polymorphic typing.
 * Current module payloads are restored from beanJson only.
 */
public final class ModuleJsonConverter {

    private static final Logger LOG = LoggerFactory.getLogger(ModuleJsonConverter.class);
    private static final int CURRENT_SCHEMA_VERSION = 1;
    private static final String FIELD_SCHEMA_VERSION = "schemaVersion";
    private static final String FIELD_MODULE_TYPE = "moduleType";
    private static final String FIELD_PAYLOAD_JSON = "payloadJson";
    private static final String FIELD_PAYLOAD_HASH = "payloadHash";
    private static final String MODULE_TYPE_MED_ORDER = "medOrder";
    private static final String MODULE_TYPE_PROGRESS_COURSE = "progressCourse";

    private static final ModuleJsonConverter INSTANCE = new ModuleJsonConverter();

    private final ObjectMapper typedMapper;
    private final ObjectMapper plainMapper;

    private ModuleJsonConverter() {
        PolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("open.dolphin")
                .allowIfSubType("java.util")
                .allowIfSubType("java.time")
                // Array type ids use "[Lcom.example.Type;" so add explicit prefixes for module payload arrays.
                .allowIfSubType("[Lopen.dolphin")
                .allowIfSubType("[Ljava.util")
                .allowIfSubType("[Ljava.time")
                .build();

        typedMapper = JsonMapper.builder()
                .activateDefaultTyping(ptv, ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .findAndAddModules()
                .build();

        plainMapper = JsonMapper.builder()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .findAndAddModules()
                .build();
    }

    public static ModuleJsonConverter getInstance() {
        return INSTANCE;
    }

    /**
     * モジュールの payload を JSON へ直列化する。失敗時は null を返す。
     */
    public String serialize(Object payload) {
        if (payload == null) {
            return null;
        }
        try {
            return typedMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            LOG.warn("Failed to serialize module payload to beanJson. type={}", payload.getClass().getName(), e);
            return null;
        }
    }

    /**
     * beanJson を復元する。復元失敗時は null を返す。
     */
    public Object deserialize(String json) {
        // String#isBlank は Java 11 以降のため、Java 8 互換ビルドでは trim+isEmpty で代替する。
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        try {
            return typedMapper.readValue(json, Object.class);
        } catch (Exception e) {
            LOG.warn("Failed to deserialize module payload from beanJson.", e);
            return null;
        }
    }

    /**
     * ModuleModel を保存向け JSON へ変換する。
     * medOrder / progressCourse は versioned envelope を採用し、
     * それ以外は従来の typed JSON を返す。
     */
    public String encode(ModuleModel module) {
        if (module == null || module.getModel() == null) {
            return null;
        }
        String legacyTypedJson = serialize(module.getModel());
        if (legacyTypedJson == null) {
            return null;
        }
        String moduleType = extractModuleType(module);
        if (!isVersionedType(moduleType)) {
            return legacyTypedJson;
        }
        try {
            ObjectNode envelope = plainMapper.createObjectNode();
            envelope.put(FIELD_SCHEMA_VERSION, CURRENT_SCHEMA_VERSION);
            envelope.put(FIELD_MODULE_TYPE, moduleType);
            envelope.put(FIELD_PAYLOAD_JSON, legacyTypedJson);
            envelope.put(FIELD_PAYLOAD_HASH, sha256Hex(legacyTypedJson));
            return plainMapper.writeValueAsString(envelope);
        } catch (Exception e) {
            LOG.warn("Failed to encode versioned module payload. type={}", moduleType, e);
            return legacyTypedJson;
        }
    }

    /**
     * JSON文字列から module payload を復元する。
     * versioned envelope なら payloadJson を優先し、従来形式も継続読取する。
     */
    public Object decodeRaw(String json) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        String payloadJson = extractVersionedPayload(json);
        if (payloadJson != null) {
            return deserialize(payloadJson);
        }
        return deserialize(json);
    }

    /**
     * ModuleModel から payload を復元する。current path は beanJson のみを扱う。
     */
    public Object decode(ModuleModel module) {
        if (module == null) {
            return null;
        }
        if (module.getBeanJson() == null || module.getBeanJson().trim().isEmpty()) {
            LOG.warn("Module payload beanJson is missing. moduleId={}", module.getId());
            return null;
        }
        return decodeRaw(module.getBeanJson());
    }

    private boolean isVersionedType(String moduleType) {
        return MODULE_TYPE_MED_ORDER.equals(moduleType) || MODULE_TYPE_PROGRESS_COURSE.equals(moduleType);
    }

    private String extractModuleType(ModuleModel module) {
        if (module.getModuleInfoBean() == null) {
            return null;
        }
        return module.getModuleInfoBean().getEntity();
    }

    private String extractVersionedPayload(String json) {
        try {
            JsonNode root = plainMapper.readTree(json);
            JsonNode schemaVersionNode = root.get(FIELD_SCHEMA_VERSION);
            JsonNode payloadNode = root.get(FIELD_PAYLOAD_JSON);
            if (schemaVersionNode == null || payloadNode == null || payloadNode.isNull()) {
                return null;
            }
            return payloadNode.asText();
        } catch (Exception e) {
            return null;
        }
    }

    private String sha256Hex(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hashed.length * 2);
            for (byte b : hashed) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            LOG.warn("Failed to hash module payload", e);
            return null;
        }
    }
}
