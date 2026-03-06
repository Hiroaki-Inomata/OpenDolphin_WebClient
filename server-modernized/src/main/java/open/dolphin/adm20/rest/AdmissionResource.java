package open.dolphin.adm20.rest;

import java.io.IOException;
import java.io.OutputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import jakarta.inject.Inject;
import jakarta.persistence.NoResultException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import open.dolphin.adm20.PlivoSender;
import open.dolphin.adm20.session.ADM20_AdmissionServiceBean;
import open.dolphin.adm20.ICarePlanModel;
import open.dolphin.security.totp.TotpHelper;
import open.dolphin.adm20.converter.IDocument;
import open.dolphin.adm20.converter.ILastDateCount30;
import open.dolphin.adm20.converter.INurseProgressCourse;
import open.dolphin.adm20.converter.IOSHelper;
import open.dolphin.adm20.converter.IOndobanModel30;
import open.dolphin.adm20.converter.ISendPackage;
import open.dolphin.adm20.session.ADM20_EHTServiceBean;
import open.dolphin.infomodel.CarePlanModel;
import open.dolphin.infomodel.ChartEventModel;
import open.dolphin.infomodel.DiagnosisSendWrapper;
import open.dolphin.infomodel.AttachmentModel;

import open.dolphin.infomodel.DocInfoModel;
import open.dolphin.infomodel.DocumentModel;
import open.dolphin.infomodel.Factor2Code;
import open.dolphin.infomodel.Factor2Spec;
import open.dolphin.infomodel.IInfoModel;
import open.dolphin.infomodel.KarteBean;
import open.dolphin.infomodel.LastDateCount30;
import open.dolphin.infomodel.ModuleModel;
import open.dolphin.infomodel.NurseProgressCourseModel;
import open.dolphin.infomodel.OndobanModel;
import open.dolphin.infomodel.PVTHealthInsuranceModel;
import open.dolphin.infomodel.PVTPublicInsuranceItemModel;
import open.dolphin.infomodel.RegisteredDiagnosisModel;
import open.dolphin.infomodel.SMSMessage;
import open.dolphin.infomodel.SchemaModel;
import open.dolphin.infomodel.UserModel;
import open.dolphin.session.KarteServiceBean;
import open.dolphin.session.UserServiceBean;
import open.dolphin.touch.JsonTouchSharedService;
import open.orca.rest.ORCAConnection;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import open.dolphin.adm20.dto.FidoAssertionFinishRequest;
import open.dolphin.adm20.dto.FidoAssertionOptionsRequest;
import open.dolphin.adm20.dto.FidoAssertionOptionsResponse;
import open.dolphin.adm20.dto.FidoAssertionResponse;
import open.dolphin.adm20.dto.FidoRegistrationFinishRequest;
import open.dolphin.adm20.dto.FidoRegistrationOptionsRequest;
import open.dolphin.adm20.dto.FidoRegistrationOptionsResponse;
import open.dolphin.adm20.dto.TotpRegistrationRequest;
import open.dolphin.adm20.dto.TotpRegistrationResponse;
import open.dolphin.adm20.dto.TotpVerificationRequest;
import open.dolphin.adm20.dto.TotpVerificationResponse;
import open.dolphin.security.SecondFactorSecurityConfig;
import open.dolphin.security.audit.AuditEventPayload;
import open.dolphin.security.audit.SessionAuditDispatcher;
import open.dolphin.security.totp.TotpRegistrationResult;
import open.dolphin.rest.orca.AbstractOrcaRestResource;
import open.dolphin.touch.JsonTouchSharedService;



/*DocumentModel kazushi Minagawa
 */
@Path("/20/adm")
public class AdmissionResource extends open.dolphin.rest.AbstractResource {

    private static final Pattern E164_PHONE_PATTERN = Pattern.compile("^\\+?[0-9]{10,15}$");
    private static final int SMS_MIN_NUMBERS = 1;
    private static final int SMS_MAX_NUMBERS = 10;
    private static final int SMS_MAX_MESSAGE_LENGTH = 1600;
    private static final int FACTOR2_CODE_RATE_LIMIT = 5;
    private static final int FACTOR2_VERIFY_RATE_LIMIT = 10;
    private static final long FACTOR2_RATE_WINDOW_MILLIS = 5L * 60L * 1000L;
    private static final ConcurrentMap<Long, Deque<Long>> FACTOR2_CODE_RATE_BUCKETS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<Long, Deque<Long>> FACTOR2_VERIFY_RATE_BUCKETS = new ConcurrentHashMap<>();
    
    @Inject
    private ADM20_AdmissionServiceBean admissionService;
    
    @Inject
    private JsonTouchSharedService sharedService;

    @Inject
    private KarteServiceBean karteServiceBean;

    @Inject
    private UserServiceBean userServiceBean;
    
    @Inject
    private ADM20_EHTServiceBean ehtService;

    @Inject
    private SecondFactorSecurityConfig secondFactorSecurityConfig;

    @Inject
    private SessionAuditDispatcher sessionAuditDispatcher;

    @Context
    private HttpServletRequest httpRequest;

    private final ObjectMapper jsonMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Inject
    private PlivoSender plivoSender;

    // VisitTouch2 Admission Model
    
    //  /10/eht/karteNumber/
    //  /10/eht/memo/
    //  /10/eht/allergy/
    //  /10/eht/diagnosis/
    //  /10/eht/progresscourse
    //  /10/eht/module/laboTest/
    //  /10/eht/item/
    //  /10/eht/ondoban/
    //  /10/eht/ondoban
    //  /10/eht/nurseProgressCourse
    
    //  /10/adm/sendPackage 重複 ???
    //---------------------------------------------------------------------------
    
    @GET
    @Path("/carePlan/{param}")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public StreamingOutput getCarePlans(final @Context HttpServletRequest servletReq, final @PathParam("param") String param) {
        
        return new StreamingOutput() {

            @Override
            public void write(OutputStream os) throws IOException, WebApplicationException {
                
                String actorFacility = requireActorFacilityId(servletReq);
                long ptPK = parseLongOr400(param, "patientPk", servletReq);
                ensurePatientFacilityOr404(actorFacility, ptPK, servletReq);
                List<CarePlanModel> list = admissionService.getCarePlans(ptPK);
                List<ICarePlanModel> result = new ArrayList<>(list.size());
                for (CarePlanModel model : list) {
                    ICarePlanModel conv = new ICarePlanModel();
                    conv.fromModel(model);
                    result.add(conv);
                }
                ObjectMapper mapper = getSerializeMapper();
                mapper.writeValue(os, result);
            }
        };
    }
    
    @POST
    @Path("/carePlan")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public StreamingOutput postCarePlan(final @Context HttpServletRequest servletReq,final String json) {
        
        return new StreamingOutput() {
            @Override
            public void write(OutputStream os) throws IOException, WebApplicationException {
                ObjectMapper mapper = new ObjectMapper();
                ICarePlanModel conv = mapper.readValue(json, ICarePlanModel.class);
                CarePlanModel model = conv.toModel();
                long karteId = model.getKarteId();
                if (karteId <= 0L) {
                    throw restError(servletReq, Response.Status.BAD_REQUEST,
                            "karte_id_required", "karteId is required.");
                }
                ensureFacilityMatchOr404(
                        requireActorFacilityId(servletReq),
                        karteServiceBean.findFacilityIdByKarteId(karteId),
                        "karteId",
                        karteId,
                        servletReq);
                UserModel actorUserModel = requireActorUserModel(servletReq);
                model.setId(0L);
                model.setUserId(requireActorUserId(servletReq));
                model.setCommonName(actorUserModel.getCommonName());
                long pk = admissionService.addCarePlan(model);
                List<Long> result = new ArrayList<>(1);
                result.add(pk);
                mapper = getSerializeMapper();
                mapper.writeValue(os, result);
            }
        };
    }
    
