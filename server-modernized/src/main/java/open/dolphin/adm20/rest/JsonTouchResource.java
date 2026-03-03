package open.dolphin.adm20.rest;

import java.io.IOException;
import java.util.List;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import open.dolphin.converter.UserModelConverter;
import open.dolphin.infomodel.AttachmentModel;
import open.dolphin.infomodel.DocInfoModel;
import open.dolphin.infomodel.DocumentModel;
import open.dolphin.infomodel.VisitPackage;
import open.dolphin.infomodel.PatientModel;
import open.dolphin.adm20.converter.IPatientList;
import open.dolphin.adm20.converter.IPatientModel;
import open.dolphin.adm20.converter.ISendPackage;
import open.dolphin.adm20.converter.IVisitPackage;
import open.dolphin.converter.StringListConverter;
import open.dolphin.infomodel.DiagnosisSendWrapper;
import open.dolphin.infomodel.KarteBean;
import open.dolphin.infomodel.ModuleModel;
import open.dolphin.infomodel.PatientList;
import open.dolphin.infomodel.RegisteredDiagnosisModel;
import open.dolphin.infomodel.SchemaModel;
import open.dolphin.infomodel.StringList;
import open.dolphin.infomodel.UserModel;
import open.dolphin.touch.JsonTouchSharedService;
import open.dolphin.adm20.converter.ISendPackage2;
import open.dolphin.touch.support.TouchJsonConverter;
import open.dolphin.session.KarteServiceBean;

/**
 *
 * @author Kazushi Minagawa.
 */
@Path("/20/adm/jtouch")
public class JsonTouchResource extends open.dolphin.rest.AbstractResource {
    
    @Inject
    private JsonTouchSharedService sharedService;

    @Inject
    private TouchJsonConverter touchJsonConverter;

    @Inject
    private KarteServiceBean karteServiceBean;
    
//minagawa^ 2013/08/29
    //@Resource(mappedName="java:jboss/datasources/OrcaDS")
    //private DataSource ds;
//minagawa$
    
    @GET
    @Path("/user/{uid}")
    @Produces(MediaType.APPLICATION_JSON)
    public UserModelConverter getUserById(@PathParam("uid") String uid) {
        return sharedService.getUserById(uid);
    }

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
        String[] params = param.split(",");
        int first = Integer.parseInt(params[0]);
        int max = Integer.parseInt(params[1]);
        List<String> kanaList = sharedService.getPatientsWithKana(fid, first, max);
        StringList stringList = new StringList();
        stringList.setList(kanaList);
        StringListConverter converter = new StringListConverter();
        converter.setModel(stringList);
        return converter;
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

        List<PatientModel> list = sharedService.getPatientsByNameOrId(fid, name, firstResult, maxResult);

        PatientList patients = new PatientList();
        patients.setList(list);
        IPatientList response = new IPatientList();
        response.setModel(patients);

