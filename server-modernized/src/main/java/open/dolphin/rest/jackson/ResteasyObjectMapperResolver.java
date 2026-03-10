package open.dolphin.rest.jackson;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.ext.ContextResolver;
import jakarta.ws.rs.ext.Provider;
import open.dolphin.rest.AbstractResource;

/**
 * Registers a Jackson mapper with JavaTime support for RESTEasy JSON binding.
 */
@Provider
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
public class ResteasyObjectMapperResolver implements ContextResolver<ObjectMapper> {

    @Inject
    ObjectMapper mapper;
    private static final String ORCA_DTO_PACKAGE = "open.dolphin.rest.dto.orca";

    @Override
    public ObjectMapper getContext(Class<?> type) {
        if (type == null) {
            return null;
        }
        Package pkg = type.getPackage();
        if (pkg == null || !pkg.getName().startsWith(ORCA_DTO_PACKAGE)) {
            return null;
        }
        return mapper != null ? mapper : AbstractResource.getSerializeMapper();
    }
}