    @PUT
    @Path("/carePlan")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public StreamingOutput putCarePlan(final String json) {
        
        return new StreamingOutput() {
            @Override
            public void write(OutputStream os) throws IOException, WebApplicationException {
                ObjectMapper mapper = new ObjectMapper();
                ICarePlanModel conv = mapper.readValue(json, ICarePlanModel.class);
                CarePlanModel model = conv.toModel();
                long id = model.getId();
                if (id <= 0L) {
                    throw restError(httpRequest, Response.Status.BAD_REQUEST,
                            "care_plan_id_required", "carePlan id is required.");
                }
                CarePlanModel existing = admissionService.findCarePlanById(id);
                if (existing == null) {
                    throw restError(httpRequest, Response.Status.NOT_FOUND, "not_found",
                            "Requested resource was not found.");
                }
                ensureFacilityMatchOr404(
                        requireActorFacilityId(httpRequest),
                        karteServiceBean.findFacilityIdByKarteId(existing.getKarteId()),
                        "carePlanId",
                        id,
                        httpRequest);
                UserModel actorUserModel = requireActorUserModel(httpRequest);
                model.setKarteId(existing.getKarteId());
                model.setUserId(requireActorUserId(httpRequest));
                model.setCommonName(actorUserModel.getCommonName());
                int cnt = admissionService.updateCarePlan(model);
                List<Integer> result = new ArrayList<>(1);
                result.add(cnt);
                mapper = getSerializeMapper();
                mapper.writeValue(os, result);
            }
        };
    }
    
    @DELETE
    @Path("/carePlan")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public StreamingOutput deleteCarePlan(final String json) {
        
        return new StreamingOutput() {
            @Override
            public void write(OutputStream os) throws IOException, WebApplicationException {
                ObjectMapper mapper = new ObjectMapper();
                ICarePlanModel conv = mapper.readValue(json, ICarePlanModel.class);
                CarePlanModel model = conv.toModel();
                long id = model.getId();
                if (id <= 0L) {
                    throw restError(httpRequest, Response.Status.BAD_REQUEST,
                            "care_plan_id_required", "carePlan id is required.");
                }
                CarePlanModel existing = admissionService.findCarePlanById(id);
                if (existing == null) {
                    throw restError(httpRequest, Response.Status.NOT_FOUND,
                            "not_found", "Requested resource was not found.");
                }
                ensureFacilityMatchOr404(
                        requireActorFacilityId(httpRequest),
                        karteServiceBean.findFacilityIdByKarteId(existing.getKarteId()),
                        "carePlanId",
                        id,
                        httpRequest);
                model.setKarteId(existing.getKarteId());
                int cnt = admissionService.deleteCarePlan(model);
                List<Integer> result = new ArrayList<>(1);
                result.add(cnt);
                mapper = getSerializeMapper();
                mapper.writeValue(os, result);
            }
        };
    }
    
    @GET
    @Path("/lastDateCount/{param}")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public StreamingOutput getLastDateCount(final @PathParam("param") String param) {
        
        return new StreamingOutput() {
            @Override
            public void write(OutputStream os) throws IOException, WebApplicationException {
                String[] params = param.split(",");
                if (params.length < 3) {
                    throw restError(httpRequest, Response.Status.BAD_REQUEST,
                            "param_invalid", "param must be patientPk,fid,pid.");
                }
                String actorFacility = requireActorFacilityId(httpRequest);
                long ptPK = parseLongOr400(params[0], "patientPk", httpRequest);
                ensurePatientFacilityOr404(actorFacility, ptPK, httpRequest);
                String patientId = normalizeText(params[2]);
                if (patientId == null) {
                    throw restError(httpRequest, Response.Status.BAD_REQUEST,
                            "patient_id_required", "patientId is required.");
                }
                String fidPid = actorFacility + IInfoModel.COMPOSITE_KEY_MAKER + patientId;
                LastDateCount30 data = admissionService.getLastDateCount(ptPK, fidPid);
                ILastDateCount30 result = new ILastDateCount30();
                result.fromModel(data);
                ObjectMapper mapper = getSerializeMapper();
                mapper.writeValue(os, result);
            }
        };
    }
    
    @GET
    @Path("/docid/{param}")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public StreamingOutput getDocIdList(final @PathParam("param") String param) {
        
        return new StreamingOutput() {
            @Override
            public void write(OutputStream os) throws IOException, WebApplicationException {
                String[] params = param.split(",");
                if (params.length < 2) {
                    throw restError(httpRequest, Response.Status.BAD_REQUEST,
                            "param_invalid", "ptPK and startDate are required.");
                }
                String actorFacility = requireActorFacilityId(httpRequest);
                long ptPK = parseLongOr400(params[0], "patientPk", httpRequest);
                ensurePatientFacilityOr404(actorFacility, ptPK, httpRequest);
                Date startDate = IOSHelper.toDate(params[1]);
                if (startDate == null) {
                    throw restError(httpRequest, Response.Status.BAD_REQUEST,
                            "start_date_invalid", "startDate must be valid.");
                }
                Collection<Long> result = admissionService.getDocIdList(ptPK, startDate);
                ObjectMapper mapper = getSerializeMapper();
                mapper.writeValue(os, result);
            }
        };
    }
    
    @GET
    @Path("/document/{param}")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public StreamingOutput getDocument(final @PathParam("param") String param) {
        
        return new StreamingOutput() {
            @Override
            public void write(OutputStream os) throws IOException, WebApplicationException {
                String actorFacility = requireActorFacilityId(httpRequest);
                long docPK = parseLongOr400(param, "docPk", httpRequest);
                ensureDocFacilityOr404(actorFacility, docPK, httpRequest);
                DocumentModel doc = admissionService.getDocumentByPk(docPK);
                if (doc == null) {
                    throw restError(httpRequest, Response.Status.NOT_FOUND,
                            "not_found", "Requested resource was not found.");
                }
                doc.toDetuch();
                IDocument conv = new IDocument();
                conv.fromModel(doc);
                ObjectMapper mapper = getSerializeMapper();
                mapper.writeValue(os, conv);
            }
        };
    }
    
    @POST
    @Path("/sendPackage")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.TEXT_PLAIN)
    public String postSendPackage(@Context HttpServletRequest servletReq, String json) throws IOException {

        ObjectMapper mapper = new ObjectMapper();
        ISendPackage pkg = mapper.readValue(json, ISendPackage.class);

        DocumentModel model = pkg != null ? pkg.documentModel() : null;
        DiagnosisSendWrapper wrapper = pkg != null ? pkg.diagnosisSendWrapperModel() : null;
        if (wrapper != null) {
            populateDiagnosisAuditMetadata(servletReq, wrapper, "/20/adm/sendPackage");
        }

        String actorFacility = requireActorFacilityId(servletReq);
        UserModel actorUserModel = requireActorUserModel(servletReq);
        String documentPatientId = null;
        KarteBean resolvedKarte = null;
        if (model != null) {
            documentPatientId = sanitizeDocumentForSendPackage(model, actorFacility, actorUserModel, servletReq);
            resolvedKarte = model.getKarte();
        }
        sanitizeDiagnosisPayload(wrapper, actorFacility, actorUserModel, resolvedKarte, documentPatientId, servletReq);
        List<String> deletedDiagnosis = pkg != null ? pkg.deletedDiagnsis() : null;
        validateDeletedDiagnosisFacilities(actorFacility, deletedDiagnosis, servletReq);
        ChartEventModel chartEvent = pkg != null ? pkg.chartEventModel() : null;
        if (chartEvent != null) {
            chartEvent.setFacilityId(actorFacility);
        }

        long retPk = sharedService.processSendPackageElements(model, wrapper, deletedDiagnosis, chartEvent);
        return String.valueOf(retPk);
    }
    
    private Date dateFromString(String raw) {
        String str = normalizeText(raw);
        if (str == null) {
            return null;
        }
        if (str.length() > 10) {
            str = str.substring(0, 10);
        }
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        format.setLenient(false);
        try {
            return format.parse(str);
        } catch (ParseException ex) {
            Logger.getLogger(AdmissionResource.class.getName())
                    .log(Level.WARNING, "Invalid date: " + raw, ex);
        }
        return null;
    }
    
//--------------------------------------------------------------------------------    
    /* VisitTouch 1.5 リソース
    -/jtouch/visitpackage/
    -/jtouch/user/
    -/touch/stampTree/
    -/touch/stamp/
    -/jtouch/patients/name/
    -/jtouch/sendPackage
    -/10/eht/order/
    /10/eht/interaction
    */
