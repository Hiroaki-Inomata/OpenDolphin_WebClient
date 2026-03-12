package open.dolphin.rest;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import open.dolphin.mbean.PvtService;
import open.dolphin.orca.transport.RestOrcaTransport;
import open.dolphin.storage.attachment.AttachmentStorageManager;
import open.dolphin.storage.attachment.AttachmentStorageMode;

@Path("/health")
public class OperationsHealthResource extends AbstractResource {

    private static final String DB_PING_SQL = "select 1";

    @PersistenceContext
    private EntityManager em;

    @Inject
    private RestOrcaTransport restOrcaTransport;

    @Inject
    private AttachmentStorageManager attachmentStorageManager;

    @Inject
    private PvtService pvtService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response health() {
        return Response.ok(Map.of(
                "status", "UP",
                "service", "server-modernized"))
                .build();
    }

    @GET
    @Path("/readiness")
    @Produces(MediaType.APPLICATION_JSON)
    public Response readiness() {
        Map<String, Object> checks = new LinkedHashMap<>();
        boolean databaseReady = checkDatabase(checks);
        boolean orcaReady = checkOrca(checks);
        boolean storageReady = checkAttachmentStorage(checks);
        boolean pvtQueueReady = checkPvtQueue(checks);

        boolean overallReady = databaseReady && orcaReady && storageReady && pvtQueueReady;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", overallReady ? "UP" : "DOWN");
        body.put("checks", checks);

        return Response.status(overallReady ? Response.Status.OK : Response.Status.SERVICE_UNAVAILABLE)
                .entity(body)
                .build();
    }

    private boolean checkDatabase(Map<String, Object> checks) {
        Map<String, Object> detail = new LinkedHashMap<>();
        try {
            Object result = em != null ? em.createNativeQuery(DB_PING_SQL).getSingleResult() : null;
            boolean up = result != null;
            detail.put("status", up ? "UP" : "DOWN");
            detail.put("result", result);
            checks.put("database", detail);
            return up;
        } catch (RuntimeException ex) {
            detail.put("status", "DOWN");
            detail.put("error", ex.getClass().getSimpleName());
            detail.put("message", ex.getMessage());
            checks.put("database", detail);
            return false;
        }
    }

    private boolean checkOrca(Map<String, Object> checks) {
        Map<String, Object> detail = new LinkedHashMap<>();
        try {
            String auditSummary = restOrcaTransport != null ? restOrcaTransport.auditSummary() : "orca.host=unknown";
            boolean up = restOrcaTransport != null && !auditSummary.contains("orca.host=unknown");
            detail.put("status", up ? "UP" : "DOWN");
            detail.put("auditSummary", auditSummary);
            checks.put("orca", detail);
            return up;
        } catch (RuntimeException ex) {
            detail.put("status", "DOWN");
            detail.put("error", ex.getClass().getSimpleName());
            detail.put("message", ex.getMessage());
            checks.put("orca", detail);
            return false;
        }
    }

    private boolean checkAttachmentStorage(Map<String, Object> checks) {
        Map<String, Object> detail = new LinkedHashMap<>();
        try {
            AttachmentStorageMode mode = attachmentStorageManager != null ? attachmentStorageManager.getMode() : null;
            boolean up = mode != null;
            detail.put("status", up ? "UP" : "DOWN");
            detail.put("mode", mode != null ? mode.name().toLowerCase(Locale.ROOT) : "unavailable");
            checks.put("attachmentStorage", detail);
            return up;
        } catch (RuntimeException ex) {
            detail.put("status", "DOWN");
            detail.put("error", ex.getClass().getSimpleName());
            detail.put("message", ex.getMessage());
            checks.put("attachmentStorage", detail);
            return false;
        }
    }

    private boolean checkPvtQueue(Map<String, Object> checks) {
        Map<String, Object> detail = new LinkedHashMap<>();
        try {
            Map<String, Object> workerHealth = pvtService != null ? pvtService.workerHealthBody() : Map.of();
            String status = String.valueOf(workerHealth.getOrDefault("status", "DOWN"));
            boolean up = "UP".equalsIgnoreCase(status) || "DISABLED".equalsIgnoreCase(status);
            detail.put("status", up ? "UP" : "DOWN");
            detail.put("workerStatus", status);
            detail.put("reasons", workerHealth.getOrDefault("reasons", java.util.List.of()));
            checks.put("pvtQueue", detail);
            return up;
        } catch (RuntimeException ex) {
            detail.put("status", "DOWN");
            detail.put("error", ex.getClass().getSimpleName());
            detail.put("message", ex.getMessage());
            checks.put("pvtQueue", detail);
            return false;
        }
    }
}
