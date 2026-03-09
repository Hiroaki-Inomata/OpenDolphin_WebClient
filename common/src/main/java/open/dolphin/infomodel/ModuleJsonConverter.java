package open.dolphin.infomodel;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility to serialize/deserialize module payloads with polymorphic typing.
 */
public final class ModuleJsonConverter {

    private static final Logger LOG = LoggerFactory.getLogger(ModuleJsonConverter.class);

    private static final ModuleJsonConverter INSTANCE = new ModuleJsonConverter();

    private final ObjectMapper typedMapper;

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
            LOG.warn("Failed to serialize module payload to beanJson. type={}"
                    , payload.getClass().getName(), e);
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
     * ModuleModel から payload を復元する。
     */
    public Object decode(ModuleModel module) {
        if (module == null) {
            return null;
        }
        return deserialize(module.getBeanJson());
    }
}