        return response;
    }  
    
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
        
        // VisitTouchでカルテ作成に必要なwrapperオブジェクト
        VisitPackage visit = sharedService.getVisitPackage(pvtPK, patientPK, docPK, mode);
        IVisitPackage conv = new IVisitPackage();
        conv.setModel(visit);
        return conv;
    }
    
    @POST
    @Path("/sendPackage")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.TEXT_PLAIN)
    public String postSendPackage(@Context HttpServletRequest servletReq, String json) throws IOException {

        ISendPackage pkg = touchJsonConverter.readLegacy(json, ISendPackage.class);
        DocumentModel model = pkg != null ? pkg.documentModel() : null;
        DiagnosisSendWrapper wrapper = pkg != null ? pkg.diagnosisSendWrapperModel() : null;
        if (wrapper != null) {
            populateDiagnosisAuditMetadata(servletReq, wrapper, "/20/adm/jtouch/sendPackage");
        }
        String actorFacility = requireActorFacility(servletReq);
        UserModel actorUserModel = requireActorUserModel(servletReq);
        String documentPatientId = null;
        KarteBean resolvedKarte = null;
        if (model != null) {
            documentPatientId = sanitizeDocumentForSendPackage(model, actorFacility, actorUserModel, servletReq);
            resolvedKarte = model.getKarte();
        }
        sanitizeDiagnosisPayload(wrapper, actorFacility, actorUserModel, resolvedKarte, documentPatientId, servletReq);
        validateDeletedDiagnosisFacilities(actorFacility, pkg != null ? pkg.deletedDiagnsis() : null, servletReq);

        long retPk = sharedService.processSendPackageElements(
                model,
                wrapper,
                pkg != null ? pkg.deletedDiagnsis() : null,
                pkg != null ? pkg.chartEventModel() : null);
        return String.valueOf(retPk);
    }
    
    // S.Oh 2014/02/06 iPadのFreeText対応 Add Start
    @POST
    @Path("/sendPackage2")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.TEXT_PLAIN)
    public String postSendPackage2(@Context HttpServletRequest servletReq, String json) throws IOException {

        ISendPackage2 pkg = touchJsonConverter.readLegacy(json, ISendPackage2.class);
        DocumentModel model = pkg != null ? pkg.documentModel() : null;
        DiagnosisSendWrapper wrapper = pkg != null ? pkg.diagnosisSendWrapperModel() : null;
        if (wrapper != null) {
            populateDiagnosisAuditMetadata(servletReq, wrapper, "/20/adm/jtouch/sendPackage2");
        }
        String actorFacility = requireActorFacility(servletReq);
        UserModel actorUserModel = requireActorUserModel(servletReq);
        String documentPatientId = null;
        KarteBean resolvedKarte = null;
        if (model != null) {
            documentPatientId = sanitizeDocumentForSendPackage(model, actorFacility, actorUserModel, servletReq);
            resolvedKarte = model.getKarte();
        }
        sanitizeDiagnosisPayload(wrapper, actorFacility, actorUserModel, resolvedKarte, documentPatientId, servletReq);
        validateDeletedDiagnosisFacilities(actorFacility, pkg != null ? pkg.deletedDiagnsis() : null, servletReq);

        long retPk = sharedService.processSendPackageElements(
                model,
                wrapper,
                pkg != null ? pkg.deletedDiagnsis() : null,
                pkg != null ? pkg.chartEventModel() : null);
        return String.valueOf(retPk);
    }
    // S.Oh 2014/02/06 Add End

    private UserModel requireActorUserModel(HttpServletRequest servletReq) {
        String actorUser = requireRemoteUser(servletReq);
        UserModel actorUserModel = sharedService.findUserModel(actorUser);
        if (actorUserModel == null) {
            throw restError(servletReq, Response.Status.UNAUTHORIZED, "unauthorized", "Authentication required.");
        }
        return actorUserModel;
    }

    private String sanitizeDocumentForSendPackage(DocumentModel model,
            String actorFacility,
            UserModel actorUserModel,
            HttpServletRequest servletReq) {
        model.setId(0L);
        DocInfoModel docInfo = model.getDocInfoModel();
        if (docInfo == null) {
            throw restError(servletReq, Response.Status.BAD_REQUEST, "patient_id_required", "patientId is required.");
        }
        docInfo.setDocPk(0L);
        String patientId = normalizeText(docInfo.getPatientId());
        if (patientId == null) {
            throw restError(servletReq, Response.Status.BAD_REQUEST, "patient_id_required", "patientId is required.");
        }
        KarteBean karte = sharedService.findKarteByPatient(actorFacility, patientId);
        if (karte == null) {
            throw restError(servletReq, Response.Status.NOT_FOUND, "not_found", "Requested resource was not found.");
        }
        model.setUserModel(actorUserModel);
        model.setKarte(karte);
        applyModuleDefaults(model, actorUserModel, karte);
        applySchemaDefaults(model, actorUserModel, karte);
        applyAttachmentDefaults(model, actorUserModel, karte);
        return patientId;
    }

    private void applyModuleDefaults(DocumentModel model, UserModel actorUserModel, KarteBean karte) {
        List<ModuleModel> modules = model.getModules();
        if (modules == null || modules.isEmpty()) {
            return;
        }
        for (ModuleModel module : modules) {
            if (module == null) {
                continue;
            }
            module.setId(0L);
            module.setUserModel(actorUserModel);
            module.setKarteBean(karte);
            module.setDocumentModel(model);
        }
    }

    private void applySchemaDefaults(DocumentModel model, UserModel actorUserModel, KarteBean karte) {
        List<SchemaModel> schemas = model.getSchema();
        if (schemas == null || schemas.isEmpty()) {
            return;
        }
        for (SchemaModel schema : schemas) {
            if (schema == null) {
                continue;
            }
            schema.setId(0L);
            schema.setUserModel(actorUserModel);
            schema.setKarteBean(karte);
            schema.setDocumentModel(model);
        }
    }

    private void applyAttachmentDefaults(DocumentModel model, UserModel actorUserModel, KarteBean karte) {
        List<AttachmentModel> attachments = model.getAttachment();
        if (attachments == null || attachments.isEmpty()) {
            return;
        }
        for (AttachmentModel attachment : attachments) {
            if (attachment == null) {
                continue;
            }
            attachment.setId(0L);
            attachment.setUserModel(actorUserModel);
            attachment.setKarteBean(karte);
            attachment.setDocumentModel(model);
        }
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
        String wrapperPatientId = normalizeText(wrapper.getPatientId());
        if (documentPatientId != null && wrapperPatientId != null && !documentPatientId.equals(wrapperPatientId)) {
            throw restError(servletReq, Response.Status.BAD_REQUEST,
                    "patient_mismatch", "document patientId and diagnosis patientId must match.");
        }
        KarteBean karte = resolvedKarte;
        if (karte == null) {
            if (wrapperPatientId == null) {
                throw restError(servletReq, Response.Status.BAD_REQUEST, "patient_id_required", "patientId is required.");
            }
            karte = sharedService.findKarteByPatient(actorFacility, wrapperPatientId);
            if (karte == null) {
                throw restError(servletReq, Response.Status.NOT_FOUND, "not_found", "Requested resource was not found.");
            }
        }
        applyDiagnosisDefaults(wrapper.getAddedDiagnosis(), actorFacility, actorUserModel, karte, servletReq);
        applyDiagnosisDefaults(wrapper.getUpdatedDiagnosis(), actorFacility, actorUserModel, karte, servletReq);
    }

    private void applyDiagnosisDefaults(List<RegisteredDiagnosisModel> diagnoses,
            String actorFacility,
            UserModel actorUserModel,
            KarteBean karte,
            HttpServletRequest servletReq) {
        if (diagnoses == null || diagnoses.isEmpty()) {
            return;
        }
        for (RegisteredDiagnosisModel diagnosis : diagnoses) {
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
            String trimmed = normalizeText(raw);
            if (trimmed == null) {
                throw restError(servletReq, Response.Status.BAD_REQUEST,
                        "diagnosis_id_invalid", "diagnosisId must be numeric.");
            }
            long diagnosisId;
            try {
                diagnosisId = Long.parseLong(trimmed);
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

    private String normalizeText(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
    
}
