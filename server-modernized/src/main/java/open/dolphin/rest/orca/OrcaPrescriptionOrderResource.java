package open.dolphin.rest.orca;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import open.dolphin.audit.AuditEventEnvelope;
import open.dolphin.infomodel.PatientModel;
import open.dolphin.rest.dto.orca.PrescriptionDoInputMeta;
import open.dolphin.rest.dto.orca.PrescriptionDoctorComment;
import open.dolphin.rest.dto.orca.PrescriptionDrug;
import open.dolphin.rest.dto.orca.PrescriptionOrder;
import open.dolphin.rest.dto.orca.PrescriptionOrderDoImportRequest;
import open.dolphin.rest.dto.orca.PrescriptionOrderDoImportResponse;
import open.dolphin.rest.dto.orca.PrescriptionOrderFetchResponse;
import open.dolphin.rest.dto.orca.PrescriptionOrderSaveResponse;
import open.dolphin.rest.dto.orca.PrescriptionRp;
import open.dolphin.rest.dto.orca.PrescriptionSetting;
import open.dolphin.session.PatientServiceBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/orca/prescription-orders")
@Produces(MediaType.APPLICATION_JSON)
public class OrcaPrescriptionOrderResource extends AbstractOrcaRestResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(OrcaPrescriptionOrderResource.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    @Inject
    private PatientServiceBean patientServiceBean;

    @Inject
    private PrescriptionOrderRepository prescriptionOrderRepository;

    @GET
    public PrescriptionOrderFetchResponse getLatestOrder(
            @Context HttpServletRequest request,
            @QueryParam("patientId") String patientId,
            @QueryParam("encounterId") String encounterId,
            @QueryParam("encounterDate") String encounterDate) {

        String runId = resolveRunId(request);
        requireRemoteUser(request);
        String facilityId = requireFacilityId(request);
        if (!hasText(patientId)) {
            recordValidationFailure(request, facilityId, null, runId, "patientId", "patientId is required",
                    "ORCA_PRESCRIPTION_ORDER_FETCH");
            throw validationError(request, "patientId", "patientId is required");
        }

        String normalizedPatientId = patientId.trim();
        ensurePatientExists(request, facilityId, normalizedPatientId, runId, "ORCA_PRESCRIPTION_ORDER_FETCH");
        LocalDate resolvedEncounterDate = parseOptionalDate(request, "encounterDate", encounterDate,
                facilityId, normalizedPatientId, runId, "ORCA_PRESCRIPTION_ORDER_FETCH");
        String resolvedEncounterId = trimToNull(encounterId);

        Optional<PrescriptionOrderRepository.StoredPrescriptionOrder> stored =
                prescriptionOrderRepository.findLatest(facilityId, normalizedPatientId, resolvedEncounterId, resolvedEncounterDate);

        PrescriptionOrderFetchResponse response = new PrescriptionOrderFetchResponse();
        response.setRunId(runId);
        response.setPatientId(normalizedPatientId);
        response.setEncounterId(resolvedEncounterId);
        response.setEncounterDate(resolvedEncounterDate != null ? resolvedEncounterDate.toString() : null);

        if (stored.isEmpty()) {
            response.setApiResult("01");
            response.setApiResultMessage("処方オーダーは未登録です");
            response.setFound(false);
            response.setOrder(null);
        } else {
            PrescriptionOrder order = decodeOrderOrThrow(request, stored.get(), facilityId, normalizedPatientId,
                    runId, "ORCA_PRESCRIPTION_ORDER_FETCH");
            response.setApiResult("00");
            response.setApiResultMessage("処理終了");
            response.setFound(true);
            response.setOrder(order);
            if (!hasText(response.getEncounterId())) {
                response.setEncounterId(trimToNull(order.getEncounterId()));
            }
            if (!hasText(response.getEncounterDate())) {
                response.setEncounterDate(normalizeDateText(order.getEncounterDate()));
            }
        }

        Map<String, Object> audit = new HashMap<>();
        audit.put("facilityId", facilityId);
        audit.put("patientId", normalizedPatientId);
        audit.put("encounterId", resolvedEncounterId);
        audit.put("encounterDate", response.getEncounterDate());
        audit.put("found", response.isFound());
        audit.put("runId", runId);
        recordAudit(request, "ORCA_PRESCRIPTION_ORDER_FETCH", audit, AuditEventEnvelope.Outcome.SUCCESS);
        return response;
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    public PrescriptionOrderSaveResponse saveOrder(
            @Context HttpServletRequest request,
            PrescriptionOrder payload) {

        String runId = resolveRunId(request);
        String remoteUser = requireRemoteUser(request);
        String facilityId = requireFacilityId(request);

        if (payload == null) {
            recordValidationFailure(request, facilityId, null, runId, "payload", "payload is required",
                    "ORCA_PRESCRIPTION_ORDER_SAVE");
            throw validationError(request, "payload", "payload is required");
        }
        if (!hasText(payload.getPatientId())) {
            recordValidationFailure(request, facilityId, null, runId, "patientId", "patientId is required",
                    "ORCA_PRESCRIPTION_ORDER_SAVE");
            throw validationError(request, "patientId", "patientId is required");
        }

        String patientId = payload.getPatientId().trim();
        ensurePatientExists(request, facilityId, patientId, runId, "ORCA_PRESCRIPTION_ORDER_SAVE");
        LocalDate encounterDate = parseOptionalDate(request, "encounterDate", payload.getEncounterDate(),
                facilityId, patientId, runId, "ORCA_PRESCRIPTION_ORDER_SAVE");
        LocalDate performDate = parseOptionalDate(request, "performDate", payload.getPerformDate(),
                facilityId, patientId, runId, "ORCA_PRESCRIPTION_ORDER_SAVE");

        PrescriptionOrder normalized = copyOrder(payload);
        normalized.setPatientId(patientId);
        normalized.setEncounterId(trimToNull(normalized.getEncounterId()));
        normalized.setEncounterDate(encounterDate != null ? encounterDate.toString() : null);
        normalized.setPerformDate(performDate != null ? performDate.toString() : null);

        String json = writeJsonOrThrow(request, normalized, facilityId, patientId, runId, "ORCA_PRESCRIPTION_ORDER_SAVE");
        Instant now = Instant.now();
        long orderId = prescriptionOrderRepository.save(
                facilityId,
                patientId,
                normalized.getEncounterId(),
                encounterDate,
                performDate,
                json,
                now,
                remoteUser);

        PrescriptionOrderSaveResponse response = new PrescriptionOrderSaveResponse();
        response.setApiResult("00");
        response.setApiResultMessage("処理終了");
        response.setRunId(runId);
        response.setOrderId(orderId);
        response.setPatientId(patientId);
        response.setEncounterId(normalized.getEncounterId());
        response.setEncounterDate(normalized.getEncounterDate());
        response.setOrder(normalized);

        Map<String, Object> audit = new HashMap<>();
        audit.put("facilityId", facilityId);
        audit.put("patientId", patientId);
        audit.put("encounterId", normalized.getEncounterId());
        audit.put("encounterDate", normalized.getEncounterDate());
        audit.put("orderId", orderId);
        audit.put("runId", runId);
        recordAudit(request, "ORCA_PRESCRIPTION_ORDER_SAVE", audit, AuditEventEnvelope.Outcome.SUCCESS);
        return response;
    }

    @POST
    @Path("/do-import")
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    public PrescriptionOrderDoImportResponse doImport(
            @Context HttpServletRequest request,
            PrescriptionOrderDoImportRequest payload) {

        String runId = resolveRunId(request);
        String remoteUser = requireRemoteUser(request);
        String facilityId = requireFacilityId(request);

        if (payload == null) {
            recordValidationFailure(request, facilityId, null, runId, "payload", "payload is required",
                    "ORCA_PRESCRIPTION_DO_IMPORT");
            throw validationError(request, "payload", "payload is required");
        }
        if (!hasText(payload.getPatientId())) {
            recordValidationFailure(request, facilityId, null, runId, "patientId", "patientId is required",
                    "ORCA_PRESCRIPTION_DO_IMPORT");
            throw validationError(request, "patientId", "patientId is required");
        }
        if (payload.getDoOrder() == null) {
            recordValidationFailure(request, facilityId, payload.getPatientId(), runId, "doOrder", "doOrder is required",
                    "ORCA_PRESCRIPTION_DO_IMPORT");
            throw validationError(request, "doOrder", "doOrder is required");
        }

        String patientId = payload.getPatientId().trim();
        ensurePatientExists(request, facilityId, patientId, runId, "ORCA_PRESCRIPTION_DO_IMPORT");

        LocalDate targetEncounterDate = parseOptionalDate(request, "encounterDate", payload.getEncounterDate(),
                facilityId, patientId, runId, "ORCA_PRESCRIPTION_DO_IMPORT");
        String targetEncounterId = trimToNull(payload.getEncounterId());

        validateDoImportUsageCodes(request, payload.getDoOrder(), facilityId, patientId, runId);

        Optional<PrescriptionOrderRepository.StoredPrescriptionOrder> stored =
                prescriptionOrderRepository.findLatest(facilityId, patientId, targetEncounterId, targetEncounterDate);
        PrescriptionOrder baseOrder = stored
                .map(row -> decodeOrderOrThrow(request, row, facilityId, patientId, runId, "ORCA_PRESCRIPTION_DO_IMPORT"))
                .orElseGet(PrescriptionOrder::new);

        List<String> warnings = new ArrayList<>();
        Instant now = Instant.now();
        PrescriptionOrder merged = applyDoImport(
                baseOrder,
                payload.getDoOrder(),
                patientId,
                targetEncounterId,
                targetEncounterDate,
                remoteUser,
                runId,
                now,
                warnings);

        LocalDate mergedEncounterDate = parseOptionalDate(request, "encounterDate", merged.getEncounterDate(),
                facilityId, patientId, runId, "ORCA_PRESCRIPTION_DO_IMPORT");
        LocalDate performDate = parseOptionalDate(request, "performDate", merged.getPerformDate(),
                facilityId, patientId, runId, "ORCA_PRESCRIPTION_DO_IMPORT");

        merged.setEncounterDate(mergedEncounterDate != null ? mergedEncounterDate.toString() : null);
        merged.setPerformDate(performDate != null ? performDate.toString() : null);

        String json = writeJsonOrThrow(request, merged, facilityId, patientId, runId, "ORCA_PRESCRIPTION_DO_IMPORT");
        long orderId = prescriptionOrderRepository.save(
                facilityId,
                patientId,
                merged.getEncounterId(),
                mergedEncounterDate,
                performDate,
                json,
                now,
                remoteUser);

        PrescriptionOrderDoImportResponse response = new PrescriptionOrderDoImportResponse();
        response.setApiResult("00");
        response.setApiResultMessage("処理終了");
        response.setRunId(runId);
        response.setOrderId(orderId);
        response.setPatientId(patientId);
        response.setEncounterId(merged.getEncounterId());
        response.setEncounterDate(merged.getEncounterDate());
        response.setOrder(merged);
        response.setWarnings(warnings);

        Map<String, Object> audit = new HashMap<>();
        audit.put("facilityId", facilityId);
        audit.put("patientId", patientId);
        audit.put("encounterId", merged.getEncounterId());
        audit.put("encounterDate", merged.getEncounterDate());
        audit.put("orderId", orderId);
        audit.put("warnings", warnings.size());
        audit.put("runId", runId);
        recordAudit(request, "ORCA_PRESCRIPTION_DO_IMPORT", audit, AuditEventEnvelope.Outcome.SUCCESS);
        return response;
    }

    private PrescriptionOrder applyDoImport(
            PrescriptionOrder base,
            PrescriptionOrder doOrder,
            String patientId,
            String targetEncounterId,
            LocalDate targetEncounterDate,
            String remoteUser,
            String runId,
            Instant now,
            List<String> warnings) {

        PrescriptionOrder merged = copyOrder(base);
        PrescriptionOrder incoming = copyOrder(doOrder);
        if (merged == null) {
            merged = new PrescriptionOrder();
        }
        if (incoming == null) {
            incoming = new PrescriptionOrder();
        }

        merged.setPatientId(patientId);
        if (hasText(targetEncounterId)) {
            merged.setEncounterId(targetEncounterId);
        } else if (hasText(incoming.getEncounterId())) {
            merged.setEncounterId(incoming.getEncounterId().trim());
        } else {
            merged.setEncounterId(trimToNull(merged.getEncounterId()));
        }

        LocalDate resolvedEncounterDate = targetEncounterDate;
        if (resolvedEncounterDate == null) {
            resolvedEncounterDate = parseFlexibleDate(incoming.getEncounterDate());
        }
        if (resolvedEncounterDate == null) {
            resolvedEncounterDate = parseFlexibleDate(merged.getEncounterDate());
        }
        if (resolvedEncounterDate != null) {
            merged.setEncounterDate(resolvedEncounterDate.toString());
        }

        LocalDate resolvedPerformDate = parseFlexibleDate(incoming.getPerformDate());
        if (resolvedPerformDate == null) {
            resolvedPerformDate = parseFlexibleDate(merged.getPerformDate());
        }
        if (resolvedPerformDate != null) {
            merged.setPerformDate(resolvedPerformDate.toString());
        }

        if (incoming.getPatientRequested() != null) {
            merged.setPatientRequested(incoming.getPatientRequested());
        }

        List<PrescriptionRp> incomingRps = safeList(incoming.getRps());
        for (PrescriptionRp incomingRp : incomingRps) {
            stampImportedRp(incomingRp, incoming, runId, remoteUser, now);
        }
        merged.setRps(mergeRps(merged.getRps(), incomingRps));

        if (!safeList(incoming.getClaimComments()).isEmpty()) {
            List<open.dolphin.rest.dto.orca.PrescriptionClaimComment> claimComments = safeList(merged.getClaimComments());
            claimComments.addAll(safeList(incoming.getClaimComments()));
            merged.setClaimComments(claimComments);
        }

        if (!safeList(incoming.getRemarks()).isEmpty()) {
            merged.setRemarks(safeList(incoming.getRemarks()));
        }

        merged.setPrescriptionSettings(mergeSettings(merged.getPrescriptionSettings(), incoming.getPrescriptionSettings()));

        List<PrescriptionDoctorComment> doctorComments = safeList(merged.getDoctorComments());
        doctorComments.addAll(safeList(incoming.getDoctorComments()));
        merged.setDoctorComments(doctorComments);

        PrescriptionDoInputMeta doMeta = merged.getDoInputMeta();
        if (doMeta == null) {
            doMeta = new PrescriptionDoInputMeta();
        }
        doMeta.setImportedFromDo(Boolean.TRUE);
        doMeta.setSourcePatientId(hasText(incoming.getPatientId()) ? incoming.getPatientId().trim() : patientId);
        doMeta.setSourceEncounterId(trimToNull(incoming.getEncounterId()));
        doMeta.setSourceEncounterDate(normalizeDateText(incoming.getEncounterDate()));
        if (incoming.getDoInputMeta() != null && hasText(incoming.getDoInputMeta().getSourceOrderId())) {
            doMeta.setSourceOrderId(incoming.getDoInputMeta().getSourceOrderId().trim());
        }
        doMeta.setImportedBy(remoteUser);
        doMeta.setImportedAt(now.toString());
        doMeta.setPolicyVersion("v1");
        doMeta.setRunId(runId);
        merged.setDoInputMeta(doMeta);

        LocalDate effectiveDate = resolvedEncounterDate != null ? resolvedEncounterDate : LocalDate.now();
        excludeExpiredImportedDrugs(merged, effectiveDate, warnings);
        return merged;
    }

    private void validateDoImportUsageCodes(
            HttpServletRequest request,
            PrescriptionOrder doOrder,
            String facilityId,
            String patientId,
            String runId) {
        if (doOrder == null || doOrder.getRps() == null) {
            return;
        }
        for (PrescriptionRp rp : doOrder.getRps()) {
            if (rp == null || rp.getDrugs() == null || rp.getDrugs().isEmpty()) {
                continue;
            }
            if (!hasText(rp.getUsageCode())) {
                Map<String, Object> details = new HashMap<>();
                details.put("facilityId", facilityId);
                details.put("patientId", patientId);
                details.put("runId", runId);
                details.put("field", "usageCode");
                details.put("rpNumber", rp.getRpNumber());
                details.put("validationError", Boolean.TRUE);
                markFailureDetails(details, Response.Status.BAD_REQUEST.getStatusCode(), "unregistered_usage",
                        "未登録用法を含むためDo入力を反映できません");
                recordAudit(request, "ORCA_PRESCRIPTION_DO_IMPORT", details, AuditEventEnvelope.Outcome.FAILURE);
                throw restError(request,
                        Response.Status.BAD_REQUEST,
                        "unregistered_usage",
                        "未登録用法を含むためDo入力を反映できません",
                        details,
                        null);
            }
        }
    }

    private void excludeExpiredImportedDrugs(PrescriptionOrder order, LocalDate asOf, List<String> warnings) {
        if (order == null || order.getRps() == null) {
            return;
        }
        for (PrescriptionRp rp : order.getRps()) {
            if (rp == null || rp.getDrugs() == null) {
                continue;
            }
            List<PrescriptionDrug> kept = new ArrayList<>();
            for (PrescriptionDrug drug : rp.getDrugs()) {
                if (drug == null) {
                    continue;
                }
                PrescriptionDoInputMeta meta = drug.getDoInputMeta();
                boolean imported = meta != null && Boolean.TRUE.equals(meta.getImportedFromDo());
                if (!imported) {
                    kept.add(drug);
                    continue;
                }
                LocalDate validTo = parseFlexibleDate(drug.getValidTo());
                if (validTo != null && validTo.isBefore(asOf)) {
                    warnings.add("有効期限切れ薬剤を除外: rp="
                            + trimToEmpty(rp.getRpNumber())
                            + ", code=" + trimToEmpty(drug.getCode())
                            + ", validTo=" + validTo);
                    continue;
                }
                kept.add(drug);
            }
            rp.setDrugs(kept);
        }
    }

    private void stampImportedRp(PrescriptionRp rp,
            PrescriptionOrder sourceOrder,
            String runId,
            String remoteUser,
            Instant now) {
        if (rp == null || rp.getDrugs() == null) {
            return;
        }
        for (PrescriptionDrug drug : rp.getDrugs()) {
            if (drug == null) {
                continue;
            }
            PrescriptionDoInputMeta meta = drug.getDoInputMeta();
            if (meta == null) {
                meta = new PrescriptionDoInputMeta();
            }
            meta.setImportedFromDo(Boolean.TRUE);
            if (!hasText(meta.getSourcePatientId())) {
                meta.setSourcePatientId(trimToNull(sourceOrder.getPatientId()));
            }
            if (!hasText(meta.getSourceEncounterId())) {
                meta.setSourceEncounterId(trimToNull(sourceOrder.getEncounterId()));
            }
            if (!hasText(meta.getSourceEncounterDate())) {
                meta.setSourceEncounterDate(normalizeDateText(sourceOrder.getEncounterDate()));
            }
            meta.setImportedBy(remoteUser);
            meta.setImportedAt(now.toString());
            if (!hasText(meta.getPolicyVersion())) {
                meta.setPolicyVersion("v1");
            }
            meta.setRunId(runId);
            drug.setDoInputMeta(meta);
        }
    }

    private List<PrescriptionRp> mergeRps(List<PrescriptionRp> baseRps, List<PrescriptionRp> incomingRps) {
        List<PrescriptionRp> merged = safeList(baseRps);
        if (incomingRps == null || incomingRps.isEmpty()) {
            return merged;
        }
        Map<String, Integer> byNumber = new LinkedHashMap<>();
        for (int i = 0; i < merged.size(); i++) {
            PrescriptionRp rp = merged.get(i);
            String key = rp != null ? trimToNull(rp.getRpNumber()) : null;
            if (key != null && !byNumber.containsKey(key)) {
                byNumber.put(key, i);
            }
        }
        for (PrescriptionRp incoming : incomingRps) {
            if (incoming == null) {
                continue;
            }
            String key = trimToNull(incoming.getRpNumber());
            Integer index = key != null ? byNumber.get(key) : null;
            if (index != null) {
                merged.set(index, incoming);
            } else {
                merged.add(incoming);
                if (key != null) {
                    byNumber.put(key, merged.size() - 1);
                }
            }
        }
        return merged;
    }

    private List<PrescriptionSetting> mergeSettings(List<PrescriptionSetting> baseSettings,
            List<PrescriptionSetting> incomingSettings) {
        List<PrescriptionSetting> merged = safeList(baseSettings);
        if (incomingSettings == null || incomingSettings.isEmpty()) {
            return merged;
        }
        Map<String, Integer> byCode = new LinkedHashMap<>();
        for (int i = 0; i < merged.size(); i++) {
            PrescriptionSetting setting = merged.get(i);
            String key = setting != null ? trimToNull(setting.getCode()) : null;
            if (key != null && !byCode.containsKey(key)) {
                byCode.put(key, i);
            }
        }
        for (PrescriptionSetting incoming : incomingSettings) {
            if (incoming == null) {
                continue;
            }
            String key = trimToNull(incoming.getCode());
            Integer index = key != null ? byCode.get(key) : null;
            if (index != null) {
                merged.set(index, incoming);
            } else {
                merged.add(incoming);
                if (key != null) {
                    byCode.put(key, merged.size() - 1);
                }
            }
        }
        return merged;
    }

    private PrescriptionOrder decodeOrderOrThrow(HttpServletRequest request,
            PrescriptionOrderRepository.StoredPrescriptionOrder stored,
            String facilityId,
            String patientId,
            String runId,
            String action) {
        try {
            return OBJECT_MAPPER.readValue(stored.payloadJson(), PrescriptionOrder.class);
        } catch (JsonProcessingException ex) {
            Map<String, Object> details = new HashMap<>();
            details.put("facilityId", facilityId);
            details.put("patientId", patientId);
            details.put("runId", runId);
            details.put("orderId", stored.id());
            markFailureDetails(details, Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(),
                    "prescription_order_decode_error", "Failed to decode prescription order payload");
            recordAudit(request, action, details, AuditEventEnvelope.Outcome.FAILURE);
            LOGGER.warn("Failed to decode prescription order payload (patientId={}, orderId={})",
                    patientId, stored.id(), ex);
            throw restError(request,
                    Response.Status.INTERNAL_SERVER_ERROR,
                    "prescription_order_decode_error",
                    "Failed to decode prescription order payload",
                    details,
                    ex);
        }
    }

    private String writeJsonOrThrow(HttpServletRequest request,
            PrescriptionOrder order,
            String facilityId,
            String patientId,
            String runId,
            String action) {
        try {
            return OBJECT_MAPPER.writeValueAsString(order);
        } catch (JsonProcessingException ex) {
            Map<String, Object> details = new HashMap<>();
            details.put("facilityId", facilityId);
            details.put("patientId", patientId);
            details.put("runId", runId);
            markFailureDetails(details, Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(),
                    "prescription_order_encode_error", "Failed to encode prescription order payload");
            recordAudit(request, action, details, AuditEventEnvelope.Outcome.FAILURE);
            LOGGER.warn("Failed to encode prescription order payload (patientId={})", patientId, ex);
            throw restError(request,
                    Response.Status.INTERNAL_SERVER_ERROR,
                    "prescription_order_encode_error",
                    "Failed to encode prescription order payload",
                    details,
                    ex);
        }
    }

    private void ensurePatientExists(HttpServletRequest request,
            String facilityId,
            String patientId,
            String runId,
            String action) {
        PatientModel patient = patientServiceBean.getPatientById(facilityId, patientId);
        if (patient != null) {
            return;
        }
        Map<String, Object> details = new HashMap<>();
        details.put("facilityId", facilityId);
        details.put("patientId", patientId);
        details.put("runId", runId);
        markFailureDetails(details, Response.Status.NOT_FOUND.getStatusCode(), "patient_not_found", "Patient not found");
        recordAudit(request, action, details, AuditEventEnvelope.Outcome.FAILURE);
        throw restError(request, Response.Status.NOT_FOUND, "patient_not_found", "Patient not found", details, null);
    }

    private void recordValidationFailure(HttpServletRequest request,
            String facilityId,
            String patientId,
            String runId,
            String field,
            String message,
            String action) {
        Map<String, Object> details = new HashMap<>();
        details.put("facilityId", facilityId);
        details.put("patientId", patientId);
        details.put("runId", runId);
        details.put("field", field);
        details.put("validationError", Boolean.TRUE);
        markFailureDetails(details, Response.Status.BAD_REQUEST.getStatusCode(), "invalid_request", message);
        recordAudit(request, action, details, AuditEventEnvelope.Outcome.FAILURE);
    }

    private LocalDate parseOptionalDate(HttpServletRequest request,
            String field,
            String value,
            String facilityId,
            String patientId,
            String runId,
            String action) {
        if (!hasText(value)) {
            return null;
        }
        try {
            return parseFlexibleDateStrict(value);
        } catch (DateTimeParseException ex) {
            recordValidationFailure(request, facilityId, patientId, runId, field,
                    field + " must be yyyy-MM-dd or yyyyMMdd", action);
            throw validationError(request, field, field + " must be yyyy-MM-dd or yyyyMMdd");
        }
    }

    private LocalDate parseFlexibleDate(String value) {
        if (!hasText(value)) {
            return null;
        }
        try {
            return parseFlexibleDateStrict(value);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private LocalDate parseFlexibleDateStrict(String value) {
        if (!hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.matches("\\d{8}")) {
            return LocalDate.parse(normalized,
                    java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
        }
        return LocalDate.parse(normalized);
    }

    private String normalizeDateText(String value) {
        LocalDate parsed = parseFlexibleDate(value);
        return parsed != null ? parsed.toString() : null;
    }

    private PrescriptionOrder copyOrder(PrescriptionOrder source) {
        if (source == null) {
            return null;
        }
        return OBJECT_MAPPER.convertValue(source, PrescriptionOrder.class);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String trimToNull(String value) {
        if (!hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private <T> List<T> safeList(List<T> source) {
        if (source == null || source.isEmpty()) {
            return new ArrayList<>();
        }
        List<T> copied = new ArrayList<>(source.size());
        for (T item : source) {
            if (Objects.nonNull(item)) {
                copied.add(item);
            }
        }
        return copied;
    }
}
