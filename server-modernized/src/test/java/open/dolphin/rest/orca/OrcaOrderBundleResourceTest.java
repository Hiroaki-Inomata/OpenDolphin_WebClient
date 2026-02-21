package open.dolphin.rest.orca;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.Date;
import java.util.List;
import java.util.Map;
import open.dolphin.audit.AuditEventEnvelope;
import open.dolphin.infomodel.BundleDolphin;
import open.dolphin.infomodel.ClaimItem;
import open.dolphin.infomodel.DocInfoModel;
import open.dolphin.infomodel.DocumentModel;
import open.dolphin.infomodel.IInfoModel;
import open.dolphin.infomodel.KarteBean;
import open.dolphin.infomodel.ModuleInfoBean;
import open.dolphin.infomodel.ModuleModel;
import open.dolphin.infomodel.PatientModel;
import open.dolphin.infomodel.UserModel;
import open.dolphin.rest.dto.orca.OrderBundleMutationRequest;
import open.dolphin.rest.dto.orca.OrderBundleRecommendationResponse;
import open.dolphin.security.audit.AuditEventPayload;
import open.dolphin.security.audit.SessionAuditDispatcher;
import open.dolphin.session.KarteServiceBean;
import open.dolphin.session.PatientServiceBean;
import open.dolphin.session.UserServiceBean;
import open.dolphin.testsupport.RuntimeDelegateTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OrcaOrderBundleResourceTest extends RuntimeDelegateTestSupport {

    private OrcaOrderBundleResource resource;
    private RecordingSessionAuditDispatcher auditDispatcher;
    private HttpServletRequest servletRequest;

    @BeforeEach
    void setUp() throws Exception {
        resource = new OrcaOrderBundleResource();
        auditDispatcher = new RecordingSessionAuditDispatcher();
        injectField(resource, "sessionAuditDispatcher", auditDispatcher);
        injectField(resource, "patientServiceBean", new FakePatientServiceBean());
        injectField(resource, "karteServiceBean", new FakeKarteServiceBean());
        injectField(resource, "userServiceBean", new FakeUserServiceBean());
        servletRequest = (HttpServletRequest) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class[]{HttpServletRequest.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("getRemoteUser".equals(name)) return "F001:doctor01";
                    if ("getRemoteAddr".equals(name)) return "127.0.0.1";
                    if ("getRequestURI".equals(name)) return "/orca/order/recommendations";
                    if ("getHeader".equals(name) && args != null && args.length == 1) {
                        String header = String.valueOf(args[0]);
                        return switch (header) {
                            case "X-Request-Id" -> "req-order-recommendation";
                            case "X-Trace-Id" -> "trace-order-recommendation";
                            case "User-Agent" -> "JUnit";
                            default -> null;
                        };
                    }
                    return null;
                });
    }

    @Test
    void getRecommendationsRejectsMissingPatientId() {
        WebApplicationException exception = null;
        try {
            resource.getRecommendations(servletRequest, " ", "medOrder", null, false, 8, 0, 100);
        } catch (WebApplicationException ex) {
            exception = ex;
        }
        assertNotNull(exception);
        assertEquals(400, exception.getResponse().getStatus());
        Map<String, Object> body = getErrorBody(exception);
        assertEquals(Boolean.TRUE, body.get("validationError"));
        assertEquals("patientId", body.get("field"));
        assertEquals("patientId is required", body.get("message"));
        assertNotNull(auditDispatcher.payload);
        assertEquals("ORCA_ORDER_RECOMMENDATION_FETCH", auditDispatcher.payload.getAction());
        assertEquals(AuditEventEnvelope.Outcome.FAILURE, auditDispatcher.outcome);
    }

    @Test
    void getRecommendationsReturnsPatientOnlyRowsWhenFacilityDisabled() {
        OrderBundleRecommendationResponse response = resource.getRecommendations(
                servletRequest,
                "00001",
                "medOrder",
                "2025-01-01",
                false,
                8,
                0,
                100);

        assertNotNull(response);
        assertEquals("00001", response.getPatientId());
        assertEquals("medOrder", response.getEntity());
        assertEquals(1, response.getRecordsReturned());
        assertEquals(2, response.getRecordsScanned());
        assertEquals(1, response.getRecommendations().size());
        var entry = response.getRecommendations().get(0);
        assertEquals("medOrder", entry.getEntity());
        assertEquals("patient", entry.getSource());
        assertEquals(2, entry.getCount());
        assertEquals("降圧薬セット", entry.getTemplate().getBundleName());
        assertEquals("out", entry.getTemplate().getPrescriptionLocation());
        assertEquals("regular", entry.getTemplate().getPrescriptionTiming());
        assertNotNull(auditDispatcher.payload);
        assertEquals("ORCA_ORDER_RECOMMENDATION_FETCH", auditDispatcher.payload.getAction());
        assertEquals(AuditEventEnvelope.Outcome.SUCCESS, auditDispatcher.outcome);
    }

    @Test
    void postBundlesRejectsInvalidStartDate() {
        OrderBundleMutationRequest payload = new OrderBundleMutationRequest();
        payload.setPatientId("00001");
        OrderBundleMutationRequest.BundleOperation op = new OrderBundleMutationRequest.BundleOperation();
        op.setOperation("create");
        op.setEntity("medOrder");
        op.setBundleName("降圧薬セット");
        op.setStartDate("2025/01/01");
        payload.setOperations(List.of(op));

        WebApplicationException exception = null;
        try {
            resource.postBundles(servletRequest, payload);
        } catch (WebApplicationException ex) {
            exception = ex;
        }

        assertNotNull(exception);
        assertEquals(400, exception.getResponse().getStatus());
        Map<String, Object> body = getErrorBody(exception);
        assertEquals(Boolean.TRUE, body.get("validationError"));
        assertEquals("startDate", body.get("field"));
        assertEquals("startDate must be yyyy-MM-dd", body.get("message"));
        assertNotNull(auditDispatcher.payload);
        assertEquals("ORCA_ORDER_BUNDLE_MUTATION", auditDispatcher.payload.getAction());
        assertEquals(AuditEventEnvelope.Outcome.FAILURE, auditDispatcher.outcome);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> getErrorBody(WebApplicationException exception) {
        return (Map<String, Object>) exception.getResponse().getEntity();
    }

    private static void injectField(Object target, String fieldName, Object value) throws Exception {
        Class<?> type = target.getClass();
        Field field = null;
        while (type != null && field == null) {
            try {
                field = type.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        if (field == null) {
            throw new NoSuchFieldException(fieldName);
        }
        field.setAccessible(true);
        field.set(target, value);
    }

    private static final class RecordingSessionAuditDispatcher extends SessionAuditDispatcher {
        private AuditEventPayload payload;
        private AuditEventEnvelope.Outcome outcome;

        @Override
        public AuditEventEnvelope record(AuditEventPayload payload, AuditEventEnvelope.Outcome overrideOutcome,
                String errorCode, String errorMessage) {
            this.payload = payload;
            this.outcome = overrideOutcome;
            return null;
        }
    }

    private static final class FakePatientServiceBean extends PatientServiceBean {
        @Override
        public PatientModel getPatientById(String fid, String pid) {
            PatientModel patient = new PatientModel();
            patient.setId(100L);
            patient.setFacilityId(fid);
            patient.setPatientId(pid);
            patient.setFullName("テスト患者");
            patient.setKanaName("テスト");
            patient.setBirthday("1990-01-01");
            patient.setGender("F");
            return patient;
        }
    }

    private static final class FakeKarteServiceBean extends KarteServiceBean {
        @Override
        public KarteBean getKarte(String facilityId, String patientId, Date fromDate) {
            KarteBean karte = new KarteBean();
            karte.setId(20L);
            return karte;
        }

        @Override
        public List<DocInfoModel> getDocumentList(long karteId, Date fromDate, boolean includeModifid) {
            DocInfoModel d1 = new DocInfoModel();
            d1.setDocPk(1001L);
            DocInfoModel d2 = new DocInfoModel();
            d2.setDocPk(1002L);
            return List.of(d1, d2);
        }

        @Override
        public List<DocumentModel> getDocuments(List<Long> ids) {
            return ids.stream().map(this::buildDocument).toList();
        }

        private DocumentModel buildDocument(Long documentId) {
            DocumentModel document = new DocumentModel();
            document.setId(documentId != null ? documentId : 0L);
            document.setStarted(new Date(1735603200000L)); // 2024-12-31
            ModuleModel module = new ModuleModel();
            module.setStarted(new Date(1735603200000L + ((documentId != null ? documentId : 0L) * 1000L)));

            ModuleInfoBean info = new ModuleInfoBean();
            info.setStampName("降圧薬セット");
            info.setStampRole(IInfoModel.ROLE_P);
            info.setStampNumber(0);
            info.setEntity(IInfoModel.ENTITY_MED_ORDER);
            module.setModuleInfoBean(info);

            BundleDolphin bundle = new BundleDolphin();
            bundle.setOrderName("降圧薬セット");
            bundle.setBundleNumber("14");
            bundle.setAdmin("1日1回 朝食後");
            bundle.setClassCode("212");
            ClaimItem item = new ClaimItem();
            item.setCode("100001");
            item.setName("アムロジピン");
            item.setNumber("1");
            item.setUnit("錠");
            bundle.setClaimItem(new ClaimItem[]{item});
            module.setModel(bundle);

            document.setModules(List.of(module));
            return document;
        }
    }

    private static final class FakeUserServiceBean extends UserServiceBean {
        @Override
        public UserModel getUser(String userId) {
            UserModel user = new UserModel();
            user.setUserId(userId);
            user.setCommonName("テスト医師");
            return user;
        }
    }
}