//--------------------------------------------------------------------------------  
    
    //--------------------------------------------------------------------------------------- 
    // 温度板対応
    //---------------------------------------------------------------------------------------
    @GET
    @Path("/ondoban/{param}")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public StreamingOutput getOndoban(final @PathParam("param") String param) {
        
        return new StreamingOutput() {
            @Override
            public void write(OutputStream os) throws IOException, WebApplicationException {
                String[] params = param.split(",");
                if (params.length < 3) {
                    throw restError(httpRequest, Response.Status.BAD_REQUEST,
                            "param_invalid", "patientPk, fromDate and toDate are required.");
                }
                String actorFacility = requireActorFacilityId(httpRequest);
                long pk = parseLongOr400(params[0], "patientPk", httpRequest);
                ensurePatientFacilityOr404(actorFacility, pk, httpRequest);
                Date fromDate = IOSHelper.toDate(params[1]);
                Date toDate = IOSHelper.toDate(params[2]);
                if (fromDate == null || toDate == null) {
                    throw restError(httpRequest, Response.Status.BAD_REQUEST,
                            "date_invalid", "fromDate/toDate must be valid.");
                }

                List<OndobanModel> list = ehtService.getOndoban(pk, fromDate, toDate);
                List<IOndobanModel30> result = new ArrayList<>();
                for (OndobanModel m : list) {
                    IOndobanModel30 om = new IOndobanModel30();
                    om.fromModel(m);
                    result.add(om);
                }

                ObjectMapper mapper = getSerializeMapper();
                mapper.writeValue(os, result);
            }
        };
    }
    @POST
    @Path("/ondoban")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public StreamingOutput postOndoban(final String json) {
        
        return new StreamingOutput() {
            @Override
            public void write(OutputStream os) throws IOException, WebApplicationException {
                String actorFacility = requireActorFacilityId(httpRequest);
                UserModel actorUserModel = requireActorUserModel(httpRequest);
                ObjectMapper mapper = new ObjectMapper();
                mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
                IOndobanModel30[] array = mapper.readValue(json, IOndobanModel30[].class);

                ArrayList<OndobanModel> saveList = new ArrayList<>(array.length);
                for (IOndobanModel30 am : array) {
                    OndobanModel om = am.toModel();
                    long karteId = requireKarteIdOr400(om.getKarte(), "karteId", httpRequest);
                    ensureFacilityMatchOr404(
                            actorFacility,
                            karteServiceBean.findFacilityIdByKarteId(karteId),
                            "karteId",
                            karteId,
                            httpRequest);
                    om.setId(0L);
                    om.setUserModel(actorUserModel);
                    saveList.add(om);
                }

                List<Long> pkList = ehtService.addOndoban(saveList);
                mapper = getSerializeMapper();
                mapper.writeValue(os, pkList);
            }
        };
    }
    @PUT
    @Path("/ondoban")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public StreamingOutput updateOndoban(final String json) {
        
        return new StreamingOutput() {
            @Override
            public void write(OutputStream os) throws IOException, WebApplicationException {
                String actorFacility = requireActorFacilityId(httpRequest);
                UserModel actorUserModel = requireActorUserModel(httpRequest);
                ObjectMapper mapper = new ObjectMapper();
                mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
                IOndobanModel30[] array = mapper.readValue(json, IOndobanModel30[].class);

                ArrayList<OndobanModel> updateList = new ArrayList<>(array.length);
                for (IOndobanModel30 am : array) {
                    OndobanModel om = am.toModel();
                    long ondobanId = om.getId();
                    if (ondobanId <= 0L) {
                        throw restError(httpRequest, Response.Status.BAD_REQUEST,
                                "ondoban_id_required", "ondoban id is required.");
                    }
                    ensureFacilityMatchOr404(
                            actorFacility,
                            karteServiceBean.findFacilityIdByOndobanId(ondobanId),
                            "ondobanId",
                            ondobanId,
                            httpRequest);
                    om.setUserModel(actorUserModel);
                    updateList.add(om);
                }

                int cnt = ehtService.updateOndoban(updateList);
                mapper = getSerializeMapper();
                mapper.writeValue(os, String.valueOf(cnt));
            }
        };
    }
    @DELETE
    @Path("/ondoban")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public StreamingOutput deleteOndoban(final String json) {
        
        return new StreamingOutput() {
            @Override
            public void write(OutputStream os) throws IOException, WebApplicationException {
                String actorFacility = requireActorFacilityId(httpRequest);
                UserModel actorUserModel = requireActorUserModel(httpRequest);
                ObjectMapper mapper = new ObjectMapper();
                mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
                IOndobanModel30[] array = mapper.readValue(json, IOndobanModel30[].class);

                ArrayList<OndobanModel> updateList = new ArrayList<>(array.length);
                for (IOndobanModel30 am : array) {
                    OndobanModel om = am.toModel();
                    long ondobanId = om.getId();
                    if (ondobanId <= 0L) {
                        throw restError(httpRequest, Response.Status.BAD_REQUEST,
                                "ondoban_id_required", "ondoban id is required.");
                    }
                    ensureFacilityMatchOr404(
                            actorFacility,
                            karteServiceBean.findFacilityIdByOndobanId(ondobanId),
                            "ondobanId",
                            ondobanId,
                            httpRequest);
                    om.setUserModel(actorUserModel);
                    updateList.add(om);
                }

                int cnt = ehtService.deleteOndoban(updateList);
                mapper = getSerializeMapper();
                mapper.writeValue(os, String.valueOf(cnt));
            }
        };
    }
    //--------------------------------------------------------------------------------------- 
    // 看護記録
    //---------------------------------------------------------------------------------------
    @GET
    @Path("/nurseProgressCourse/{param}")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public StreamingOutput getNurseProgressCourse(final @PathParam("param") String param) {
        
        return new StreamingOutput() {
            @Override
            public void write(OutputStream os) throws IOException, WebApplicationException {
                String[] params = param.split(",");
                if (params.length < 3) {
                    throw restError(httpRequest, Response.Status.BAD_REQUEST,
                            "param_invalid", "patientPk, firstResult and maxResult are required.");
                }
                String actorFacility = requireActorFacilityId(httpRequest);
                long pk = parseLongOr400(params[0], "patientPk", httpRequest);
                ensurePatientFacilityOr404(actorFacility, pk, httpRequest);
                int firstResult;
                int maxResult;
                try {
                    firstResult = Integer.parseInt(params[1]);
                    maxResult = Integer.parseInt(params[2]);
                } catch (NumberFormatException ex) {
                    throw restError(httpRequest, Response.Status.BAD_REQUEST,
                            "param_invalid", "firstResult/maxResult must be numeric.");
                }

                List<NurseProgressCourseModel> list = ehtService.getNurseProgressCourse(pk, firstResult, maxResult);
                List<INurseProgressCourse> result = new ArrayList<>();
                for (NurseProgressCourseModel model : list) {
                    INurseProgressCourse conv = new INurseProgressCourse();
                    conv.fromModel(model);
                    result.add(conv);
                }

                ObjectMapper mapper = getSerializeMapper();
                mapper.writeValue(os, result);
            }
        };
    }
    @POST
    @Path("/nurseProgressCourse")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public StreamingOutput postNurseProgressCourse(final String json) {
        
        return new StreamingOutput() {
            @Override
            public void write(OutputStream os) throws IOException, WebApplicationException {
                String actorFacility = requireActorFacilityId(httpRequest);
                UserModel actorUserModel = requireActorUserModel(httpRequest);
                ObjectMapper mapper = new ObjectMapper();
                mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
                INurseProgressCourse conv = mapper.readValue(json, INurseProgressCourse.class);

                NurseProgressCourseModel model = conv.toModel();
                long karteId = requireKarteIdOr400(model.getKarte(), "karteId", httpRequest);
                ensureFacilityMatchOr404(
                        actorFacility,
                        karteServiceBean.findFacilityIdByKarteId(karteId),
                        "karteId",
                        karteId,
                        httpRequest);
                model.setId(0L);
                model.setUserModel(actorUserModel);
                Long pk = ehtService.addNurseProgressCourse(model);
                List<Long> pkList = new ArrayList<>(1);
                pkList.add(pk);

                mapper = getSerializeMapper();
                mapper.writeValue(os, pkList);
            }
        };
    }
    @PUT
    @Path("/nurseProgressCourse")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public StreamingOutput updateNurseProgressCourse(final String json) {
        
        return new StreamingOutput() {
            @Override
            public void write(OutputStream os) throws IOException, WebApplicationException {
                String actorFacility = requireActorFacilityId(httpRequest);
                UserModel actorUserModel = requireActorUserModel(httpRequest);
                ObjectMapper mapper = new ObjectMapper();
                mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
                INurseProgressCourse conv = mapper.readValue(json, INurseProgressCourse.class);

                NurseProgressCourseModel model = conv.toModel();
                long nurseProgressCourseId = model.getId();
                if (nurseProgressCourseId <= 0L) {
                    throw restError(httpRequest, Response.Status.BAD_REQUEST,
                            "nurse_progress_course_id_required", "nurseProgressCourse id is required.");
                }
                ensureFacilityMatchOr404(
                        actorFacility,
                        karteServiceBean.findFacilityIdByNurseProgressCourseId(nurseProgressCourseId),
                        "nurseProgressCourseId",
                        nurseProgressCourseId,
                        httpRequest);
                model.setUserModel(actorUserModel);
                int cnt = ehtService.updateNurseProgressCourse(model);

                mapper = getSerializeMapper();
                mapper.writeValue(os, String.valueOf(cnt));
            }
        };
    }
    @DELETE
    @Path("/nurseProgressCourse")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public StreamingOutput deleteNurseProgressCourse(final String json) {
        
        return new StreamingOutput() {
            @Override
            public void write(OutputStream os) throws IOException, WebApplicationException {
                String actorFacility = requireActorFacilityId(httpRequest);
                UserModel actorUserModel = requireActorUserModel(httpRequest);
                ObjectMapper mapper = new ObjectMapper();
                mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
                INurseProgressCourse conv = mapper.readValue(json, INurseProgressCourse.class);

                NurseProgressCourseModel model = conv.toModel();
                long nurseProgressCourseId = model.getId();
                if (nurseProgressCourseId <= 0L) {
                    throw restError(httpRequest, Response.Status.BAD_REQUEST,
                            "nurse_progress_course_id_required", "nurseProgressCourse id is required.");
                }
                ensureFacilityMatchOr404(
                        actorFacility,
                        karteServiceBean.findFacilityIdByNurseProgressCourseId(nurseProgressCourseId),
                        "nurseProgressCourseId",
                        nurseProgressCourseId,
                        httpRequest);
                model.setUserModel(actorUserModel);
                int cnt = ehtService.deleteNurseProgressCourse(model);

                mapper = getSerializeMapper();
                mapper.writeValue(os, String.valueOf(cnt));
            }
        };
    }
    
    @PUT
    @Path("/sms/message")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public StreamingOutput sendSMSMessage(final String json) {
        
        return new StreamingOutput() {

            @Override
            public void write(OutputStream output) throws IOException, WebApplicationException {
                long actorUserPk = resolveActorUserPkForAudit();
                List<String> normalized = new ArrayList<>();
                int messageLength = 0;
                try {
                    requireAdmin(httpRequest, userServiceBean);
                    actorUserPk = requireActorUserPk(httpRequest);
                    ObjectMapper mapper = new ObjectMapper();
                    SMSMessage sms = mapper.readValue(json, SMSMessage.class);

                    List<String> numbers = sms != null && sms.getNumbers() != null ? sms.getNumbers() : List.of();
                    if (numbers.size() < SMS_MIN_NUMBERS || numbers.size() > SMS_MAX_NUMBERS) {
                        throw restError(httpRequest, Response.Status.BAD_REQUEST,
                                "numbers_invalid", "numbers size must be 1..10.");
                    }
                    for (String raw : numbers) {
                        String value = normalizeText(raw);
                        if (value == null || !E164_PHONE_PATTERN.matcher(value).matches()) {
                            throw restError(httpRequest, Response.Status.BAD_REQUEST,
                                    "number_invalid", "number must match E.164 style.");
                        }
                        normalized.add(value);
                    }
                    String message = sms != null ? sms.getMessage() : null;
                    String normalizedMessage = normalizeText(message);
                    if (normalizedMessage == null || normalizedMessage.length() > SMS_MAX_MESSAGE_LENGTH) {
                        throw restError(httpRequest, Response.Status.BAD_REQUEST,
                                "message_invalid", "message length must be 1..1600.");
                    }
                    messageLength = normalizedMessage.length();
                    plivoSender.send(normalized, message);

                    Map<String, Object> details = new LinkedHashMap<>();
                    details.put("count", normalized.size());
                    details.put("numbersMasked", maskPhoneNumbers(normalized));
                    details.put("messageLength", messageLength);
                    details.put("status", "success");
                    recordAudit("SMS_SEND", "/20/adm/sms/message", actorUserPk, details);

                    mapper = getSerializeMapper();
                    mapper.writeValue(output, String.valueOf(normalized.size()));
                } catch (WebApplicationException ex) {
                    Map<String, Object> details = new LinkedHashMap<>();
                    details.put("count", normalized.size());
                    details.put("numbersMasked", maskPhoneNumbers(normalized));
                    details.put("messageLength", messageLength);
                    details.put("status", "failed");
                    if (ex.getResponse() != null) {
                        details.put("httpStatus", ex.getResponse().getStatus());
                    }
                    recordAudit("SMS_SEND_FAILED", "/20/adm/sms/message", actorUserPk, details);
                    throw ex;
                } catch (RuntimeException ex) {
                    Map<String, Object> details = new LinkedHashMap<>();
                    details.put("count", normalized.size());
                    details.put("numbersMasked", maskPhoneNumbers(normalized));
                    details.put("messageLength", messageLength);
                    details.put("status", "failed");
                    details.put("reason", "internal_error");
                    recordAudit("SMS_SEND_FAILED", "/20/adm/sms/message", actorUserPk, details);
                    throw ex;
                }
            }
        };
    }
    
    @PUT
    @Path("/factor2/code")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public StreamingOutput getFactor2Code(final String json) {
        
        return new StreamingOutput() {

            @Override
            public void write(OutputStream output) throws IOException, WebApplicationException {
                long actorUserPk = requireActorUserPk(httpRequest);
                String mobileMasked = "***";
                try {
                    ObjectMapper mapper = new ObjectMapper();
                    Factor2Code spec = mapper.readValue(json, Factor2Code.class);
                    if (spec.getUserPK() > 0L && spec.getUserPK() != actorUserPk) {
                        throw restError(httpRequest, Response.Status.NOT_FOUND,
                                "not_found", "Requested resource was not found.");
                    }
                    String mobileNumber = normalizeText(spec.getMobileNumber());
                    if (mobileNumber == null || !E164_PHONE_PATTERN.matcher(mobileNumber).matches()) {
                        throw restError(httpRequest, Response.Status.BAD_REQUEST,
                                "phone_number_invalid", "phoneNumber must match E.164 style.");
                    }
                    mobileMasked = maskPhone(mobileNumber);
                    checkFactor2RateLimit(actorUserPk, FACTOR2_CODE_RATE_LIMIT, FACTOR2_CODE_RATE_BUCKETS);
                    spec.setUserPK(actorUserPk);
                    spec.setMobileNumber(mobileNumber);

                    // One time password
                    String code = TotpHelper.generateSmsCode();
                    spec.setCode(code);

                    // persist temporaly
                    ehtService.saveFactor2Code(spec);

                    // ユーザーのモバイルへ送信
                    List<String> numbers = new ArrayList<>(1);
                    numbers.add(spec.getMobileNumber());
                    plivoSender.send(numbers, code);

                    Map<String, Object> details = new LinkedHashMap<>();
                    details.put("status", "success");
                    details.put("mobileMasked", mobileMasked);
                    details.put("rateLimited", false);
                    recordAudit("FACTOR2_CODE_SEND", "/20/adm/factor2/code", actorUserPk, details);

                    mapper = getSerializeMapper();
                    mapper.writeValue(output, "1");
                } catch (WebApplicationException ex) {
                    Map<String, Object> details = new LinkedHashMap<>();
                    details.put("status", "failed");
                    details.put("mobileMasked", mobileMasked);
                    boolean rateLimited = ex.getResponse() != null && ex.getResponse().getStatus() == 429;
                    details.put("rateLimited", rateLimited);
                    if (ex.getResponse() != null) {
                        details.put("httpStatus", ex.getResponse().getStatus());
                    }
                    recordAudit("FACTOR2_CODE_SEND_FAILED", "/20/adm/factor2/code", actorUserPk, details);
                    throw ex;
                } catch (RuntimeException ex) {
                    Map<String, Object> details = new LinkedHashMap<>();
                    details.put("status", "failed");
                    details.put("mobileMasked", mobileMasked);
                    details.put("rateLimited", false);
                    details.put("reason", "internal_error");
                    recordAudit("FACTOR2_CODE_SEND_FAILED", "/20/adm/factor2/code", actorUserPk, details);
                    throw ex;
                }
            }
        };
    }
    
    @PUT
    @Path("/factor2/device")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public StreamingOutput putFactor2Device(final String json) {
        
        return new StreamingOutput() {

            @Override
            public void write(OutputStream output) throws IOException, WebApplicationException {
                long actorUserPk = requireActorUserPk(httpRequest);
                String actorUserId = requireActorUserId(httpRequest);
                String mobileMasked = "***";
                try {
                    ObjectMapper mapper = new ObjectMapper();
                    Factor2Spec spec = mapper.readValue(json, Factor2Spec.class);
                    if (spec.getUserPK() > 0L && spec.getUserPK() != actorUserPk) {
                        throw restError(httpRequest, Response.Status.NOT_FOUND,
                                "not_found", "Requested resource was not found.");
                    }
                    String incomingUserId = normalizeText(spec.getUserId());
                    if (incomingUserId != null && !incomingUserId.equals(actorUserId)) {
                        throw restError(httpRequest, Response.Status.NOT_FOUND,
                                "not_found", "Requested resource was not found.");
                    }
                    String phoneNumber = normalizeText(spec.getPhoneNumber());
                    if (phoneNumber == null || !E164_PHONE_PATTERN.matcher(phoneNumber).matches()) {
                        throw restError(httpRequest, Response.Status.BAD_REQUEST,
                                "phone_number_invalid", "phoneNumber must match E.164 style.");
                    }
                    mobileMasked = maskPhone(phoneNumber);
                    validateLengthOr400(spec.getDeviceName(), 128, "device_name_too_long", "deviceName must be <=128.");
                    validateLengthOr400(spec.getMacAddress(), 128, "mac_address_too_long", "macAddress must be <=128.");
                    spec.setUserPK(actorUserPk);
                    spec.setUserId(actorUserId);
                    spec.setPhoneNumber(phoneNumber);

                    // Backup key
                    String bkey = TotpHelper.generateBackupKey();
                    spec.setBackupKey(bkey);

                    // 保存
                    try {
                        ehtService.saveFactor2(spec);
                    } catch (NoResultException ne) {
                        throw new WebApplicationException(ne, 404);
                    } catch (Exception e) {
                        throw new WebApplicationException(e, 404);
                    }

                    Map<String, Object> details = new LinkedHashMap<>();
                    details.put("status", "success");
                    details.put("mobileMasked", mobileMasked);
                    details.put("backupKeyIssued", true);
                    recordAudit("FACTOR2_DEVICE_TRUST", "/20/adm/factor2/device", actorUserPk, details);

                    mapper = getSerializeMapper();
                    mapper.writeValue(output, bkey);
                } catch (WebApplicationException ex) {
                    Map<String, Object> details = new LinkedHashMap<>();
                    details.put("status", "failed");
                    details.put("mobileMasked", mobileMasked);
                    if (ex.getResponse() != null) {
                        details.put("httpStatus", ex.getResponse().getStatus());
                    }
                    recordAudit("FACTOR2_DEVICE_TRUST_FAILED", "/20/adm/factor2/device", actorUserPk, details);
                    throw ex;
                } catch (RuntimeException ex) {
                    Map<String, Object> details = new LinkedHashMap<>();
                    details.put("status", "failed");
                    details.put("mobileMasked", mobileMasked);
                    details.put("reason", "internal_error");
                    recordAudit("FACTOR2_DEVICE_TRUST_FAILED", "/20/adm/factor2/device", actorUserPk, details);
                    throw ex;
                }
            }
        };
    }
    
    @DELETE
    @Path("/factor2/auth/{param}")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public StreamingOutput resetFactor2Auth(final @PathParam("param") String param) {
        
        return new StreamingOutput() {

            @Override
            public void write(OutputStream output) throws IOException, WebApplicationException {
                long targetUserPk = parseLongOr400(param, "userPk", httpRequest);
                long actorUserPk = requireActorUserPk(httpRequest);
                String actorFacility = requireActorFacilityId(httpRequest);
                if (targetUserPk != actorUserPk) {
                    if (!isAdmin(httpRequest)) {
                        throw restError(httpRequest, Response.Status.NOT_FOUND,
                                "not_found", "Requested resource was not found.");
                    }
                    requireAdmin(httpRequest, userServiceBean);
                    UserModel targetUser = userServiceBean.getUserByPk(targetUserPk);
                    if (targetUser == null) {
                        throw restError(httpRequest, Response.Status.NOT_FOUND,
                                "not_found", "Requested resource was not found.");
                    }
                    String targetFacility = getRemoteFacility(targetUser.getUserId());
                    ensureFacilityMatchOr404(actorFacility, targetFacility, "userPk", targetUserPk, httpRequest);
                }

                ehtService.resetFactor2Auth(targetUserPk);
                Map<String, Object> details = new LinkedHashMap<>();
                details.put("status", "success");
                details.put("targetUserPk", targetUserPk);
                recordAudit("FACTOR2_RESET", "/20/adm/factor2/auth", actorUserPk, details);

                ObjectMapper mapper = getSerializeMapper();
                mapper.writeValue(output, "1");
            }
        };
    }
    
    @PUT
    @Path("/user/factor2/device")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public JsonTouchSharedService.SafeUserResponse getUserWithNewFactor2Device(String json) throws IOException {

        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        Factor2Spec spec = mapper.readValue(json, Factor2Spec.class);
        
        UserModel result = ehtService.getUserWithNewFactor2Device(spec);
        return JsonTouchSharedService.toSafeUserResponse(result);
    }
    
    @PUT
    @Path("/user/factor2/backup")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public JsonTouchSharedService.SafeUserResponse getUserWithF2Backup(String json) throws IOException {

        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        Factor2Spec spec = mapper.readValue(json, Factor2Spec.class);

        UserModel result = ehtService.getUserWithF2Backup(spec);
        return JsonTouchSharedService.toSafeUserResponse(result);
    }

    @POST
    @Path("/factor2/totp/registration")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response startTotpRegistration(String json) throws IOException {
        TotpRegistrationRequest request = jsonMapper.readValue(json, TotpRegistrationRequest.class);
        long actorUserPk = requireActorUserPk(httpRequest);
        if (request.getUserPk() > 0L && request.getUserPk() != actorUserPk) {
            throw restError(httpRequest, Response.Status.NOT_FOUND,
                    "not_found", "Requested resource was not found.");
        }
        request.setUserPk(actorUserPk);
        try {
            TotpRegistrationResult result = ehtService.startTotpRegistration(
                    actorUserPk,
                    request.getLabel(),
                    request.getAccountName(),
                    request.getIssuer(),
                    secondFactorSecurityConfig.getTotpSecretProtector());

            TotpRegistrationResponse response = new TotpRegistrationResponse();
            response.setCredentialId(result.credentialId());
            response.setSecret(result.secret());
            response.setProvisioningUri(result.provisioningUri());

            Map<String, Object> details = new HashMap<>();
            details.put("credentialId", result.credentialId());
            details.put("label", request.getLabel());
            recordAudit("TOTP_REGISTER_INIT", "/20/adm/factor2/totp/registration", actorUserPk, details);
            return Response.ok(response).build();
        } catch (NoResultException e) {
            recordAuditFailure("TOTP_REGISTER_INIT_FAILED", "/20/adm/factor2/totp/registration", actorUserPk,
                    "user_not_found", e, Response.Status.NOT_FOUND.getStatusCode());
            throw new WebApplicationException(e, 404);
        } catch (SecurityException e) {
            recordAuditFailure("TOTP_REGISTER_INIT_FAILED", "/20/adm/factor2/totp/registration", actorUserPk,
                    e.getMessage(), e, Response.Status.BAD_REQUEST.getStatusCode());
            throw new WebApplicationException(e, 400);
        }
    }

    @POST
    @Path("/factor2/totp/verification")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response verifyTotpRegistration(String json) throws IOException {
        TotpVerificationRequest request = jsonMapper.readValue(json, TotpVerificationRequest.class);
        long actorUserPk = requireActorUserPk(httpRequest);
        if (request.getUserPk() > 0L && request.getUserPk() != actorUserPk) {
            throw restError(httpRequest, Response.Status.NOT_FOUND,
                    "not_found", "Requested resource was not found.");
        }
        request.setUserPk(actorUserPk);
        checkFactor2RateLimit(actorUserPk, FACTOR2_VERIFY_RATE_LIMIT, FACTOR2_VERIFY_RATE_BUCKETS);
        try {
            List<String> codes = ehtService.completeTotpRegistration(
                    actorUserPk,
                    request.getCredentialId(),
                    request.getCode(),
                    secondFactorSecurityConfig.getTotpSecretProtector());
            TotpVerificationResponse response = new TotpVerificationResponse();
            response.setVerified(true);
            response.setBackupCodes(codes);

            Map<String, Object> details = new HashMap<>();
            details.put("credentialId", request.getCredentialId());
            details.put("backupCodes", codes.size());
            recordAudit("TOTP_REGISTER_COMPLETE", "/20/adm/factor2/totp/verification", actorUserPk, details);
            return Response.ok(response).build();
        } catch (NoResultException e) {
            Map<String, Object> details = new HashMap<>();
            details.put("credentialId", request.getCredentialId());
            recordAuditFailure("TOTP_REGISTER_COMPLETE_FAILED", "/20/adm/factor2/totp/verification", actorUserPk,
                    "credential_not_found", e, Response.Status.NOT_FOUND.getStatusCode(), details);
            throw new WebApplicationException(e, 404);
        } catch (SecurityException e) {
            Map<String, Object> details = new HashMap<>();
            details.put("credentialId", request.getCredentialId());
            recordAuditFailure("TOTP_REGISTER_COMPLETE_FAILED", "/20/adm/factor2/totp/verification", actorUserPk,
                    e.getMessage(), e, Response.Status.BAD_REQUEST.getStatusCode(), details);
            throw new WebApplicationException(e, 400);
        }
    }

    @POST
    @Path("/factor2/fido2/registration/options")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response startFidoRegistration(String json) throws IOException {
        FidoRegistrationOptionsRequest request = jsonMapper.readValue(json, FidoRegistrationOptionsRequest.class);
        long actorUserPk = requireActorUserPk(httpRequest);
        String actorUserId = requireActorUserId(httpRequest);
        if (request.getUserPk() > 0L && request.getUserPk() != actorUserPk) {
            throw restError(httpRequest, Response.Status.NOT_FOUND,
                    "not_found", "Requested resource was not found.");
        }
        String requestUserId = normalizeText(request.getUserId());
        if (requestUserId != null && !requestUserId.equals(actorUserId)) {
            throw restError(httpRequest, Response.Status.NOT_FOUND,
                    "not_found", "Requested resource was not found.");
        }
        request.setUserPk(actorUserPk);
        request.setUserId(actorUserId);
        try {
            var challenge = ehtService.startFidoRegistration(
                    actorUserPk,
                    secondFactorSecurityConfig.getFido2Config(),
                    request.getAuthenticatorAttachment());
            FidoRegistrationOptionsResponse response = new FidoRegistrationOptionsResponse();
            response.setRequestId(challenge.getRequestId());
            response.setPublicKeyCredentialCreationOptions(challenge.getChallengePayload());

            Map<String, Object> details = new HashMap<>();
            details.put("requestId", challenge.getRequestId());
            details.put("authenticatorAttachment", request.getAuthenticatorAttachment());
            recordAudit("FIDO2_REGISTER_INIT", "/20/adm/factor2/fido2/registration/options", actorUserPk, details);
            return Response.ok(response).build();
        } catch (NoResultException e) {
            Map<String, Object> details = new HashMap<>();
            details.put("authenticatorAttachment", request.getAuthenticatorAttachment());
            recordAuditFailure("FIDO2_REGISTER_INIT_FAILED", "/20/adm/factor2/fido2/registration/options", actorUserPk,
                    "user_not_found", e, Response.Status.NOT_FOUND.getStatusCode(), details);
            throw new WebApplicationException(e, 404);
        } catch (SecurityException e) {
            Map<String, Object> details = new HashMap<>();
            details.put("authenticatorAttachment", request.getAuthenticatorAttachment());
            recordAuditFailure("FIDO2_REGISTER_INIT_FAILED", "/20/adm/factor2/fido2/registration/options", actorUserPk,
                    e.getMessage(), e, Response.Status.BAD_REQUEST.getStatusCode(), details);
            throw new WebApplicationException(e, 400);
        }
    }

    @POST
    @Path("/factor2/fido2/registration/finish")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response finishFidoRegistration(String json) throws IOException {
        FidoRegistrationFinishRequest request = jsonMapper.readValue(json, FidoRegistrationFinishRequest.class);
        long actorUserPk = requireActorUserPk(httpRequest);
        if (request.getUserPk() > 0L && request.getUserPk() != actorUserPk) {
            throw restError(httpRequest, Response.Status.NOT_FOUND,
                    "not_found", "Requested resource was not found.");
        }
        request.setUserPk(actorUserPk);
        checkFactor2RateLimit(actorUserPk, FACTOR2_VERIFY_RATE_LIMIT, FACTOR2_VERIFY_RATE_BUCKETS);
        try {
            var credential = ehtService.finishFidoRegistration(
                    actorUserPk,
                    request.getRequestId(),
                    request.getCredentialResponse(),
                    request.getLabel(),
                    secondFactorSecurityConfig.getFido2Config());
            Map<String, Object> result = new HashMap<>();
            result.put("credentialId", credential.getCredentialId());

            Map<String, Object> details = new HashMap<>();
            details.put("credentialId", credential.getCredentialId());
            recordAudit("FIDO2_REGISTER_COMPLETE", "/20/adm/factor2/fido2/registration/finish", actorUserPk, details);
            return Response.ok(result).build();
        } catch (NoResultException e) {
            Map<String, Object> details = new HashMap<>();
            details.put("requestId", request.getRequestId());
            recordAuditFailure("FIDO2_REGISTER_COMPLETE_FAILED", "/20/adm/factor2/fido2/registration/finish", actorUserPk,
                    "challenge_not_found", e, Response.Status.NOT_FOUND.getStatusCode(), details);
            throw new WebApplicationException(e, 404);
        } catch (SecurityException e) {
            Map<String, Object> details = new HashMap<>();
            details.put("requestId", request.getRequestId());
            recordAuditFailure("FIDO2_REGISTER_COMPLETE_FAILED", "/20/adm/factor2/fido2/registration/finish", actorUserPk,
                    e.getMessage(), e, Response.Status.BAD_REQUEST.getStatusCode(), details);
            throw new WebApplicationException(e, 400);
        }
    }

    @POST
    @Path("/factor2/fido2/assertion/options")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response startFidoAssertion(String json) throws IOException {
        FidoAssertionOptionsRequest request = jsonMapper.readValue(json, FidoAssertionOptionsRequest.class);
        long actorUserPk = requireActorUserPk(httpRequest);
        String actorUserId = requireActorUserId(httpRequest);
        if (request.getUserPk() > 0L && request.getUserPk() != actorUserPk) {
            throw restError(httpRequest, Response.Status.NOT_FOUND,
                    "not_found", "Requested resource was not found.");
        }
        String requestedUserId = normalizeText(request.getUserId());
        if (requestedUserId != null && !requestedUserId.equals(actorUserId)) {
            throw restError(httpRequest, Response.Status.NOT_FOUND,
                    "not_found", "Requested resource was not found.");
        }
        request.setUserPk(actorUserPk);
        request.setUserId(actorUserId);
        try {
            var challenge = ehtService.startFidoAssertion(
                    actorUserPk,
                    actorUserId,
                    secondFactorSecurityConfig.getFido2Config());
            FidoAssertionOptionsResponse response = new FidoAssertionOptionsResponse();
            response.setRequestId(challenge.getRequestId());
            response.setPublicKeyCredentialRequestOptions(challenge.getChallengePayload());

            Map<String, Object> details = new HashMap<>();
            details.put("requestId", challenge.getRequestId());
            recordAudit("FIDO2_ASSERT_INIT", "/20/adm/factor2/fido2/assertion/options", actorUserPk, details);
            return Response.ok(response).build();
        } catch (NoResultException e) {
            Map<String, Object> details = new HashMap<>();
            details.put("userId", actorUserId);
            recordAuditFailure("FIDO2_ASSERT_INIT_FAILED", "/20/adm/factor2/fido2/assertion/options", actorUserPk,
                    "credential_not_found", e, Response.Status.NOT_FOUND.getStatusCode(), details);
            throw new WebApplicationException(e, 404);
        } catch (SecurityException e) {
            Map<String, Object> details = new HashMap<>();
            details.put("userId", actorUserId);
            recordAuditFailure("FIDO2_ASSERT_INIT_FAILED", "/20/adm/factor2/fido2/assertion/options", actorUserPk,
                    e.getMessage(), e, Response.Status.BAD_REQUEST.getStatusCode(), details);
            throw new WebApplicationException(e, 400);
        }
    }

    @POST
    @Path("/factor2/fido2/assertion/finish")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response finishFidoAssertion(String json) throws IOException {
        FidoAssertionFinishRequest request = jsonMapper.readValue(json, FidoAssertionFinishRequest.class);
        long actorUserPk = requireActorUserPk(httpRequest);
        if (request.getUserPk() > 0L && request.getUserPk() != actorUserPk) {
            throw restError(httpRequest, Response.Status.NOT_FOUND,
                    "not_found", "Requested resource was not found.");
        }
        request.setUserPk(actorUserPk);
        checkFactor2RateLimit(actorUserPk, FACTOR2_VERIFY_RATE_LIMIT, FACTOR2_VERIFY_RATE_BUCKETS);
        try {
            boolean success = ehtService.finishFidoAssertion(
                    actorUserPk,
                    request.getRequestId(),
                    request.getCredentialResponse(),
                    secondFactorSecurityConfig.getFido2Config());
            FidoAssertionResponse response = new FidoAssertionResponse();
            response.setAuthenticated(success);

            Map<String, Object> details = new HashMap<>();
            details.put("requestId", request.getRequestId());
            details.put("authenticated", success);
            recordAudit("FIDO2_ASSERT_COMPLETE", "/20/adm/factor2/fido2/assertion/finish", actorUserPk, details);
            return Response.ok(response).build();
        } catch (NoResultException e) {
            Map<String, Object> details = new HashMap<>();
            details.put("requestId", request.getRequestId());
            recordAuditFailure("FIDO2_ASSERT_COMPLETE_FAILED", "/20/adm/factor2/fido2/assertion/finish", actorUserPk,
                    "challenge_not_found", e, Response.Status.NOT_FOUND.getStatusCode(), details);
            throw new WebApplicationException(e, 404);
        } catch (SecurityException e) {
            Map<String, Object> details = new HashMap<>();
            details.put("requestId", request.getRequestId());
            recordAuditFailure("FIDO2_ASSERT_COMPLETE_FAILED", "/20/adm/factor2/fido2/assertion/finish", actorUserPk,
                    e.getMessage(), e, Response.Status.BAD_REQUEST.getStatusCode(), details);
            throw new WebApplicationException(e, 400);
        }
    }
