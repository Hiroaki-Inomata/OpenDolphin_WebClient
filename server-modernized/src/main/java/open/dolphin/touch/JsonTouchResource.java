package open.dolphin.touch;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.logging.Logger;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.BadRequestException;
import open.dolphin.infomodel.DiagnosisSendWrapper;
import open.dolphin.converter.StringListConverter;
import open.dolphin.infomodel.DocInfoModel;
import open.dolphin.infomodel.DocumentModel;
import open.dolphin.infomodel.IInfoModel;
import open.dolphin.infomodel.KarteBean;
import open.dolphin.infomodel.KarteEntryBean;
import open.dolphin.infomodel.ModuleInfoBean;
import open.dolphin.infomodel.ModuleModel;
import open.dolphin.infomodel.PatientList;
import open.dolphin.infomodel.PatientModel;
import open.dolphin.infomodel.RegisteredDiagnosisModel;
import open.dolphin.infomodel.SchemaModel;
import open.dolphin.infomodel.AttachmentModel;
import open.dolphin.infomodel.StringList;
import open.dolphin.infomodel.UserModel;
import open.dolphin.infomodel.VisitPackage;
import open.dolphin.session.KarteServiceBean;
import open.dolphin.touch.converter.IDocument;
import open.dolphin.touch.converter.IDocument2;
import open.dolphin.touch.converter.IMKDocument;
import open.dolphin.touch.converter.IMKDocument2;
import open.dolphin.touch.converter.IPatientList;
import open.dolphin.touch.converter.IPatientModel;
import open.dolphin.touch.converter.ISendPackage;
import open.dolphin.touch.converter.ISendPackage2;
import open.dolphin.touch.converter.IVisitPackage;
import open.dolphin.touch.support.TouchJsonConverter;

/**
 *
 * @author Kazushi Minagawa.
 */
@Path("/jtouch")
public class JsonTouchResource extends open.dolphin.rest.AbstractResource {

    private static final Logger LOGGER = Logger.getLogger(JsonTouchResource.class.getName());

    @Inject
    private TouchJsonConverter touchJsonConverter;

    @Inject
    private JsonTouchSharedService sharedService;

    @Inject
    private KarteServiceBean karteServiceBean;

    @Context
    private HttpServletRequest servletRequest;
    
    @GET
    @Path("/user/{uid}")
    @Produces(MediaType.APPLICATION_JSON)
    public JsonTouchSharedService.SafeUserResponse getUserById(@Context HttpServletRequest servletReq,
            @PathParam("uid") String uid) {
        String actor = servletReq != null ? servletReq.getRemoteUser() : null;
        JsonTouchSharedService.SafeUserResponse response = sharedService.getSafeUserById(actor, uid);
        if (response == null) {
            throw restError(servletReq, Response.Status.NOT_FOUND, "not_found", "Requested resource was not found.");
        }
        return response;
    }
    
    @GET
    @Path("/patient/{pid}")
    @Produces(MediaType.APPLICATION_JSON)
    public IPatientModel getPatientById(@Context HttpServletRequest servletReq, @PathParam("pid") String pid) {
        String fid = getRemoteFacility(servletReq.getRemoteUser());
        JsonTouchSharedService.PatientModelSnapshot snapshot = sharedService.getPatientSnapshot(fid, pid);
        IPatientModel model = new IPatientModel();
        model.setModel(snapshot.getPatient());
        model.setKartePK(snapshot.getKartePk());
        return model;
    }
    
    @GET
    @Path("/patients/name/{param}")
    @Produces(MediaType.APPLICATION_JSON)
    public IPatientList getPatientsByNameOrId(@Context HttpServletRequest servletReq, @PathParam("param") String param) {

        String [] params = param.split(",");
        
        String fid = getRemoteFacility(servletReq.getRemoteUser());
        String name = params[0];
        int firstResult = params.length==3 ? Integer.parseInt(params[1]) : 0;
        int maxResult = params.length==3 ? Integer.parseInt(params[2]) :100;

        List<PatientModel> found = sharedService.getPatientsByNameOrId(fid, name, firstResult, maxResult);
        PatientList patients = new PatientList();
        patients.setList(found);
        IPatientList response = new IPatientList();
        response.setModel(patients);
        return response;
    }
    
//minagawa^ 音声検索辞書作成
    @GET
    @Path("/patients/count")
    @Produces(MediaType.TEXT_PLAIN)
    public String getPatientCount(@Context HttpServletRequest servletReq) {
        String fid = getRemoteFacility(servletReq.getRemoteUser());
        return String.valueOf(sharedService.countPatients(fid));
    }
    
