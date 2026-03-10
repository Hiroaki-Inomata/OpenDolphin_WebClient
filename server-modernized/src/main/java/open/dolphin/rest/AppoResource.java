package open.dolphin.rest;

import java.io.IOException;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import open.dolphin.infomodel.AppoList;
import open.dolphin.session.AppoServiceBean;

/**
 * REST Web Service
 *
 * @author Kazushi Minagawa, Digital Globe, Inc.
 */
@Path("/appo")
public class AppoResource extends AbstractResource {
    
    @Inject
    private AppoServiceBean appoServiceBean;

    /** Creates a new instance of AppoResource */
    public AppoResource() {
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.TEXT_PLAIN)
    public String putXml(@Context HttpServletRequest request, String json) throws IOException {
        String fid = requireActorFacility(request);

        AppoList list = readJson(json, AppoList.class);
        
        int count = appoServiceBean.putAppointmentsForFacility(fid, list.getList());
        if (count == 0 && list.getList() != null && !list.getList().isEmpty()) {
            throw new NotFoundException("Appointment not found");
        }
        String cntStr = String.valueOf(count);
        debug(cntStr);

        return cntStr;
    }

}
