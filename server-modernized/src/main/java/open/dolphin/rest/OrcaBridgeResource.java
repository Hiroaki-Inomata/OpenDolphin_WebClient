package open.dolphin.rest;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.LinkedHashMap;
import java.util.Map;
import open.dolphin.orca.OrcaGatewayException;
import open.dolphin.orca.transport.OrcaEndpoint;
import open.dolphin.orca.transport.OrcaTransport;
import open.dolphin.orca.transport.OrcaTransportRequest;
import open.dolphin.orca.transport.OrcaTransportResult;
import open.dolphin.rest.orca.AbstractOrcaRestResource;

/**
 * JSON bridge for ORCA legacy XML operations used by the web client.
 */
@Path("/api/v1/orca/bridge")
public class OrcaBridgeResource extends AbstractResource {

    @Inject
    OrcaTransport orcaTransport;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response invoke(@Context HttpServletRequest request, String payload) {
        JsonNode root;
        try {
            root = readJsonTree(payload);
        } catch (Exception ex) {
            throw new BadRequestException("Invalid JSON payload");
        }
        if (root == null || root.isMissingNode()) {
            throw new BadRequestException("Invalid JSON payload");
        }
        String endpointName = readText(root, "endpoint");
        if (endpointName == null || endpointName.isBlank()) {
            throw new BadRequestException("endpoint is required");
        }
        OrcaEndpoint endpoint;
        try {
            endpoint = OrcaEndpoint.valueOf(endpointName.trim());
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Unknown endpoint: " + endpointName);
        }
        if (orcaTransport == null) {
            throw new OrcaGatewayException("ORCA transport is not available");
        }

        String classCode = readText(root, "classCode");
        String query = readText(root, "query");
        String xmlPayload = readText(root, "payload");
        String defaultPayload = readText(root, "defaultPayload");
        if (defaultPayload == null || defaultPayload.isBlank()) {
            defaultPayload = buildDefaultPayload(endpoint, classCode);
        }
        String resolvedPayload = (xmlPayload == null || xmlPayload.isBlank()) ? defaultPayload : xmlPayload;

        OrcaTransportResult result;
        if ("GET".equalsIgnoreCase(endpoint.getMethod())) {
            result = orcaTransport.invokeDetailed(endpoint, OrcaTransportRequest.get(query));
        } else {
            if ((resolvedPayload == null || resolvedPayload.isBlank()) && endpoint.requiresBody()) {
                throw new BadRequestException("payload is required for endpoint: " + endpoint.name());
            }
            if (resolvedPayload == null) {
                resolvedPayload = "";
            }
            resolvedPayload = OrcaApiProxySupport.applyQueryMeta(resolvedPayload, endpoint, classCode);
            result = orcaTransport.invokeDetailed(endpoint, OrcaTransportRequest.post(resolvedPayload));
        }

        String runId = AbstractOrcaRestResource.resolveRunIdValue(request);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("endpoint", endpoint.name());
        response.put("httpStatus", result != null ? result.getStatus() : 500);
        response.put("contentType", result != null ? result.getContentType() : MediaType.APPLICATION_XML);
        response.put("body", result != null ? result.getBody() : "");
        response.put("runId", runId);
        return Response.ok(response, MediaType.APPLICATION_JSON_TYPE).build();
    }

    private String buildDefaultPayload(OrcaEndpoint endpoint, String classCode) {
        if (endpoint == null) {
            return null;
        }
        switch (endpoint) {
            case SYSTEM_MANAGEMENT_LIST:
                return "<data><system01lstv2req type=\"record\"><Request_Number type=\"string\">"
                        + ((classCode == null || classCode.isBlank()) ? "02" : classCode.trim())
                        + "</Request_Number></system01lstv2req></data>";
            case MANAGE_USERS:
                return "<data><manageusersreq type=\"record\"><Request_Number type=\"string\">01</Request_Number></manageusersreq></data>";
            case INSURANCE_PROVIDER:
                return "<data><insprogetreq type=\"record\"></insprogetreq></data>";
            case ACCEPTANCE_LIST:
                java.time.LocalDate today = java.time.LocalDate.now();
                java.time.LocalTime now = java.time.LocalTime.now().withNano(0);
                return "<data><acceptlstv2req type=\"record\"><Acceptance_Date type=\"string\">"
                        + today + "</Acceptance_Date><Acceptance_Time type=\"string\">" + now
                        + "</Acceptance_Time></acceptlstv2req></data>";
            default:
                return null;
        }
    }

    private String readText(JsonNode root, String key) {
        JsonNode node = root.get(key);
        if (node == null || node.isNull()) {
            return null;
        }
        return node.asText(null);
    }
}