//minagawa$  

    private long parseLongOr400(String raw, String name, HttpServletRequest req) {
        String normalized = normalizeText(raw);
        if (normalized == null) {
            throw restError(req, Response.Status.BAD_REQUEST,
                    name + "_invalid", name + " must be numeric.");
        }
        try {
            long value = Long.parseLong(normalized);
            if (value <= 0L) {
                throw restError(req, Response.Status.BAD_REQUEST,
                        name + "_invalid", name + " must be numeric.");
            }
            return value;
        } catch (NumberFormatException ex) {
            throw restError(req, Response.Status.BAD_REQUEST,
                    name + "_invalid", name + " must be numeric.");
        }
    }

    private String requireActorUserId(HttpServletRequest req) {
        return requireRemoteUser(req);
    }

    private String requireActorFacilityId(HttpServletRequest req) {
        return requireActorFacility(req);
    }

    private UserModel requireActorUserModel(HttpServletRequest req) {
        String actorUserId = requireActorUserId(req);
        if (userServiceBean == null) {
            throw restError(req, Response.Status.UNAUTHORIZED, "unauthorized", "Authentication required.");
        }
        try {
            UserModel actor = userServiceBean.getUser(actorUserId);
            if (actor == null) {
                throw restError(req, Response.Status.UNAUTHORIZED, "unauthorized", "Authentication required.");
            }
            return actor;
        } catch (NoResultException | SecurityException ex) {
            throw restError(req, Response.Status.UNAUTHORIZED, "unauthorized", "Authentication required.");
        }
    }

    private long requireActorUserPk(HttpServletRequest req) {
        UserModel actor = requireActorUserModel(req);
        long actorPk = actor.getId();
        if (actorPk <= 0L) {
            throw restError(req, Response.Status.UNAUTHORIZED, "unauthorized", "Authentication required.");
        }
        return actorPk;
    }

    private boolean isAdmin(HttpServletRequest req) {
        String actorUserId = requireActorUserId(req);
        boolean userBeanAdmin = userServiceBean != null && userServiceBean.isAdmin(actorUserId);
        boolean roleAdmin = req != null && req.isUserInRole("ADMIN");
        return userBeanAdmin || roleAdmin;
    }

    private void ensurePatientFacilityOr404(String actorFacility, long patientPk, HttpServletRequest req) {
        ensureFacilityMatchOr404(
                actorFacility,
                karteServiceBean.findFacilityIdByPatientPk(patientPk),
                "patientPk",
                patientPk,
                req);
    }

    private void ensureDocFacilityOr404(String actorFacility, long docPk, HttpServletRequest req) {
        ensureFacilityMatchOr404(
                actorFacility,
                karteServiceBean.findFacilityIdByDocId(docPk),
                "docPk",
                docPk,
                req);
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

    private long requireKarteIdOr400(KarteBean karte, String name, HttpServletRequest req) {
        long karteId = karte != null ? karte.getId() : 0L;
        if (karteId <= 0L) {
            throw restError(req, Response.Status.BAD_REQUEST,
                    "karte_id_required", name + " is required.");
        }
        return karteId;
    }

    private void validateLengthOr400(String value, int max, String code, String message) {
        if (value != null && value.length() > max) {
            throw restError(httpRequest, Response.Status.BAD_REQUEST, code, message);
        }
    }

    private void checkFactor2RateLimit(long userPk, int limit, ConcurrentMap<Long, Deque<Long>> buckets) {
        long now = System.currentTimeMillis();
        Deque<Long> queue = buckets.computeIfAbsent(userPk, key -> new ArrayDeque<>());
        synchronized (queue) {
            while (!queue.isEmpty() && (now - queue.peekFirst()) > FACTOR2_RATE_WINDOW_MILLIS) {
                queue.pollFirst();
            }
            if (queue.size() >= limit) {
                throw tooManyRequests(httpRequest, "too_many_requests");
            }
            queue.addLast(now);
        }
    }

    private long resolveActorUserPkForAudit() {
        try {
            return requireActorUserPk(httpRequest);
        } catch (RuntimeException ex) {
            return -1L;
        }
    }

    private WebApplicationException tooManyRequests(HttpServletRequest req, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "too_many_requests");
        body.put("code", "too_many_requests");
        body.put("errorCode", "too_many_requests");
        body.put("message", message);
        body.put("status", 429);
        String traceId = resolveTraceId(req);
        if (traceId != null && !traceId.isBlank()) {
            body.put("traceId", traceId);
        }
        if (req != null && req.getRequestURI() != null && !req.getRequestURI().isBlank()) {
            body.put("path", req.getRequestURI());
        }
        Response response = Response.status(429)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(body)
                .build();
        return new WebApplicationException(message, response);
    }

    private List<String> maskPhoneNumbers(List<String> numbers) {
        List<String> masked = new ArrayList<>(numbers.size());
        for (String number : numbers) {
            masked.add(maskPhone(number));
        }
        return masked;
    }

    private String maskPhone(String number) {
        String value = normalizeText(number);
        if (value == null) {
            return "***";
        }
        if (value.length() <= 4) {
            return "*".repeat(value.length());
        }
        return "*".repeat(Math.max(0, value.length() - 4)) + value.substring(value.length() - 4);
    }
    
    /**
     * 保健医療機関コードとJMARIコードを取得する。
     * @return 
     */
    private String getFacilityCodeBy1001() {
       
////s.oh^ 2013/10/17 ローカルORCA対応
//        try {
//            // custom.properties から 保健医療機関コードとJMARIコードを読む
//            Properties config = new Properties();
//            // コンフィグファイルを読み込む
//            StringBuilder sb = new StringBuilder();
//            sb.append(System.getProperty("jboss.home.dir"));
//            sb.append(File.separator);
//            sb.append("custom.properties");
//            File f = new File(sb.toString());
//            FileInputStream fin = new FileInputStream(f);
//            InputStreamReader r = new InputStreamReader(fin, "JISAutoDetect");
//            config.load(r);
//            r.close();
//            // JMARI code
//            String jmari = config.getProperty("jamri.code");
//            String hcfacility = config.getProperty("healthcarefacility.code");
//            if(jmari != null && jmari.length() == 12 && hcfacility != null && hcfacility.length() == 10) {
//                StringBuilder ret = new StringBuilder();
//                ret.append(hcfacility);
//                ret.append("JPN");
//                ret.append(jmari);
//                return ret.toString();
//            }
//        } catch (FileNotFoundException ex) {
//            Logger.getLogger(EHTResource.class.getName()).log(Level.SEVERE, null, ex);
//        } catch (UnsupportedEncodingException ex) {
//            Logger.getLogger(EHTResource.class.getName()).log(Level.SEVERE, null, ex);
//        } catch (IOException ex) {
//            Logger.getLogger(EHTResource.class.getName()).log(Level.SEVERE, null, ex);
//        }
////s.oh$
        // SQL 文
        StringBuilder buf = new StringBuilder();
        buf.append("select kanritbl from tbl_syskanri where kanricd='1001'");
        String sql = buf.toString();

        Connection con = null;
        PreparedStatement ps;
        
        StringBuilder ret = new StringBuilder();

        try {
            //con = ds.getConnection();
            con = getConnection();
            ps = con.prepareStatement(sql);

            ResultSet rs = ps.executeQuery();

            if (rs.next()) {

                String line = rs.getString(1);
                
                // 保険医療機関コード 10桁
                ret.append(line.substring(0, 10));
                
                // JMARIコード JPN+12桁 (total 15)
                int index = line.indexOf("JPN");
                if (index>0) {
                    ret.append(line.substring(index, index+15));
                }
            }
            rs.close();
            ps.close();
            con.close();
            con = null;

        } catch (SQLException e) {
            e.printStackTrace(System.err);

        } finally {
            if (con != null) {
                try {
                    con.close();
                } catch (SQLException e) {
                }
            }
        }

        return ret.toString();        
    }

    private void recordAudit(String action, String resource, long userPk, Map<String, Object> details) {
        Map<String, Object> payloadDetails = new HashMap<>();
        if (details != null && !details.isEmpty()) {
            payloadDetails.putAll(stripSensitiveAuditDetails(details));
        }
        payloadDetails.putIfAbsent("status", "success");
        String runId = AbstractOrcaRestResource.resolveRunIdValue(httpRequest);
        if (runId != null && !runId.isBlank()) {
            payloadDetails.putIfAbsent("runId", runId);
        }
        AuditEventPayload payload = new AuditEventPayload();
        String actorId = Optional.ofNullable(httpRequest)
                .map(HttpServletRequest::getRemoteUser)
                .filter(s -> !s.isBlank())
                .orElse(String.valueOf(userPk));
        payload.setActorId(actorId);
        payload.setActorDisplayName(actorId);
        if (httpRequest != null && httpRequest.isUserInRole("ADMIN")) {
            payload.setActorRole("ADMIN");
        }
        payload.setAction(action);
        payload.setResource(resource);
        if (runId != null && !runId.isBlank()) {
            payload.setRunId(runId);
        }
        payload.setDetails(payloadDetails);
        if (httpRequest != null) {
            payload.setIpAddress(httpRequest.getRemoteAddr());
            payload.setUserAgent(httpRequest.getHeader("User-Agent"));
            payload.setRequestId(Optional.ofNullable(httpRequest.getHeader("X-Request-Id")).orElse(UUID.randomUUID().toString()));
        } else {
            payload.setRequestId(UUID.randomUUID().toString());
        }
        String traceId = resolveTraceId(httpRequest);
        if (traceId == null || traceId.isBlank()) {
            traceId = payload.getRequestId();
        }
        payload.setTraceId(traceId);
        enrichUserDetails(payloadDetails, actorId);
        enrichTraceDetails(payloadDetails, traceId);
        if (sessionAuditDispatcher != null) {
            sessionAuditDispatcher.record(payload);
        }
    }

    private void recordAuditFailure(String action, String resource, long userPk, String reason, Throwable error) {
        recordAuditFailure(action, resource, userPk, reason, error, null, null);
    }

    private void recordAuditFailure(String action, String resource, long userPk, String reason, Throwable error,
            Integer httpStatus) {
        recordAuditFailure(action, resource, userPk, reason, error, httpStatus, null);
    }

    private void recordAuditFailure(String action, String resource, long userPk, String reason, Throwable error,
            Integer httpStatus, Map<String, Object> extraDetails) {
        Map<String, Object> details = new HashMap<>();
        if (extraDetails != null && !extraDetails.isEmpty()) {
            details.putAll(extraDetails);
        }
        details.put("status", "failed");
        if (reason != null && !reason.isBlank()) {
            details.put("reason", reason);
            details.put("errorCode", reason);
        }
        if (error != null) {
            String exceptionClass = error.getClass().getSimpleName();
            details.put("error", exceptionClass);
            details.put("exceptionClass", exceptionClass);
            if (error.getMessage() != null && !error.getMessage().isBlank()) {
                details.put("errorMessage", error.getMessage());
            }
        }
        if (httpStatus != null) {
            details.put("httpStatus", httpStatus);
        }
        recordAudit(action, resource, userPk, details);
    }

    private Map<String, Object> stripSensitiveAuditDetails(Map<String, Object> details) {
        Map<String, Object> filtered = new HashMap<>();
        for (Map.Entry<String, Object> entry : details.entrySet()) {
            if (entry == null || entry.getKey() == null) {
                continue;
            }
            String key = entry.getKey().trim().toLowerCase();
            if (key.equals("backupkey")
                    || key.equals("secret")
                    || key.equals("backupcodes")
                    || key.equals("smsbody")
                    || key.equals("messagebody")
                    || key.equals("code")
                    || key.equals("authcode")
                    || key.equals("totpcode")) {
                continue;
            }
            filtered.put(entry.getKey(), entry.getValue());
        }
        return filtered;
    }

    private void enrichUserDetails(Map<String, Object> details, String actorId) {
        if (details == null || actorId == null) {
            return;
        }
        details.putIfAbsent("remoteUser", actorId);
        int idx = actorId.indexOf(IInfoModel.COMPOSITE_KEY_MAKER);
        if (idx > 0) {
            details.putIfAbsent("facilityId", actorId.substring(0, idx));
            if (idx + 1 < actorId.length()) {
                details.putIfAbsent("userId", actorId.substring(idx + 1));
            }
        }
    }

    private void enrichTraceDetails(Map<String, Object> details, String traceId) {
        if (details == null || traceId == null || traceId.isBlank()) {
            return;
        }
        details.putIfAbsent("traceId", traceId);
    }
     
    private Connection getConnection() throws SQLException {
        return ORCAConnection.getInstance().getConnection();
    }
    
    private void closeStatement(java.sql.Statement st) {
        if (st != null) {
            try {
                st.close();
            }
            catch (SQLException e) {
            	e.printStackTrace(System.err);
            }
        }
    }
    
    private void closeConnection(Connection c) {
        try {
            c.close();
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }
    }
}
