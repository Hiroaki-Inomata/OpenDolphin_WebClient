package open.dolphin.rest;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import open.dolphin.audit.AuditEventEnvelope;
import open.dolphin.orca.OrcaGatewayException;
import open.dolphin.orca.transport.OrcaEndpoint;
import open.dolphin.orca.transport.OrcaTransport;
import open.dolphin.orca.transport.OrcaTransportRequest;
import open.dolphin.orca.transport.OrcaTransportResult;
import open.dolphin.orca.transport.RestOrcaTransport;
import open.dolphin.security.audit.AuditEventPayload;
import open.dolphin.security.audit.SessionAuditDispatcher;
import jakarta.ws.rs.core.StreamingOutput;
import open.dolphin.rest.orca.AbstractOrcaRestResource;

/**
 * ORCA report endpoints (prescription/karte/report) and blobapi proxy.
 */
@Path("/")
public class OrcaReportResource extends AbstractResource {

    private static final Logger LOGGER = Logger.getLogger(OrcaReportResource.class.getName());
    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration DEFAULT_BLOB_DEADLINE = Duration.ofSeconds(20);
    private static final int BLOB_RETRY_MAX = 3;
    private static final long BLOB_RETRY_BACKOFF_MS = 300L;

    @Inject
    OrcaTransport orcaTransport;

    @Inject
    RestOrcaTransport restOrcaTransport;

