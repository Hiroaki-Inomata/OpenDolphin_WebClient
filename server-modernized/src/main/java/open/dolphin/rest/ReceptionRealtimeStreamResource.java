package open.dolphin.rest;

import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.sse.Sse;
import jakarta.ws.rs.sse.SseEventSink;
import java.util.HashMap;
import java.util.Map;
import open.dolphin.session.framework.SessionOperation;

/**
 * Reception 一覧のリアルタイム更新通知（SSE）。
 */
@Path("/realtime/reception")
@SessionOperation
public class ReceptionRealtimeStreamResource extends AbstractResource {

    @Inject
    private ReceptionRealtimeSseSupport sseSupport;

    @Context
    private HttpServletRequest servletRequest;

    @GET
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public void subscribe(@Context SseEventSink eventSink,
            @Context Sse sse,
            @HeaderParam("Last-Event-ID") String lastEventId) {

        if (eventSink == null || eventSink.isClosed()) {
            return;
        }

        try {
            String remoteUser = servletRequest != null ? servletRequest.getRemoteUser() : null;
            if (remoteUser == null || remoteUser.isBlank()) {
                eventSink.close();
                throw restError(servletRequest, Response.Status.UNAUTHORIZED,
                        "remote_user_missing", "Authenticated user is required");
            }
            String facilityId = getRemoteFacility(remoteUser);
            if (facilityId == null || facilityId.isBlank()) {
                eventSink.close();
                throw restError(servletRequest, Response.Status.UNAUTHORIZED,
                        "facility_missing", "Remote user must belong to a facility");
            }
            if (sse == null || sseSupport == null) {
                eventSink.close();
                throw restError(servletRequest, Response.Status.SERVICE_UNAVAILABLE,
                        "sse_unavailable", "Reception realtime stream is not available");
            }
            sseSupport.register(facilityId, sse, eventSink, lastEventId);
        } catch (WebApplicationException ex) {
            throw ex;
        } catch (Exception ex) {
            eventSink.close();
            Map<String, Object> details = new HashMap<>();
            details.put("reason", ex.getClass().getSimpleName());
            throw restError(servletRequest, Response.Status.SERVICE_UNAVAILABLE,
                    "reception_realtime_unavailable", "Reception realtime stream unavailable", details, ex);
        }
    }
}
