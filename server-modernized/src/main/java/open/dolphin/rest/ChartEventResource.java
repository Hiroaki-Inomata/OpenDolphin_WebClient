package open.dolphin.rest;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import jakarta.inject.Inject;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import open.dolphin.converter.ChartEventModelConverter;
import open.dolphin.infomodel.ChartEventModel;
import open.dolphin.mbean.ServletContextHolder;
import open.dolphin.session.ChartEventServiceBean;
import open.dolphin.session.support.ChartEventSessionKeys;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * ChartEventResource
 * @author masuda, Masuda Naika
 * 
 * minagawa^ OpenDolphin/Pro のパスに合うように変更点
 * @Path, DISPATCH_URL
 */
@Path("/chartEvent")
public class ChartEventResource extends AbstractResource {
    
    private static final boolean debug = false;
    private static final int CLIENT_UUID_MAX_LENGTH = 64;
    private static final int asyncTimeout = Integer.getInteger("chartEvent.asyncTimeoutMs", 60_000);
    private static final int globalMax = Integer.getInteger("chartEvent.maxSubscribers", 2000);
    private static final int perFacilityMax = Integer.getInteger("chartEvent.maxSubscribersPerFacility", 200);
    private static final int perClientMax = Integer.getInteger("chartEvent.maxSubscribersPerClient", 3);
    
    @Inject
    private ChartEventServiceBean eventServiceBean;
    
    @Inject
    private ServletContextHolder contextHolder;

    @Context
    private HttpServletRequest servletReq;
    
    @GET
    @Path("/subscribe")
    public void subscribe() {

        String fid = requireActorFacility(servletReq);
        String clientUUID = normalizeClientUuid(servletReq.getHeader(ChartEventSessionKeys.CLIENT_UUID));
        if (clientUUID == null) {
            throw restError(servletReq, Response.Status.BAD_REQUEST, "invalid_request",
                    "Header 'clientUUID' is required.");
        }
        if (clientUUID.length() > CLIENT_UUID_MAX_LENGTH) {
            throw restError(servletReq, Response.Status.BAD_REQUEST, "invalid_request",
                    "Header 'clientUUID' must be 64 characters or fewer.");
        }
//minagawa^        
        if (debug) {
            StringBuilder sb = new StringBuilder();
            sb.append(fid).append(":").append(clientUUID);
            sb.append(" did request subscribe");
            debug(sb.toString());
        }
//minagawa$        

        List<AsyncContext> acList = contextHolder.getAsyncContextList();
        final AsyncContext ac;
        int subscribers;
        synchronized (acList) {
            int totalSubscribers = acList.size();
            int facilitySubscribers = countSubscribers(acList, fid, null);
            int clientSubscribers = countSubscribers(acList, fid, clientUUID);
            if (isSubscriberLimitExceeded(totalSubscribers, facilitySubscribers, clientSubscribers)) {
                Map<String, Object> details = new LinkedHashMap<>();
                details.put("globalSubscribers", totalSubscribers);
                details.put("globalMax", globalMax);
                details.put("facilitySubscribers", facilitySubscribers);
                details.put("perFacilityMax", perFacilityMax);
                details.put("clientSubscribers", clientSubscribers);
                details.put("perClientMax", perClientMax);
                throw restError(servletReq, Response.Status.TOO_MANY_REQUESTS, "too_many_requests",
                        "Too many chart event subscribers.", details, null);
            }

            ac = servletReq.startAsync();
            // timeoutを設定
            ac.setTimeout(asyncTimeout);
            // requestにfid, clientUUIDを記録しておく
            ac.getRequest().setAttribute(ChartEventSessionKeys.FACILITY_ID, fid);
            ac.getRequest().setAttribute(ChartEventSessionKeys.CLIENT_UUID, clientUUID);
            contextHolder.addAsyncContext(ac);
            subscribers = acList.size();
        }

//minagawa^
        debug("subscribers count = " + subscribers);
//minagawa$        
        
        ac.addListener(new AsyncListener() {

            private void remove() {
                // JBOSS終了時にぬるぽ？
                try {
                    contextHolder.removeAsyncContext(ac);
                } catch (NullPointerException ex) {
                }
            }

            @Override
            public void onComplete(AsyncEvent event) throws IOException {
                remove();
            }

            @Override
            public void onTimeout(AsyncEvent event) throws IOException {
                remove();
                //event.getThrowable().printStackTrace(System.out);
            }

            @Override
            public void onError(AsyncEvent event) throws IOException {
                remove();
                //event.getThrowable().printStackTrace(System.out);
            }

            @Override
            public void onStartAsync(AsyncEvent event) throws IOException {
            }
        });
    }

    private boolean isSubscriberLimitExceeded(int totalSubscribers, int facilitySubscribers, int clientSubscribers) {
        return totalSubscribers >= globalMax
                || facilitySubscribers >= perFacilityMax
                || clientSubscribers >= perClientMax;
    }

    private int countSubscribers(List<AsyncContext> acList, String facilityId, String clientUUID) {
        int count = 0;
        for (AsyncContext context : acList) {
            String subscribedFacility = readAttribute(context, ChartEventSessionKeys.FACILITY_ID);
            if (facilityId == null || !facilityId.equals(subscribedFacility)) {
                continue;
            }
            if (clientUUID != null) {
                String subscribedClient = readAttribute(context, ChartEventSessionKeys.CLIENT_UUID);
                if (!clientUUID.equals(subscribedClient)) {
                    continue;
                }
            }
            count++;
        }
        return count;
    }

    private String readAttribute(AsyncContext context, String key) {
        if (context == null || key == null) {
            return null;
        }
        try {
            Object value = context.getRequest() != null ? context.getRequest().getAttribute(key) : null;
            return value instanceof String str ? str : null;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private String normalizeClientUuid(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    @PUT
    @Path("/event")
    @Consumes()
    @Produces(MediaType.APPLICATION_JSON)
    public String putChartEvent(String json) throws IOException {
        String fid = requireActorFacility(servletReq);
        
//minagawa^ resteasyを使用
//        ChartEventModel msg = (ChartEventModel)
//                getConverter().fromJson(json, ChartEventModel.class);
//        int cnt = eventServiceBean.processChartEvent(msg);
//        return String.valueOf(cnt);
        debug("putChartEvent did call");
        ObjectMapper mapper = new ObjectMapper();
        // 2013/06/24
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        ChartEventModel msg = mapper.readValue(json, ChartEventModel.class);
        msg.setFacilityId(fid);
        int cnt = eventServiceBean.processChartEvent(msg);
        return String.valueOf(cnt);
//minagawa$        
    }
    
    // 参：きしだのはてな もっとJavaEE6っぽくcometチャットを実装する
    // http://d.hatena.ne.jp/nowokay/20110416/1302978207
    @GET
    @Path("/dispatch")
    @Produces(MediaType.APPLICATION_JSON)
    public ChartEventModelConverter deliverChartEvent() {
        
//minagawa^ resteasyを使用
//        ChartEventModel msg = (ChartEventModel)servletReq.getAttribute(KEY_NAME);
//        String json = getConverter().toJson(msg);
//        return json;
        debug("deliverChartEvent did call");
        ChartEventModel msg = (ChartEventModel)servletReq.getAttribute(ChartEventSessionKeys.EVENT_ATTRIBUTE);
        ChartEventModelConverter conv = new ChartEventModelConverter();
        conv.setModel(msg);
        return conv;
//minagawa$          
    }

    @Override
    protected void debug(String msg) {
        if (debug || DEBUG) {
            super.debug(msg);
        }
    }

}