    @GET
    @Path("/patients/dump/kana/{param}")
    @Produces(MediaType.APPLICATION_JSON)
    public StringListConverter getPatientsWithKana(@Context HttpServletRequest servletReq, @PathParam("param") String param) {
        
        String fid = getRemoteFacility(servletReq.getRemoteUser());
        String [] params = param.split(",");
        int first = Integer.parseInt(params[0]);
        int max = Integer.parseInt(params[1]);
        List<String> kanaList = sharedService.getPatientsWithKana(fid, first, max);
        StringList stringList = new StringList();
        stringList.setList(kanaList);
        StringListConverter converter = new StringListConverter();
        converter.setModel(stringList);
        return converter;
    }
//minagawa$    
    
    @GET
    @Path("/visitpackage/{param}")
    @Produces(MediaType.APPLICATION_JSON)
    public IVisitPackage getVisitPackage(@Context HttpServletRequest servletReq, @PathParam("param") String param) {
        
        String[] params = param.split(",");
        
        long pvtPK = Long.parseLong(params[0]);
        long patientPK = Long.parseLong(params[1]);
        long docPK = Long.parseLong(params[2]);
        int mode = Integer.parseInt(params[3]);
        String actorFacility = requireActorFacility(servletReq);
        if (pvtPK > 0L) {
            ensureFacilityMatchOr404(actorFacility, karteServiceBean.findFacilityIdByPvtId(pvtPK), "pvtPk", pvtPK, servletReq);
        }
        if (patientPK > 0L) {
            ensureFacilityMatchOr404(actorFacility, karteServiceBean.findFacilityIdByPatientPk(patientPK), "patientPk", patientPK, servletReq);
        }
        if (docPK > 0L) {
            ensureFacilityMatchOr404(actorFacility, karteServiceBean.findFacilityIdByDocId(docPK), "docPk", docPK, servletReq);
        }
        VisitPackage visit = sharedService.getVisitPackage(pvtPK, patientPK, docPK, mode);
        IVisitPackage conv = new IVisitPackage();
        conv.setModel(visit);
        return conv;
    }
    
