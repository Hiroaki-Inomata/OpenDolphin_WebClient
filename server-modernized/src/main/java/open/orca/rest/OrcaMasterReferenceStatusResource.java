package open.orca.rest;

import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;
import open.dolphin.rest.masterupdate.MasterUpdateService;
import open.dolphin.rest.orca.AbstractOrcaRestResource;

/**
 * Read-only status endpoint for reference dataset update states.
 */
@Path("/orca/master/reference")
public class OrcaMasterReferenceStatusResource {

    @Inject
    private MasterUpdateService masterUpdateService;

    @GET
    @Path("/status")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getStatus(@Context HttpServletRequest request) {
        String runId = AbstractOrcaRestResource.resolveRunIdValue(request);
        Map<String, Object> body = masterUpdateService.getReferenceStatus(runId);
        return Response.ok(body).header("x-run-id", runId).build();
    }
}
