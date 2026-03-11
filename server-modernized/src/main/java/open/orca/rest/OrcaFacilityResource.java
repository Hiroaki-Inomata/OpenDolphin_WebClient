package open.orca.rest;

import jakarta.ejb.EJB;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/orca")
public class OrcaFacilityResource {

    @EJB
    private OrcaResource orcaResource;

    @GET
    @Path("/facilitycode")
    @Produces(MediaType.TEXT_PLAIN)
    public String getFacilityCodeBy1001() {
        return orcaResource.getFacilityCodeBy1001();
    }

    @GET
    @Path("/deptinfo")
    @Produces({MediaType.TEXT_PLAIN, MediaType.APPLICATION_JSON})
    public Response getDeptInfo(@Context HttpServletRequest request) {
        return orcaResource.getDeptInfo(request);
    }
}
