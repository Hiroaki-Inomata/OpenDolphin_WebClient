package open.dolphin.rest.support;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Objects;

public final class LegacyJsonSupport {

    private LegacyJsonSupport() {
    }

    public static ObjectMapper resolveMapper(ObjectMapper injectedMapper) {
        return Objects.requireNonNull(injectedMapper, "ObjectMapper must be injected");
    }

    public static <T> T readBody(String json, Class<T> type, ObjectMapper injectedMapper) throws IOException {
        return resolveMapper(injectedMapper)
                .copy()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .readValue(json, type);
    }
}
