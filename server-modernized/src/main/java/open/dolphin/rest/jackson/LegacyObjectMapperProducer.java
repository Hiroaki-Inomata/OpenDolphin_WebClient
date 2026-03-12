package open.dolphin.rest.jackson;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;
import open.dolphin.rest.AbstractResource;

/**
 * CDI producer that supplies a legacy-compatible {@link ObjectMapper}.
 * Consolidates serialization defaults from {@link AbstractResource#getSerializeMapper()}
 * and ADM系が必要とするデシリアライズ設定をひとつにまとめる。
 */
@ApplicationScoped
public class LegacyObjectMapperProducer {

    @Produces
    @Dependent
    public ObjectMapper provideLegacyAwareMapper() {
        return AbstractResource.getSerializeMapper();
    }
}