    @Inject
    SessionAuditDispatcher sessionAuditDispatcher;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(DEFAULT_CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    @POST
    @Path("/api01rv2/prescriptionv2")
    @Consumes({MediaType.APPLICATION_XML, MediaType.TEXT_XML})
    @Produces({MediaType.APPLICATION_JSON, "application/pdf"})
    public Response postPrescription(@Context HttpServletRequest request, String payload) {
        return respondReport(request, OrcaEndpoint.PRESCRIPTION_REPORT,
                "/api01rv2/prescriptionv2", payload, "ORCA_REPORT_PRESCRIPTION");
    }

    @POST
    @Path("/orca/prescriptionv2")
    @Consumes({MediaType.APPLICATION_XML, MediaType.TEXT_XML})
    @Produces({MediaType.APPLICATION_JSON, "application/pdf"})
    public Response postPrescriptionWithOrcaPrefix(@Context HttpServletRequest request, String payload) {
        return respondReport(request, OrcaEndpoint.PRESCRIPTION_REPORT,
                "/orca/prescriptionv2", payload, "ORCA_REPORT_PRESCRIPTION");
    }

    @POST
    @Path("/api/orca/prescriptionv2")
    @Consumes({MediaType.APPLICATION_XML, MediaType.TEXT_XML})
    @Produces({MediaType.APPLICATION_JSON, "application/pdf"})
    public Response postPrescriptionWithApiOrcaPrefix(@Context HttpServletRequest request, String payload) {
        return respondReport(request, OrcaEndpoint.PRESCRIPTION_REPORT,
                "/api/orca/prescriptionv2", payload, "ORCA_REPORT_PRESCRIPTION");
    }

    @POST
    @Path("/api/api01rv2/prescriptionv2")
    @Consumes({MediaType.APPLICATION_XML, MediaType.TEXT_XML})
    @Produces({MediaType.APPLICATION_JSON, "application/pdf"})
    public Response postPrescriptionWithApiPrefix(@Context HttpServletRequest request, String payload) {
        return respondReport(request, OrcaEndpoint.PRESCRIPTION_REPORT,
                "/api/api01rv2/prescriptionv2", payload, "ORCA_REPORT_PRESCRIPTION");
    }

    @POST
    @Path("/api01rv2/medicinenotebookv2")
    @Consumes({MediaType.APPLICATION_XML, MediaType.TEXT_XML})
    @Produces({MediaType.APPLICATION_JSON, "application/pdf"})
    public Response postMedicineNotebook(@Context HttpServletRequest request, String payload) {
        return respondReport(request, OrcaEndpoint.MEDICINE_NOTEBOOK_REPORT,
                "/api01rv2/medicinenotebookv2", payload, "ORCA_REPORT_MEDICINE_NOTEBOOK");
    }

    @POST
    @Path("/orca/medicinenotebookv2")
    @Consumes({MediaType.APPLICATION_XML, MediaType.TEXT_XML})
    @Produces({MediaType.APPLICATION_JSON, "application/pdf"})
    public Response postMedicineNotebookWithOrcaPrefix(@Context HttpServletRequest request, String payload) {
        return respondReport(request, OrcaEndpoint.MEDICINE_NOTEBOOK_REPORT,
                "/orca/medicinenotebookv2", payload, "ORCA_REPORT_MEDICINE_NOTEBOOK");
    }

    @POST
    @Path("/api/orca/medicinenotebookv2")
    @Consumes({MediaType.APPLICATION_XML, MediaType.TEXT_XML})
    @Produces({MediaType.APPLICATION_JSON, "application/pdf"})
    public Response postMedicineNotebookWithApiOrcaPrefix(@Context HttpServletRequest request, String payload) {
        return respondReport(request, OrcaEndpoint.MEDICINE_NOTEBOOK_REPORT,
                "/api/orca/medicinenotebookv2", payload, "ORCA_REPORT_MEDICINE_NOTEBOOK");
    }

    @POST
    @Path("/api/api01rv2/medicinenotebookv2")
    @Consumes({MediaType.APPLICATION_XML, MediaType.TEXT_XML})
    @Produces({MediaType.APPLICATION_JSON, "application/pdf"})
    public Response postMedicineNotebookWithApiPrefix(@Context HttpServletRequest request, String payload) {
        return respondReport(request, OrcaEndpoint.MEDICINE_NOTEBOOK_REPORT,
                "/api/api01rv2/medicinenotebookv2", payload, "ORCA_REPORT_MEDICINE_NOTEBOOK");
    }

    @POST
    @Path("/api01rv2/karteno1v2")
    @Consumes({MediaType.APPLICATION_XML, MediaType.TEXT_XML})
    @Produces({MediaType.APPLICATION_JSON, "application/pdf"})
    public Response postKarteno1(@Context HttpServletRequest request, String payload) {
        return respondReport(request, OrcaEndpoint.KARTENO1_REPORT,
                "/api01rv2/karteno1v2", payload, "ORCA_REPORT_KARTENO1");
    }

    @POST
    @Path("/orca/karteno1v2")
    @Consumes({MediaType.APPLICATION_XML, MediaType.TEXT_XML})
    @Produces({MediaType.APPLICATION_JSON, "application/pdf"})
    public Response postKarteno1WithOrcaPrefix(@Context HttpServletRequest request, String payload) {
        return respondReport(request, OrcaEndpoint.KARTENO1_REPORT,
                "/orca/karteno1v2", payload, "ORCA_REPORT_KARTENO1");
    }

    @POST
    @Path("/api/orca/karteno1v2")
    @Consumes({MediaType.APPLICATION_XML, MediaType.TEXT_XML})
    @Produces({MediaType.APPLICATION_JSON, "application/pdf"})
    public Response postKarteno1WithApiOrcaPrefix(@Context HttpServletRequest request, String payload) {
        return respondReport(request, OrcaEndpoint.KARTENO1_REPORT,
                "/api/orca/karteno1v2", payload, "ORCA_REPORT_KARTENO1");
    }

    @POST
    @Path("/api/api01rv2/karteno1v2")
    @Consumes({MediaType.APPLICATION_XML, MediaType.TEXT_XML})
    @Produces({MediaType.APPLICATION_JSON, "application/pdf"})
    public Response postKarteno1WithApiPrefix(@Context HttpServletRequest request, String payload) {
        return respondReport(request, OrcaEndpoint.KARTENO1_REPORT,
                "/api/api01rv2/karteno1v2", payload, "ORCA_REPORT_KARTENO1");
    }

    @POST
    @Path("/api01rv2/karteno3v2")
    @Consumes({MediaType.APPLICATION_XML, MediaType.TEXT_XML})
    @Produces({MediaType.APPLICATION_JSON, "application/pdf"})
    public Response postKarteno3(@Context HttpServletRequest request, String payload) {
        return respondReport(request, OrcaEndpoint.KARTENO3_REPORT,
                "/api01rv2/karteno3v2", payload, "ORCA_REPORT_KARTENO3");
    }

    @POST
    @Path("/orca/karteno3v2")
    @Consumes({MediaType.APPLICATION_XML, MediaType.TEXT_XML})
    @Produces({MediaType.APPLICATION_JSON, "application/pdf"})
    public Response postKarteno3WithOrcaPrefix(@Context HttpServletRequest request, String payload) {
        return respondReport(request, OrcaEndpoint.KARTENO3_REPORT,
                "/orca/karteno3v2", payload, "ORCA_REPORT_KARTENO3");
    }

    @POST
    @Path("/api/orca/karteno3v2")
    @Consumes({MediaType.APPLICATION_XML, MediaType.TEXT_XML})
    @Produces({MediaType.APPLICATION_JSON, "application/pdf"})
    public Response postKarteno3WithApiOrcaPrefix(@Context HttpServletRequest request, String payload) {
        return respondReport(request, OrcaEndpoint.KARTENO3_REPORT,
                "/api/orca/karteno3v2", payload, "ORCA_REPORT_KARTENO3");
    }

    @POST
    @Path("/api/api01rv2/karteno3v2")
    @Consumes({MediaType.APPLICATION_XML, MediaType.TEXT_XML})
    @Produces({MediaType.APPLICATION_JSON, "application/pdf"})
    public Response postKarteno3WithApiPrefix(@Context HttpServletRequest request, String payload) {
        return respondReport(request, OrcaEndpoint.KARTENO3_REPORT,
                "/api/api01rv2/karteno3v2", payload, "ORCA_REPORT_KARTENO3");
    }

    @POST
    @Path("/api01rv2/invoicereceiptv2")
    @Consumes({MediaType.APPLICATION_XML, MediaType.TEXT_XML})
    @Produces({MediaType.APPLICATION_JSON, "application/pdf"})
    public Response postInvoiceReceipt(@Context HttpServletRequest request, String payload) {
        return respondReport(request, OrcaEndpoint.INVOICE_RECEIPT_REPORT,
                "/api01rv2/invoicereceiptv2", payload, "ORCA_REPORT_INVOICE_RECEIPT");
    }

    @POST
    @Path("/orca/invoicereceiptv2")
    @Consumes({MediaType.APPLICATION_XML, MediaType.TEXT_XML})
    @Produces({MediaType.APPLICATION_JSON, "application/pdf"})
    public Response postInvoiceReceiptWithOrcaPrefix(@Context HttpServletRequest request, String payload) {
        return respondReport(request, OrcaEndpoint.INVOICE_RECEIPT_REPORT,
                "/orca/invoicereceiptv2", payload, "ORCA_REPORT_INVOICE_RECEIPT");
    }

    @POST
    @Path("/api/orca/invoicereceiptv2")
    @Consumes({MediaType.APPLICATION_XML, MediaType.TEXT_XML})
    @Produces({MediaType.APPLICATION_JSON, "application/pdf"})
    public Response postInvoiceReceiptWithApiOrcaPrefix(@Context HttpServletRequest request, String payload) {
        return respondReport(request, OrcaEndpoint.INVOICE_RECEIPT_REPORT,
                "/api/orca/invoicereceiptv2", payload, "ORCA_REPORT_INVOICE_RECEIPT");
    }

    @POST
    @Path("/api/api01rv2/invoicereceiptv2")
    @Consumes({MediaType.APPLICATION_XML, MediaType.TEXT_XML})
    @Produces({MediaType.APPLICATION_JSON, "application/pdf"})
    public Response postInvoiceReceiptWithApiPrefix(@Context HttpServletRequest request, String payload) {
        return respondReport(request, OrcaEndpoint.INVOICE_RECEIPT_REPORT,
                "/api/api01rv2/invoicereceiptv2", payload, "ORCA_REPORT_INVOICE_RECEIPT");
    }

    @POST
    @Path("/api01rv2/statementv2")
    @Consumes({MediaType.APPLICATION_XML, MediaType.TEXT_XML})
    @Produces({MediaType.APPLICATION_JSON, "application/pdf"})
    public Response postStatement(@Context HttpServletRequest request, String payload) {
        return respondReport(request, OrcaEndpoint.STATEMENT_REPORT,
                "/api01rv2/statementv2", payload, "ORCA_REPORT_STATEMENT");
    }

    @POST
    @Path("/orca/statementv2")
    @Consumes({MediaType.APPLICATION_XML, MediaType.TEXT_XML})
    @Produces({MediaType.APPLICATION_JSON, "application/pdf"})
    public Response postStatementWithOrcaPrefix(@Context HttpServletRequest request, String payload) {
        return respondReport(request, OrcaEndpoint.STATEMENT_REPORT,
                "/orca/statementv2", payload, "ORCA_REPORT_STATEMENT");
    }

    @POST
    @Path("/api/orca/statementv2")
    @Consumes({MediaType.APPLICATION_XML, MediaType.TEXT_XML})
    @Produces({MediaType.APPLICATION_JSON, "application/pdf"})
    public Response postStatementWithApiOrcaPrefix(@Context HttpServletRequest request, String payload) {
        return respondReport(request, OrcaEndpoint.STATEMENT_REPORT,
                "/api/orca/statementv2", payload, "ORCA_REPORT_STATEMENT");
    }

    @POST
    @Path("/api/api01rv2/statementv2")
    @Consumes({MediaType.APPLICATION_XML, MediaType.TEXT_XML})
    @Produces({MediaType.APPLICATION_JSON, "application/pdf"})
    public Response postStatementWithApiPrefix(@Context HttpServletRequest request, String payload) {
        return respondReport(request, OrcaEndpoint.STATEMENT_REPORT,
                "/api/api01rv2/statementv2", payload, "ORCA_REPORT_STATEMENT");
    }

    @GET
    @Path("/blobapi/{dataId}")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response getBlob(@Context HttpServletRequest request, @PathParam("dataId") String dataId) {
        return proxyBlob(request, dataId, "/blobapi/" + dataId);
    }

    @GET
    @Path("/api/blobapi/{dataId}")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response getBlobWithApiPrefix(@Context HttpServletRequest request, @PathParam("dataId") String dataId) {
        return proxyBlob(request, dataId, "/api/blobapi/" + dataId);
    }

    private Response respondReport(HttpServletRequest request, OrcaEndpoint endpoint, String resourcePath,
            String payload, String action) {
        String runId = AbstractOrcaRestResource.resolveRunIdValue(request);
        Map<String, Object> details = buildAuditDetails(request, resourcePath, runId);
        try {
            if (orcaTransport == null) {
                throw new OrcaGatewayException("ORCA transport is not available");
            }
            if (payload == null || payload.isBlank()) {
                throw new BadRequestException("ORCA report payload is required");
            }
            if (isJsonPayload(payload)) {
                throw new BadRequestException("ORCA report payload must be xml2");
            }
            OrcaTransportResult result = orcaTransport.invokeDetailed(endpoint, OrcaTransportRequest.post(payload));
            Response pdfResponse = tryPdfBlobResponse(request, result, payload, details, resourcePath, action, runId);
            if (pdfResponse != null) {
                return pdfResponse;
            }
            markSuccess(details);
            recordAudit(request, resourcePath, action, details,
                    AuditEventEnvelope.Outcome.SUCCESS, null, null);
            return OrcaApiProxySupport.buildProxyResponse(result, runId);
        } catch (RuntimeException ex) {
            String errorCode = "orca.report.error";
            String errorMessage = ex.getMessage();
            int status = (ex instanceof BadRequestException)
                    ? Response.Status.BAD_REQUEST.getStatusCode()
                    : Response.Status.BAD_GATEWAY.getStatusCode();
            markFailure(details, status, errorCode, errorMessage);
            recordAudit(request, resourcePath, action, details,
                    AuditEventEnvelope.Outcome.FAILURE, errorCode, errorMessage);
            throw ex;
        }
    }

    private Response tryPdfBlobResponse(HttpServletRequest request, OrcaTransportResult result, String payload,
            Map<String, Object> details, String resourcePath, String action, String runId) {
        if (result == null || result.getBody() == null || result.getBody().isBlank()) {
            return null;
        }
        if (!isPdfRequest(payload)) {
            return null;
        }
        String dataId = extractDataId(result.getBody());
        if (dataId == null || dataId.isBlank()) {
            return null;
        }
        BlobStreamResult blob = fetchBlobWithRetry(dataId, details);
        if (blob == null || blob.body == null) {
            return null;
        }
        markSuccess(details);
        details.put("dataId", dataId);
        recordAudit(request, resourcePath, action, details,
                AuditEventEnvelope.Outcome.SUCCESS, null, null);
        StreamingOutput stream = output -> streamPdfFromZip(blob, output);
        return Response.ok(stream, "application/pdf")
                .header("X-Run-Id", runId)
                .header("X-Orca-Data-Id", dataId)
                .build();
    }

    private BlobStreamResult fetchBlobWithRetry(String dataId, Map<String, Object> details) {
        String authHeader = restOrcaTransport != null ? restOrcaTransport.resolveBasicAuthHeader() : null;
        if (authHeader == null || authHeader.isBlank()) {
            throw new OrcaGatewayException("ORCA basic auth is not configured");
        }
        String primaryUrl = restOrcaTransport != null ? restOrcaTransport.buildOrcaUrl("/blobapi/" + dataId) : null;
        String secondaryUrl = resolveAlternateBlobUrl(primaryUrl);
        Instant deadline = Instant.now().plus(DEFAULT_BLOB_DEADLINE);
        Integer lastStatus = null;
        RuntimeException lastFailure = null;
        for (String candidate : buildBlobCandidates(primaryUrl, secondaryUrl)) {
            for (int attempt = 0; attempt <= BLOB_RETRY_MAX; attempt++) {
                if (isDeadlineExceeded(deadline)) {
                    throw new OrcaGatewayException("ORCA blobapi deadline exceeded");
                }
                try {
                    Duration timeout = resolveBlobRequestTimeout(deadline);
                    BlobStreamResult attemptResult = fetchBlob(candidate, authHeader, timeout);
                    lastStatus = attemptResult.status;
                    if (attemptResult.status >= 200 && attemptResult.status < 300 && attemptResult.body != null) {
                        details.put("resolvedUrl", attemptResult.url);
                        return attemptResult;
                    }
                    closeQuietly(attemptResult.body);
                    if (!shouldRetryBlob(attemptResult.status)) {
                        break;
                    }
                } catch (RuntimeException ex) {
                    lastFailure = ex;
                }
                if (!sleepUntilDeadline(deadline, BLOB_RETRY_BACKOFF_MS)) {
                    throw new OrcaGatewayException("ORCA blobapi deadline exceeded");
                }
            }
        }
        if (lastFailure != null) {
            throw lastFailure;
        }
        if (lastStatus != null) {
            throw new OrcaGatewayException("ORCA blobapi response status " + lastStatus);
        }
        throw new OrcaGatewayException("ORCA blobapi response missing");
    }

    private Response proxyBlob(HttpServletRequest request, String dataId, String resourcePath) {
        String runId = AbstractOrcaRestResource.resolveRunIdValue(request);
        Map<String, Object> details = buildAuditDetails(request, resourcePath, runId);
        try {
            if (dataId == null || dataId.isBlank()) {
                throw new IllegalArgumentException("dataId is required");
            }
            BlobStreamResult blob = fetchBlobWithRetry(dataId, details);
            if (blob == null || blob.body == null) {
                throw new OrcaGatewayException("ORCA blobapi response missing");
            }
            markSuccess(details);
            recordAudit(request, resourcePath, "ORCA_REPORT_BLOB", details,
                    AuditEventEnvelope.Outcome.SUCCESS, null, null);
            StreamingOutput stream = output -> streamBlob(blob, output);
            Response.ResponseBuilder builder = Response.ok(stream)
                    .header("X-Run-Id", runId)
                    .header("X-Orca-Blob-Url", details.get("resolvedUrl"));
            if (blob.contentLength >= 0) {
                builder.header("Content-Length", blob.contentLength);
            }
            String contentType = blob.contentType != null && !blob.contentType.isBlank()
                    ? blob.contentType
                    : MediaType.APPLICATION_OCTET_STREAM;
            return builder.type(contentType).build();
        } catch (RuntimeException ex) {
            String errorCode = "orca.report.blob.error";
            String errorMessage = ex.getMessage();
            markFailure(details, Response.Status.BAD_GATEWAY.getStatusCode(), errorCode, errorMessage);
            recordAudit(request, resourcePath, "ORCA_REPORT_BLOB", details,
                    AuditEventEnvelope.Outcome.FAILURE, errorCode, errorMessage);
            throw ex;
        }
    }

    private BlobStreamResult fetchBlob(String url, String authHeader, Duration timeout) {
        try {
            HttpClient resolvedClient = restOrcaTransport != null && restOrcaTransport.rawHttpClient() != null
                    ? restOrcaTransport.rawHttpClient()
                    : client;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(timeout != null ? timeout : DEFAULT_READ_TIMEOUT)
                    .header("Authorization", authHeader)
                    .GET()
                    .build();
            HttpResponse<InputStream> response = resolvedClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            String contentType = response.headers().firstValue("Content-Type").orElse(null);
            long contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
            return new BlobStreamResult(url, response.statusCode(), response.body(), contentType, contentLength);
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "Failed to call ORCA blobapi: " + url, ex);
            throw new OrcaGatewayException("Failed to call ORCA blobapi", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new OrcaGatewayException("ORCA blobapi request interrupted", ex);
        } catch (IllegalArgumentException ex) {
            throw new OrcaGatewayException("Invalid ORCA blobapi URL: " + url, ex);
        }
    }

    private void streamBlob(BlobStreamResult blob, OutputStream output) throws IOException {
        if (blob == null || blob.body == null) {
            throw new OrcaGatewayException("ORCA blobapi response body is empty");
        }
        try (InputStream in = blob.body) {
            in.transferTo(output);
            output.flush();
        }
    }

    private void streamPdfFromZip(BlobStreamResult blob, OutputStream output) throws IOException {
        if (blob == null || blob.body == null) {
            throw new OrcaGatewayException("ORCA blobapi response body is empty");
        }
        try (InputStream in = blob.body; ZipInputStream zis = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String entryName = entry.getName();
                if (entryName != null && entryName.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
                    zis.transferTo(output);
                    output.flush();
                    return;
                }
            }
        }
        throw new OrcaGatewayException("ORCA blob zip does not contain PDF");
    }

