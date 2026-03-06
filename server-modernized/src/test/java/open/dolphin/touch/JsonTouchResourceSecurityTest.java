package open.dolphin.touch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.WebApplicationException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import open.dolphin.infomodel.AttachmentModel;
import open.dolphin.infomodel.DiagnosisSendWrapper;
import open.dolphin.infomodel.DocInfoModel;
import open.dolphin.infomodel.DocumentModel;
import open.dolphin.infomodel.KarteBean;
import open.dolphin.infomodel.ModuleInfoBean;
import open.dolphin.infomodel.ModuleModel;
import open.dolphin.infomodel.PatientModel;
import open.dolphin.infomodel.RegisteredDiagnosisModel;
import open.dolphin.infomodel.SchemaModel;
import open.dolphin.infomodel.UserModel;
import open.dolphin.session.ChartEventServiceBean;
import open.dolphin.session.KarteServiceBean;
import open.dolphin.session.UserServiceBean;
import open.dolphin.touch.converter.ISendPackage;
import open.dolphin.touch.support.TouchJsonConverter;
import open.dolphin.touch.session.IPhoneServiceBean;
import open.dolphin.testsupport.RuntimeDelegateTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class JsonTouchResourceSecurityTest extends RuntimeDelegateTestSupport {

    private static final String ACTOR_REMOTE_USER = "facility01:user01";

    @Mock
    private TouchJsonConverter touchJsonConverter;

    @Mock
    private IPhoneServiceBean iPhoneServiceBean;

    @Mock
    private KarteServiceBean karteServiceBean;

    @Mock
    private ChartEventServiceBean chartEventServiceBean;

    @Mock
    private UserServiceBean userServiceBean;

    @Mock
    private HttpServletRequest servletRequest;

    private JsonTouchSharedService sharedService;
    private JsonTouchResource touchResource;
    private open.dolphin.adm10.rest.JsonTouchResource adm10Resource;
    private open.dolphin.adm20.rest.JsonTouchResource adm20Resource;

    @BeforeEach
    void setUp() throws Exception {
        when(servletRequest.getRemoteUser()).thenReturn(ACTOR_REMOTE_USER);

        sharedService = new JsonTouchSharedService();
        setField(sharedService, "iPhoneService", iPhoneServiceBean);
        setField(sharedService, "karteService", karteServiceBean);
        setField(sharedService, "chartService", chartEventServiceBean);
        setField(sharedService, "userServiceBean", userServiceBean);

        touchResource = new JsonTouchResource();
        setField(touchResource, "sharedService", sharedService);
        setField(touchResource, "touchJsonConverter", touchJsonConverter);
        setField(touchResource, "karteServiceBean", karteServiceBean);
        setField(touchResource, "servletRequest", servletRequest);

        adm10Resource = new open.dolphin.adm10.rest.JsonTouchResource();
        setField(adm10Resource, "sharedService", sharedService);
        setField(adm10Resource, "touchJsonConverter", touchJsonConverter);
        setField(adm10Resource, "karteServiceBean", karteServiceBean);

        adm20Resource = new open.dolphin.adm20.rest.JsonTouchResource();
        setField(adm20Resource, "sharedService", sharedService);
        setField(adm20Resource, "touchJsonConverter", touchJsonConverter);
        setField(adm20Resource, "karteServiceBean", karteServiceBean);
    }

    @Test
    void userEndpointReturns404ForCrossUserWithoutAdminRole() throws Exception {
        UserModel actor = user("F001:doctor01", "F001", "doctor");
        UserModel target = user("F001:nurse02", "F001", "user");
        when(iPhoneServiceBean.getUserById("F001:doctor01")).thenReturn(actor);
        when(iPhoneServiceBean.getUserById("F001:nurse02")).thenReturn(target);
        when(userServiceBean.isAdmin("F001:doctor01")).thenReturn(false);

        assertNotFound(() -> touchResource.getUserById(servletRequest, "nurse02"));
        assertNotFound(() -> adm10Resource.getUserById(servletRequest, "nurse02"));
        assertNotFound(() -> adm20Resource.getUserById(servletRequest, "nurse02"));
    }

    @Test
    void userEndpointAllowsSelfAndHidesPasswordField() throws Exception {
        when(servletRequest.getRemoteUser()).thenReturn("F001:doctor01");
        UserModel actor = user("F001:doctor01", "F001", "doctor");
        actor.setPassword("secret-value");
        actor.setEmail("doctor@example.com");
        when(iPhoneServiceBean.getUserById("F001:doctor01")).thenReturn(actor);
        when(userServiceBean.isAdmin("F001:doctor01")).thenReturn(false);

        JsonTouchSharedService.SafeUserResponse response = touchResource.getUserById(servletRequest, "doctor01");
        String json = new ObjectMapper().writeValueAsString(response);
        org.assertj.core.api.Assertions.assertThat(json)
                .doesNotContain("password")
                .doesNotContain("temporaryPassword")
                .doesNotContain("credential")
                .doesNotContain("salt")
                .doesNotContain("hash")
                .doesNotContain("memo")
                .doesNotContain("orcaId")
                .doesNotContain("useDrugId")
                .contains("doctor@example.com");
    }

    @Test
    void visitPackageReturns404OnFacilityMismatchForAllResources() throws Exception {
        JsonTouchSharedService sharedMock = mock(JsonTouchSharedService.class);
        JsonTouchResource touch = new JsonTouchResource();
        open.dolphin.adm10.rest.JsonTouchResource adm10 = new open.dolphin.adm10.rest.JsonTouchResource();
        open.dolphin.adm20.rest.JsonTouchResource adm20 = new open.dolphin.adm20.rest.JsonTouchResource();
        setField(touch, "sharedService", sharedMock);
        setField(adm10, "sharedService", sharedMock);
        setField(adm20, "sharedService", sharedMock);
        setField(touch, "karteServiceBean", karteServiceBean);
        setField(adm10, "karteServiceBean", karteServiceBean);
        setField(adm20, "karteServiceBean", karteServiceBean);
        setField(touch, "servletRequest", servletRequest);

        when(karteServiceBean.findFacilityIdByPvtId(1L)).thenReturn("facility01");
        when(karteServiceBean.findFacilityIdByPatientPk(2L)).thenReturn("facility01");
        when(karteServiceBean.findFacilityIdByDocId(3L)).thenReturn("F999");

        assertNotFound(() -> touch.getVisitPackage(servletRequest, "1,2,3,1"));
        assertNotFound(() -> adm10.getVisitPackage(servletRequest, "1,2,3,1"));
        assertNotFound(() -> adm20.getVisitPackage(servletRequest, "1,2,3,1"));
        verify(sharedMock, never()).getVisitPackage(anyLong(), anyLong(), anyLong(), anyInt());
    }

    @Test
    void getUserByIdReturns404WhenUidFacilityDiffersFromActorFacilityForAllResources() {
        assertNotFound(() -> touchResource.getUserById(servletRequest, "facility02:user99"));
        assertNotFound(() -> adm10Resource.getUserById(servletRequest, "facility02:user99"));
        assertNotFound(() -> adm20Resource.getUserById(servletRequest, "facility02:user99"));
        verify(iPhoneServiceBean, never()).getUserById("facility02:user99");
    }

    @Test
    void sendPackageSanitizesDocumentAndDiagnosisBeforePersist() throws Exception {
        assertSendPackageSanitization(
                payload -> touchResource.postSendPackage(payload),
                open.dolphin.touch.converter.ISendPackage.class);
    }

    @Test
    void sendPackageSanitizesDocumentAndDiagnosisBeforePersistAdm10() throws Exception {
        assertSendPackageSanitization(
                payload -> adm10Resource.postSendPackage(servletRequest, payload),
                open.dolphin.adm10.converter.ISendPackage.class);
    }

    @Test
    void sendPackageSanitizesDocumentAndDiagnosisBeforePersistAdm20() throws Exception {
        assertSendPackageSanitization(
                payload -> adm20Resource.postSendPackage(servletRequest, payload),
                open.dolphin.adm20.converter.ISendPackage.class);
    }

    @Test
    void sendPackageRejectsCrossFacilityDeletedDiagnosis() throws Exception {
        UserModel actorUser = new UserModel();
        actorUser.setUserId(ACTOR_REMOTE_USER);
        when(iPhoneServiceBean.getUserById(ACTOR_REMOTE_USER)).thenReturn(actorUser);

        ISendPackage touchPkg = mock(ISendPackage.class);
        when(touchPkg.documentModel()).thenReturn(null);
        when(touchPkg.diagnosisSendWrapperModel()).thenReturn(null);
        when(touchPkg.deletedDiagnsis()).thenReturn(List.of("77"));
        when(touchJsonConverter.readLegacy(any(String.class),
                org.mockito.ArgumentMatchers.eq(open.dolphin.touch.converter.ISendPackage.class))).thenReturn(touchPkg);

        open.dolphin.adm10.converter.ISendPackage adm10Pkg = mock(open.dolphin.adm10.converter.ISendPackage.class);
        when(adm10Pkg.documentModel()).thenReturn(null);
        when(adm10Pkg.diagnosisSendWrapperModel()).thenReturn(null);
        when(adm10Pkg.deletedDiagnsis()).thenReturn(List.of("77"));
        when(touchJsonConverter.readLegacy(any(String.class),
                org.mockito.ArgumentMatchers.eq(open.dolphin.adm10.converter.ISendPackage.class))).thenReturn(adm10Pkg);

        open.dolphin.adm20.converter.ISendPackage adm20Pkg = mock(open.dolphin.adm20.converter.ISendPackage.class);
        when(adm20Pkg.documentModel()).thenReturn(null);
        when(adm20Pkg.diagnosisSendWrapperModel()).thenReturn(null);
        when(adm20Pkg.deletedDiagnsis()).thenReturn(List.of("77"));
        when(touchJsonConverter.readLegacy(any(String.class),
                org.mockito.ArgumentMatchers.eq(open.dolphin.adm20.converter.ISendPackage.class))).thenReturn(adm20Pkg);
        when(karteServiceBean.findFacilityIdByDiagnosisId(77L)).thenReturn("F999");

        assertNotFound(() -> touchResource.postSendPackage("{\"dummy\":true}"));
        assertNotFound(() -> adm10Resource.postSendPackage(servletRequest, "{\"dummy\":true}"));
        assertNotFound(() -> adm20Resource.postSendPackage(servletRequest, "{\"dummy\":true}"));
        verify(karteServiceBean, never()).removeDiagnosis(anyList());
        verify(karteServiceBean, never()).postPutSendDiagnosis(any(DiagnosisSendWrapper.class));
    }

    private void assertSendPackageSanitization(SendPackageInvoker invoker, Class<?> sendPackageType) throws Exception {
        UserModel actorUser = new UserModel();
        actorUser.setUserId(ACTOR_REMOTE_USER);
        when(iPhoneServiceBean.getUserById(ACTOR_REMOTE_USER)).thenReturn(actorUser);

        PatientModel patient = new PatientModel();
        patient.setId(10L);
        patient.setPatientId("P100");
        patient.setFacilityId("facility01");
        when(iPhoneServiceBean.getPatientById("facility01", "P100")).thenReturn(patient);

        KarteBean resolvedKarte = new KarteBean();
        resolvedKarte.setId(500L);
        resolvedKarte.setPatientModel(patient);
        when(karteServiceBean.getKarte(10L, null)).thenReturn(resolvedKarte);
        when(karteServiceBean.findFacilityIdByDiagnosisId(11L)).thenReturn("facility01");
        when(karteServiceBean.findFacilityIdByDiagnosisId(12L)).thenReturn("facility01");
        when(karteServiceBean.addDocument(any(DocumentModel.class))).thenReturn(99L);
        when(karteServiceBean.postPutSendDiagnosis(any(DiagnosisSendWrapper.class))).thenReturn(Collections.emptyList());

        DocumentModel payloadDocument = buildPayloadDocument();
        DiagnosisSendWrapper payloadWrapper = buildPayloadWrapper();
        if (open.dolphin.touch.converter.ISendPackage.class.equals(sendPackageType)) {
            ISendPackage pkg = mock(ISendPackage.class);
            when(pkg.documentModel()).thenReturn(payloadDocument);
            when(pkg.diagnosisSendWrapperModel()).thenReturn(payloadWrapper);
            when(pkg.deletedDiagnsis()).thenReturn(Collections.emptyList());
            when(pkg.chartEventModel()).thenReturn(null);
            when(touchJsonConverter.readLegacy(any(String.class),
                    org.mockito.ArgumentMatchers.eq(open.dolphin.touch.converter.ISendPackage.class))).thenReturn(pkg);
        } else if (open.dolphin.adm10.converter.ISendPackage.class.equals(sendPackageType)) {
            open.dolphin.adm10.converter.ISendPackage pkg = mock(open.dolphin.adm10.converter.ISendPackage.class);
            when(pkg.documentModel()).thenReturn(payloadDocument);
            when(pkg.diagnosisSendWrapperModel()).thenReturn(payloadWrapper);
            when(pkg.deletedDiagnsis()).thenReturn(Collections.emptyList());
            when(pkg.chartEventModel()).thenReturn(null);
            when(touchJsonConverter.readLegacy(any(String.class),
                    org.mockito.ArgumentMatchers.eq(open.dolphin.adm10.converter.ISendPackage.class))).thenReturn(pkg);
        } else if (open.dolphin.adm20.converter.ISendPackage.class.equals(sendPackageType)) {
            open.dolphin.adm20.converter.ISendPackage pkg = mock(open.dolphin.adm20.converter.ISendPackage.class);
            when(pkg.documentModel()).thenReturn(payloadDocument);
            when(pkg.diagnosisSendWrapperModel()).thenReturn(payloadWrapper);
            when(pkg.deletedDiagnsis()).thenReturn(Collections.emptyList());
            when(pkg.chartEventModel()).thenReturn(null);
            when(touchJsonConverter.readLegacy(any(String.class),
                    org.mockito.ArgumentMatchers.eq(open.dolphin.adm20.converter.ISendPackage.class))).thenReturn(pkg);
        } else {
            throw new IllegalArgumentException("Unsupported sendPackageType: " + sendPackageType);
        }

        String ret = invoker.invoke("{\"dummy\":true}");
        assertEquals("99", ret);

        ArgumentCaptor<DocumentModel> docCaptor = ArgumentCaptor.forClass(DocumentModel.class);
        verify(karteServiceBean).addDocument(docCaptor.capture());
        DocumentModel persisted = docCaptor.getValue();
        assertEquals(0L, persisted.getId());
        assertSame(actorUser, persisted.getUserModel());
        assertSame(resolvedKarte, persisted.getKarte());
        assertEquals(0L, persisted.getDocInfoModel().getDocPk());

        ModuleModel persistedModule = persisted.getModules().get(0);
        assertEquals(0L, persistedModule.getId());
        assertSame(actorUser, persistedModule.getUserModel());
        assertSame(resolvedKarte, persistedModule.getKarte());
        assertSame(persisted, persistedModule.getDocumentModel());

        SchemaModel persistedSchema = persisted.getSchema().get(0);
        assertEquals(0L, persistedSchema.getId());
        assertSame(actorUser, persistedSchema.getUserModel());
        assertSame(resolvedKarte, persistedSchema.getKarte());
        assertSame(persisted, persistedSchema.getDocumentModel());

        AttachmentModel persistedAttachment = persisted.getAttachment().get(0);
        assertEquals(0L, persistedAttachment.getId());
        assertSame(actorUser, persistedAttachment.getUserModel());
        assertSame(resolvedKarte, persistedAttachment.getKarte());
        assertSame(persisted, persistedAttachment.getDocumentModel());

        ArgumentCaptor<DiagnosisSendWrapper> wrapperCaptor = ArgumentCaptor.forClass(DiagnosisSendWrapper.class);
        verify(karteServiceBean).postPutSendDiagnosis(wrapperCaptor.capture());
        DiagnosisSendWrapper persistedWrapper = wrapperCaptor.getValue();
        assertSame(actorUser, persistedWrapper.getAddedDiagnosis().get(0).getUserModel());
        assertSame(resolvedKarte, persistedWrapper.getAddedDiagnosis().get(0).getKarte());
        assertSame(actorUser, persistedWrapper.getUpdatedDiagnosis().get(0).getUserModel());
        assertSame(resolvedKarte, persistedWrapper.getUpdatedDiagnosis().get(0).getKarte());
    }

    private static DocumentModel buildPayloadDocument() {
        DocumentModel model = new DocumentModel();
        model.setId(777L);
        UserModel payloadUser = new UserModel();
        payloadUser.setUserId("F999:attacker");
        model.setUserModel(payloadUser);
        KarteBean payloadKarte = new KarteBean();
        payloadKarte.setId(9999L);
        model.setKarte(payloadKarte);

        DocInfoModel docInfo = new DocInfoModel();
        docInfo.setPatientId("P100");
        docInfo.setDocPk(888L);
        model.setDocInfoModel(docInfo);

        ModuleModel module = new ModuleModel();
        module.setId(333L);
        module.setModuleInfoBean(new ModuleInfoBean());
        model.setModules(new ArrayList<>(List.of(module)));

        SchemaModel schema = new SchemaModel();
        schema.setId(444L);
        model.setSchema(new ArrayList<>(List.of(schema)));

        AttachmentModel attachment = new AttachmentModel();
        attachment.setId(555L);
        model.setAttachment(new ArrayList<>(List.of(attachment)));
        return model;
    }

    private static DiagnosisSendWrapper buildPayloadWrapper() {
        DiagnosisSendWrapper wrapper = new DiagnosisSendWrapper();
        wrapper.setPatientId("P100");
        RegisteredDiagnosisModel added = new RegisteredDiagnosisModel();
        added.setId(11L);
        RegisteredDiagnosisModel updated = new RegisteredDiagnosisModel();
        updated.setId(12L);
        wrapper.setAddedDiagnosis(new ArrayList<>(List.of(added)));
        wrapper.setUpdatedDiagnosis(new ArrayList<>(List.of(updated)));
        return wrapper;
    }

    private static void assertNotFound(Executable executable) {
        WebApplicationException ex = assertThrows(WebApplicationException.class, executable::execute);
        assertEquals(404, ex.getResponse().getStatus());
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    private static UserModel user(String userId, String facilityId, String roleValue) {
        UserModel user = new UserModel();
        user.setUserId(userId);
        open.dolphin.infomodel.FacilityModel facility = new open.dolphin.infomodel.FacilityModel();
        facility.setFacilityId(facilityId);
        user.setFacilityModel(facility);
        open.dolphin.infomodel.RoleModel role = new open.dolphin.infomodel.RoleModel();
        role.setRole(roleValue);
        user.setRoles(List.of(role));
        return user;
    }

    @FunctionalInterface
    private interface Executable {
        void execute() throws Exception;
    }

    @FunctionalInterface
    private interface SendPackageInvoker {
        String invoke(String payload) throws Exception;
    }
}
