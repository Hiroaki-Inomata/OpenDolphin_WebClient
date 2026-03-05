package open.dolphin.rest;

import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import open.dolphin.session.UserServiceBean;

/**
 * REST Web Service
 * サーバー情報の取得
 */
@Path("/serverinfo")
public class ServerInfoResource extends AbstractResource {

    private static final Logger LOGGER = Logger.getLogger(ServerInfoResource.class.getName());

    private static final String JAMRI_CODE = "jamri.code";
    private static final String CLOUD_ZERO = "cloud.zero";
    private static final Set<String> ALLOWED_PROPERTIES = Set.of(JAMRI_CODE, CLOUD_ZERO);

    @Inject
    UserServiceBean userServiceBean;

    @GET
    @Path("/jamri")
    @Produces(MediaType.TEXT_PLAIN)
    public String getJamri(@Context HttpServletRequest servletReq) {
        requireAdminAccess(servletReq);
        return getProperty(JAMRI_CODE);
    }

    @GET
    @Path("/cloud/zero")
    @Produces(MediaType.TEXT_PLAIN)
    public String getServerInfo(@Context HttpServletRequest servletReq) {
        requireAdminAccess(servletReq);
        return getProperty(CLOUD_ZERO);
    }

    String getProperty(String item) {
        if (!ALLOWED_PROPERTIES.contains(item)) {
            return "";
        }
        Properties config = loadCustomProperties();
        return config.getProperty(item, "");
    }

    private void requireAdminAccess(HttpServletRequest request) {
        String actor = request != null ? request.getRemoteUser() : null;
        if (actor == null || actor.isBlank() || userServiceBean == null || !userServiceBean.isAdmin(actor)) {
            throw notFound(request);
        }
    }

    private WebApplicationException notFound(HttpServletRequest request) {
        return restError(request, jakarta.ws.rs.core.Response.Status.NOT_FOUND,
                "not_found", "Requested resource was not found.");
    }

    private Properties loadCustomProperties() {
        Properties config = new Properties();
        String jbossHome = System.getProperty("jboss.home.dir");
        if (jbossHome == null || jbossHome.isBlank()) {
            return config;
        }
        File file = new File(jbossHome, "custom.properties");
        if (!file.exists() || !file.isFile()) {
            return config;
        }
        try (FileInputStream input = new FileInputStream(file);
                InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            config.load(reader);
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "Failed to read custom.properties for serverinfo endpoint.", ex);
        }
        return config;
    }
}