    private void closeQuietly(InputStream stream) {
        if (stream == null) {
            return;
        }
        try {
            stream.close();
        } catch (IOException ex) {
            LOGGER.log(Level.FINE, "Failed to close ORCA blob stream", ex);
        }
    }

    private boolean shouldRetryBlob(int status) {
        return status == 404 || status == 202 || status == 204 || status == 503;
    }

    private boolean sleepUntilDeadline(Instant deadline, long backoffMs) {
        if (backoffMs <= 0) {
            return !isDeadlineExceeded(deadline);
        }
        long remainingMs = Duration.between(Instant.now(), deadline).toMillis();
        if (remainingMs <= 0) {
            return false;
        }
        long sleepMs = Math.min(backoffMs, remainingMs);
        try {
            Thread.sleep(sleepMs);
            return !isDeadlineExceeded(deadline);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private boolean isDeadlineExceeded(Instant deadline) {
        return deadline == null || !Instant.now().isBefore(deadline);
    }

    private Duration resolveBlobRequestTimeout(Instant deadline) {
        if (deadline == null) {
            return DEFAULT_READ_TIMEOUT;
        }
        long remainingMs = Duration.between(Instant.now(), deadline).toMillis();
        if (remainingMs <= 0) {
            throw new OrcaGatewayException("ORCA blobapi deadline exceeded");
        }
        long timeoutMs = Math.min(DEFAULT_READ_TIMEOUT.toMillis(), remainingMs);
        return Duration.ofMillis(Math.max(1L, timeoutMs));
    }

    private String resolveAlternateBlobUrl(String primaryUrl) {
        if (primaryUrl == null || primaryUrl.isBlank()) {
            return null;
        }
        if (primaryUrl.contains("/api/blobapi/")) {
            return primaryUrl.replace("/api/blobapi/", "/blobapi/");
        }
        if (primaryUrl.contains("/blobapi/")) {
            return primaryUrl.replace("/blobapi/", "/api/blobapi/");
        }
        return null;
    }

    private java.util.List<String> buildBlobCandidates(String primaryUrl, String secondaryUrl) {
        java.util.List<String> candidates = new java.util.ArrayList<>();
        if (primaryUrl != null && !primaryUrl.isBlank()) {
            candidates.add(primaryUrl);
        }
        if (secondaryUrl != null && !secondaryUrl.isBlank() && !secondaryUrl.equals(primaryUrl)) {
            candidates.add(secondaryUrl);
        }
        return candidates;
    }

    private String extractDataId(String json) {
        try {
            JsonNode root = readJsonTree(json);
            Optional<JsonNode> node = findJsonValue(root, "Data_Id");
            return node.map(JsonNode::asText).orElse(null);
        } catch (IOException ex) {
            return null;
        }
    }

    private Optional<JsonNode> findJsonValue(JsonNode node, String key) {
        if (node == null || key == null) {
            return Optional.empty();
        }
        if (node.has(key)) {
            return Optional.ofNullable(node.get(key));
        }
        for (JsonNode child : node) {
            Optional<JsonNode> found = findJsonValue(child, key);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    private boolean isPdfRequest(String payload) {
        if (payload == null || payload.isBlank()) {
            return false;
        }
        Document document = parseXmlSafely(payload);
        if (document == null) {
            return false;
        }
        if (hasTagValueIgnoreCase(document, "Print_Mode", "PDF")) {
            return true;
        }
        return hasTagValueIgnoreCase(document, "Output_Format", "PDF")
                || hasTagValueIgnoreCase(document, "Output_Format", "1")
                || hasTagValueIgnoreCase(document, "Output_Format", "2");
    }

    private Document parseXmlSafely(String payload) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setNamespaceAware(true);
            return factory.newDocumentBuilder().parse(new InputSource(new StringReader(payload)));
        } catch (Exception ex) {
            LOGGER.log(Level.FINE, "Failed to parse report payload as XML", ex);
            return null;
        }
    }

    private boolean hasTagValueIgnoreCase(Document document, String tagName, String expectedValue) {
        if (document == null || tagName == null || expectedValue == null) {
            return false;
        }
        String expected = expectedValue.trim();
        if (expected.isEmpty()) {
            return false;
        }
        Node root = document.getDocumentElement();
        return hasTagValueIgnoreCase(root, tagName, expected);
    }

    private boolean hasTagValueIgnoreCase(Node node, String tagName, String expectedValue) {
        if (node == null) {
            return false;
        }
        if (node instanceof Element element) {
            String current = element.getTagName();
            if (current != null && current.equalsIgnoreCase(tagName)) {
                String text = element.getTextContent();
                if (text != null && text.trim().equalsIgnoreCase(expectedValue)) {
                    return true;
                }
            }
        }
        Node child = node.getFirstChild();
        while (child != null) {
            if (hasTagValueIgnoreCase(child, tagName, expectedValue)) {
                return true;
            }
            child = child.getNextSibling();
        }
        return false;
    }

    private Map<String, Object> buildAuditDetails(HttpServletRequest request, String resourcePath, String runId) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("runId", runId);
        details.put("resource", resourcePath);
        String remoteUser = request != null ? request.getRemoteUser() : null;
        String facilityId = getRemoteFacility(remoteUser);
        if (facilityId != null && !facilityId.isBlank()) {
            details.put("facilityId", facilityId);
        }
        String traceId = resolveTraceId(request);
        if (traceId != null && !traceId.isBlank()) {
            details.put("traceId", traceId);
        }
        String requestId = request != null ? request.getHeader("X-Request-Id") : null;
        if (requestId != null && !requestId.isBlank()) {
            details.put("requestId", requestId);
        } else if (traceId != null && !traceId.isBlank()) {
            details.put("requestId", traceId);
        }
        return details;
    }

    private void markSuccess(Map<String, Object> details) {
        if (details != null) {
            details.put("status", "success");
        }
    }

    private void markFailure(Map<String, Object> details, int httpStatus, String errorCode, String errorMessage) {
        if (details == null) {
            return;
        }
        details.put("status", "failed");
        details.put("httpStatus", httpStatus);
        if (errorCode != null && !errorCode.isBlank()) {
            details.put("errorCode", errorCode);
        }
        if (errorMessage != null && !errorMessage.isBlank()) {
            details.put("errorMessage", errorMessage);
        }
    }

    private void recordAudit(HttpServletRequest request, String resourcePath, String action, Map<String, Object> details,
            AuditEventEnvelope.Outcome outcome, String errorCode, String errorMessage) {
        if (sessionAuditDispatcher == null) {
            return;
        }
        AuditEventPayload payload = new AuditEventPayload();
        payload.setAction(action);
        payload.setResource(resourcePath);
        payload.setActorId(request != null ? request.getRemoteUser() : null);
        payload.setIpAddress(request != null ? request.getRemoteAddr() : null);
        payload.setUserAgent(request != null ? request.getHeader("User-Agent") : null);
        String traceId = resolveTraceId(request);
        if (traceId != null && !traceId.isBlank()) {
            payload.setTraceId(traceId);
        }
        String requestId = request != null ? request.getHeader("X-Request-Id") : null;
        if (requestId != null && !requestId.isBlank()) {
            payload.setRequestId(requestId);
        } else if (traceId != null && !traceId.isBlank()) {
            payload.setRequestId(traceId);
        }
        payload.setDetails(details);
        sessionAuditDispatcher.record(payload, outcome, errorCode, errorMessage);
    }

    private boolean isJsonPayload(String payload) {
        if (payload == null) {
            return false;
        }
        String trimmed = payload.trim();
        return trimmed.startsWith("{") || trimmed.startsWith("[");
    }


    private static final class BlobStreamResult {
        private final String url;
        private final int status;
        private final InputStream body;
        private final String contentType;
        private final long contentLength;

        private BlobStreamResult(String url, int status, InputStream body, String contentType, long contentLength) {
            this.url = url;
            this.status = status;
            this.body = body;
            this.contentType = contentType;
            this.contentLength = contentLength;
        }
    }
}