    @POST
    @Path("/sendPackage")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.TEXT_PLAIN)
    public String postSendPackage(String json) {
        final String endpoint = "POST /jtouch/sendPackage";
        final String traceId = JsonTouchAuditLogger.begin(servletRequest, endpoint,
                () -> "payloadSize=" + (json != null ? json.length() : 0));
        try {
            ISendPackage pkg = touchJsonConverter.readLegacy(json, ISendPackage.class);
            DocumentModel model = pkg != null ? pkg.documentModel() : null;
            DiagnosisSendWrapper wrapper = pkg != null ? pkg.diagnosisSendWrapperModel() : null;
            if (wrapper != null) {
                populateDiagnosisAuditMetadata(servletRequest, wrapper, "/jtouch/sendPackage");
            }
            String actorFacility = requireActorFacility(servletRequest);
            UserModel actorUserModel = requireActorUserModel(servletRequest);
            String documentPatientId = null;
            KarteBean resolvedKarte = null;
            if (model != null) {
                documentPatientId = sanitizeDocumentForPersist(model, servletRequest, actorUserModel);
                resolvedKarte = model.getKarte();
            }
            sanitizeDiagnosisPayload(wrapper, actorFacility, actorUserModel, resolvedKarte, documentPatientId, servletRequest);
            validateDeletedDiagnosisFacilities(actorFacility, pkg != null ? pkg.deletedDiagnsis() : null, servletRequest);
            long retPk = sharedService.processSendPackageElements(
                    model,
                    wrapper,
                    pkg != null ? pkg.deletedDiagnsis() : null,
                    pkg != null ? pkg.chartEventModel() : null);
            JsonTouchAuditLogger.success(endpoint, traceId, () -> "documentPk=" + retPk);
            return String.valueOf(retPk);
        } catch (WebApplicationException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw JsonTouchAuditLogger.failure(LOGGER, endpoint, traceId, e);
        }
    }
    
    // S.Oh 2014/02/06 iPadのFreeText対応 Add Start
    @POST
    @Path("/sendPackage2")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.TEXT_PLAIN)
    public String postSendPackage2(String json) {
        final String endpoint = "POST /jtouch/sendPackage2";
        final String traceId = JsonTouchAuditLogger.begin(servletRequest, endpoint,
                () -> "payloadSize=" + (json != null ? json.length() : 0));
        try {
            ISendPackage2 pkg = touchJsonConverter.readLegacy(json, ISendPackage2.class);
            DocumentModel model = pkg != null ? pkg.documentModel() : null;
            DiagnosisSendWrapper wrapper = pkg != null ? pkg.diagnosisSendWrapperModel() : null;
            if (wrapper != null) {
                populateDiagnosisAuditMetadata(servletRequest, wrapper, "/jtouch/sendPackage2");
            }
            String actorFacility = requireActorFacility(servletRequest);
            UserModel actorUserModel = requireActorUserModel(servletRequest);
            String documentPatientId = null;
            KarteBean resolvedKarte = null;
            if (model != null) {
                documentPatientId = sanitizeDocumentForPersist(model, servletRequest, actorUserModel);
                resolvedKarte = model.getKarte();
            }
            sanitizeDiagnosisPayload(wrapper, actorFacility, actorUserModel, resolvedKarte, documentPatientId, servletRequest);
            validateDeletedDiagnosisFacilities(actorFacility, pkg != null ? pkg.deletedDiagnsis() : null, servletRequest);
            long retPk = sharedService.processSendPackageElements(
                    model,
                    wrapper,
                    pkg != null ? pkg.deletedDiagnsis() : null,
                    pkg != null ? pkg.chartEventModel() : null);
            JsonTouchAuditLogger.success(endpoint, traceId, () -> "documentPk=" + retPk);
            return String.valueOf(retPk);
        } catch (WebApplicationException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw JsonTouchAuditLogger.failure(LOGGER, endpoint, traceId, e);
        }
    }
    // S.Oh 2014/02/06 Add End
    
    @POST
    @Path("/document")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.TEXT_PLAIN)
    public String postDocument(@Context HttpServletRequest servletReq,
            @QueryParam("dryRun") @DefaultValue("false") boolean dryRun,
            String json) {
        return handleDocumentPayload("POST /jtouch/document", json, IDocument.class, IDocument::toModel, dryRun, servletReq);
    }

    public String postDocument(boolean dryRun, String json) {
        return postDocument(null, dryRun, json);
    }
    
    // S.Oh 2014/02/06 iPadのFreeText対応 Add Start
    @POST
    @Path("/document2")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.TEXT_PLAIN)
    public String postDocument2(@Context HttpServletRequest servletReq,
            @QueryParam("dryRun") @DefaultValue("false") boolean dryRun,
            String json) {
        return handleDocumentPayload("POST /jtouch/document2", json, IDocument2.class, IDocument2::toModel, dryRun, servletReq);
    }

    public String postDocument2(boolean dryRun, String json) {
        return postDocument2(null, dryRun, json);
    }
    // S.Oh 2014/02/06 Add End
    
    @POST
    @Path("/mkdocument")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.TEXT_PLAIN)
    public String postMkDocument(@Context HttpServletRequest servletReq,
            @QueryParam("dryRun") @DefaultValue("false") boolean dryRun,
            String json) {
        return handleDocumentPayload("POST /jtouch/mkdocument", json, IMKDocument.class, IMKDocument::toModel, dryRun, servletReq);
    }

    public String postMkDocument(boolean dryRun, String json) {
        return postMkDocument(null, dryRun, json);
    }
    
    // S.Oh 2014/02/06 iPadのFreeText対応 Add Start
    @POST
    @Path("/mkdocument2")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.TEXT_PLAIN)
    public String postMkDocument2(@Context HttpServletRequest servletReq,
            @QueryParam("dryRun") @DefaultValue("false") boolean dryRun,
            String json) {
        return handleDocumentPayload("POST /jtouch/mkdocument2", json, IMKDocument2.class, IMKDocument2::toModel, dryRun, servletReq);
    }

    public String postMkDocument2(boolean dryRun, String json) {
        return postMkDocument2(null, dryRun, json);
    }

    private <T> String handleDocumentPayload(String endpoint, String json, Class<T> payloadType,
            Function<T, DocumentModel> converter, boolean dryRun, HttpServletRequest servletReq) {
        final String traceId = JsonTouchAuditLogger.begin(servletReq, endpoint,
                () -> "payloadSize=" + (json != null ? json.length() : 0));
        try {
            T payload = touchJsonConverter.readLegacy(json, payloadType);
            DocumentModel model = converter.apply(payload);
            if (!dryRun) {
                prepareDocumentForPersist(model, servletReq);
            }
            long pk = dryRun ? resolveDryRunDocumentPk(model) : sharedService.saveDocument(model);
            JsonTouchAuditLogger.success(endpoint, traceId,
                    () -> dryRun ? "dryRun=true,documentPk=" + pk : "documentPk=" + pk);
            return String.valueOf(pk);
        } catch (WebApplicationException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw JsonTouchAuditLogger.failure(LOGGER, endpoint, traceId, e);
        }
    }

    private String sanitizeDocumentForPersist(DocumentModel model, HttpServletRequest servletReq, UserModel actorUserModel) {
        prepareDocumentForPersist(model, servletReq, actorUserModel);
        return extractPatientId(model.getDocInfoModel());
    }

    private void prepareDocumentForPersist(DocumentModel model, HttpServletRequest servletReq, UserModel actorUserModel) {
        if (model == null) {
            throw new BadRequestException("Document payload is required.");
        }
        Date now = new Date();
        ensureEntryDefaults(model, now);
        UserModel user = ensureUser(model, servletReq, actorUserModel);
        KarteBean karte = ensureKarte(model, servletReq);
        ensureDocInfoDefaults(model, now, karte);
        ensureModuleDefaults(model, now, user, karte);
        ensureSchemaDefaults(model, now, user, karte);
        ensureAttachmentDefaults(model, now, user, karte);
    }

    private void prepareDocumentForPersist(DocumentModel model, HttpServletRequest servletReq) {
        prepareDocumentForPersist(model, servletReq, null);
    }

    private void ensureEntryDefaults(KarteEntryBean entry, Date now) {
        if (entry.getConfirmed() == null) {
            entry.setConfirmed(now);
        }
        if (entry.getStarted() == null) {
            entry.setStarted(entry.getConfirmed());
        }
        if (entry.getRecorded() == null) {
            entry.setRecorded(now);
        }
        if (!hasText(entry.getStatus())) {
            entry.setStatus(IInfoModel.STATUS_FINAL);
        }
    }

    private UserModel ensureUser(DocumentModel model, HttpServletRequest servletReq, UserModel providedActorUser) {
        UserModel user = providedActorUser;
        if (servletReq != null) {
            if (user == null) {
                user = requireActorUserModel(servletReq);
            }
            model.setUserModel(user);
            return user;
        }
        if (user != null) {
            model.setUserModel(user);
            return user;
        }
        user = model.getUserModel();
        if (user != null) {
            return user;
        }
        if (servletReq == null) {
            UserModel fallback = new UserModel();
            fallback.setUserId("touch-placeholder");
            model.setUserModel(fallback);
            return fallback;
        }
        throw new BadRequestException("userModel is required when dryRun=false");
    }

    private KarteBean ensureKarte(DocumentModel model, HttpServletRequest servletReq) {
        String patientId = extractPatientId(model.getDocInfoModel());
        if (servletReq != null) {
            if (!hasText(patientId)) {
                throw restError(servletReq, Response.Status.BAD_REQUEST,
                        "patient_id_required", "patientId is required.");
            }
            String facilityId = requireActorFacility(servletReq);
            KarteBean resolved = sharedService.findKarteByPatient(facilityId, patientId);
            if (resolved == null) {
                throw restError(servletReq, Response.Status.NOT_FOUND,
                        "not_found", "Requested resource was not found.");
            }
            model.setKarte(resolved);
            return resolved;
        }
        KarteBean karte = model.getKarte();
        if (karte != null && karte.getId() > 0L) {
            return karte;
        }
        if (servletReq == null) {
            KarteBean fallback = new KarteBean();
            fallback.setId(0L);
            model.setKarte(fallback);
            return fallback;
        }
        throw new BadRequestException("karte reference is required when dryRun=false");
    }

    private void ensureDocInfoDefaults(DocumentModel model, Date now, KarteBean karte) {
        DocInfoModel docInfo = model.getDocInfoModel();
        if (docInfo == null) {
            docInfo = new DocInfoModel();
            model.setDocInfoModel(docInfo);
        }
        model.setId(0L);
        docInfo.setDocPk(0L);
        if (!hasText(docInfo.getDocId())) {
            docInfo.setDocId(generateDocId());
        }
        if (!hasText(docInfo.getDocType())) {
            docInfo.setDocType(IInfoModel.DOCTYPE_KARTE);
        }
        if (!hasText(docInfo.getTitle())) {
            docInfo.setTitle("Touch Document");
        }
        if (!hasText(docInfo.getPurpose())) {
            docInfo.setPurpose("SOAP");
        }
        if (docInfo.getConfirmDate() == null) {
            docInfo.setConfirmDate(model.getConfirmed());
        }
        if (docInfo.getFirstConfirmDate() == null) {
            docInfo.setFirstConfirmDate(model.getStarted());
        }
        if (!hasText(docInfo.getStatus())) {
            docInfo.setStatus(model.getStatus());
        }
        if (!hasText(docInfo.getAdmFlag())) {
            docInfo.setAdmFlag("O");
        }
        if (!hasText(docInfo.getPatientId()) && karte != null && karte.getPatientModel() != null) {
            docInfo.setPatientId(karte.getPatientModel().getPatientId());
        }
        if (!hasText(docInfo.getPatientName()) && karte != null && karte.getPatientModel() != null) {
            docInfo.setPatientName(karte.getPatientModel().getFullName());
        }
        if (!hasText(docInfo.getPatientGender()) && karte != null && karte.getPatientModel() != null) {
            docInfo.setPatientGender(karte.getPatientModel().getGender());
        }
    }

    private void ensureModuleDefaults(DocumentModel model, Date now, UserModel user, KarteBean karte) {
        List<ModuleModel> modules = model.getModules();
        if (modules == null || modules.isEmpty()) {
            return;
        }
        for (int i = 0; i < modules.size(); i++) {
            ModuleModel module = modules.get(i);
            if (module == null) {
                continue;
            }
            ensureEntryDefaults(module, now);
            module.setUserModel(user);
            module.setKarteBean(karte);
            module.setDocumentModel(model);
            module.setId(0L);
            ModuleInfoBean info = module.getModuleInfoBean();
            if (info != null) {
                if (info.getStampNumber() == 0) {
                    info.setStampNumber(i);
                }
                if (!hasText(info.getPerformFlag())) {
                    info.setPerformFlag("1");
                }
            }
        }
    }

    private void ensureSchemaDefaults(DocumentModel model, Date now, UserModel user, KarteBean karte) {
        List<SchemaModel> schemas = model.getSchema();
        if (schemas == null || schemas.isEmpty()) {
            return;
        }
        for (SchemaModel schema : schemas) {
            if (schema == null) {
                continue;
            }
            ensureEntryDefaults(schema, now);
            schema.setUserModel(user);
            schema.setKarteBean(karte);
            schema.setDocumentModel(model);
            schema.setId(0L);
        }
    }

    private void ensureAttachmentDefaults(DocumentModel model, Date now, UserModel user, KarteBean karte) {
        List<AttachmentModel> attachments = model.getAttachment();
        if (attachments == null || attachments.isEmpty()) {
            return;
        }
        for (AttachmentModel attachment : attachments) {
            if (attachment == null) {
                continue;
            }
            ensureEntryDefaults(attachment, now);
            attachment.setUserModel(user);
            attachment.setKarteBean(karte);
            attachment.setDocumentModel(model);
            attachment.setId(0L);
        }
    }

    private String extractPatientId(DocInfoModel docInfo) {
        if (docInfo == null) {
            return null;
        }
        String patientId = docInfo.getPatientId();
        return hasText(patientId) ? patientId : null;
    }

    private UserModel requireActorUserModel(HttpServletRequest servletReq) {
        String actorUser = requireRemoteUser(servletReq);
        UserModel actorUserModel = sharedService.findUserModel(actorUser);
        if (actorUserModel == null) {
            throw restError(servletReq, Response.Status.UNAUTHORIZED,
                    "unauthorized", "Authentication required.");
        }
        return actorUserModel;
    }

    private void sanitizeDiagnosisPayload(DiagnosisSendWrapper wrapper,
            String actorFacility,
            UserModel actorUserModel,
            KarteBean resolvedKarte,
            String documentPatientId,
            HttpServletRequest servletReq) {
        if (wrapper == null) {
            return;
        }
        String wrapperPatientId = hasText(wrapper.getPatientId()) ? wrapper.getPatientId().trim() : null;
        if (documentPatientId != null && wrapperPatientId != null && !documentPatientId.equals(wrapperPatientId)) {
            throw restError(servletReq, Response.Status.BAD_REQUEST,
                    "patient_mismatch", "document patientId and diagnosis patientId must match.");
        }
        KarteBean karte = resolvedKarte;
        if (karte == null) {
            if (!hasText(wrapperPatientId)) {
                throw restError(servletReq, Response.Status.BAD_REQUEST,
                        "patient_id_required", "patientId is required.");
            }
            karte = sharedService.findKarteByPatient(actorFacility, wrapperPatientId);
            if (karte == null) {
                throw restError(servletReq, Response.Status.NOT_FOUND,
                        "not_found", "Requested resource was not found.");
            }
        }
        applyDiagnosisDefaults(wrapper.getAddedDiagnosis(), actorFacility, actorUserModel, karte, servletReq);
        applyDiagnosisDefaults(wrapper.getUpdatedDiagnosis(), actorFacility, actorUserModel, karte, servletReq);
    }

    private void applyDiagnosisDefaults(List<RegisteredDiagnosisModel> list,
            String actorFacility,
            UserModel actorUserModel,
            KarteBean karte,
            HttpServletRequest servletReq) {
        if (list == null || list.isEmpty()) {
            return;
        }
        for (RegisteredDiagnosisModel diagnosis : list) {
            if (diagnosis == null) {
                continue;
            }
            if (diagnosis.getId() > 0L) {
                ensureFacilityMatchOr404(actorFacility,
                        karteServiceBean.findFacilityIdByDiagnosisId(diagnosis.getId()),
                        "diagnosisId",
                        diagnosis.getId(),
                        servletReq);
            }
            diagnosis.setUserModel(actorUserModel);
            diagnosis.setKarte(karte);
        }
    }

    private void validateDeletedDiagnosisFacilities(String actorFacility, List<String> deletedDiagnosis, HttpServletRequest servletReq) {
        if (deletedDiagnosis == null || deletedDiagnosis.isEmpty()) {
            return;
        }
        for (String raw : deletedDiagnosis) {
            if (!hasText(raw)) {
                throw restError(servletReq, Response.Status.BAD_REQUEST,
                        "diagnosis_id_invalid", "diagnosisId must be numeric.");
            }
            long diagnosisId;
            try {
                diagnosisId = Long.parseLong(raw.trim());
            } catch (NumberFormatException ex) {
                throw restError(servletReq, Response.Status.BAD_REQUEST,
                        "diagnosis_id_invalid", "diagnosisId must be numeric.");
            }
            ensureFacilityMatchOr404(actorFacility,
                    karteServiceBean.findFacilityIdByDiagnosisId(diagnosisId),
                    "diagnosisId",
                    diagnosisId,
                    servletReq);
        }
    }

    private String generateDocId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private long resolveDryRunDocumentPk(DocumentModel model) {
        if (model == null) {
            return 0L;
        }
        DocInfoModel info = model.getDocInfoModel();
        if (info != null && info.getDocPk() > 0L) {
            return info.getDocPk();
        }
        long id = model.getId();
        return id > 0L ? id : 0L;
    }
}
