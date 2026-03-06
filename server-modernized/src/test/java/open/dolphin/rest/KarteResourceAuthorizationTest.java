package open.dolphin.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.WebApplicationException;
import java.util.Date;
import java.util.List;
import open.dolphin.converter.SchemaModelConverter;
import open.dolphin.infomodel.KarteBean;
import open.dolphin.infomodel.SchemaModel;
import open.dolphin.infomodel.UserModel;
import open.dolphin.security.audit.AuditTrailService;
import open.dolphin.session.KarteServiceBean;
import open.dolphin.session.PVTServiceBean;
import open.dolphin.session.framework.SessionTraceManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KarteResourceAuthorizationTest {

    @Mock
    KarteServiceBean karteServiceBean;

    @Mock
    PVTServiceBean pvtServiceBean;

    @Mock
    AuditTrailService auditTrailService;

    @Mock
    SessionTraceManager sessionTraceManager;

    @Mock
    HttpServletRequest httpServletRequest;

    @InjectMocks
    KarteResource resource;

    @BeforeEach
    void setUp() {
        when(httpServletRequest.getRemoteUser()).thenReturn("FAC_A:user01");
    }

    @Test
    void getImageAllowsSameFacilitySchema() {
        SchemaModel schema = new SchemaModel();
        schema.setId(55L);
        KarteBean karteBean = new KarteBean();
        karteBean.setId(501L);
        schema.setKarteBean(karteBean);
        UserModel userModel = new UserModel();
        userModel.setId(601L);
        schema.setUserModel(userModel);
        when(karteServiceBean.findFacilityIdBySchemaId(55L)).thenReturn("FAC_A");
        when(karteServiceBean.getImage(55L)).thenReturn(schema);

        SchemaModelConverter result = resource.getImage(httpServletRequest, "55");

        assertThat(result.getId()).isEqualTo(55L);
        verify(karteServiceBean).getImage(55L);
    }

    @Test
    void getImageRejectsCrossFacilitySchema() {
        when(karteServiceBean.findFacilityIdBySchemaId(55L)).thenReturn("FAC_B");

        assertForbidden(() -> resource.getImage(httpServletRequest, "55"));
        verify(karteServiceBean, never()).getImage(55L);
    }

    @Test
    void deleteDiagnosisAllowsSameFacilityIds() {
        when(karteServiceBean.findFacilityIdByDiagnosisId(11L)).thenReturn("FAC_A");
        when(karteServiceBean.findFacilityIdByDiagnosisId(12L)).thenReturn("FAC_A");
        when(karteServiceBean.removeDiagnosis(List.of(11L, 12L))).thenReturn(2);

        resource.deleteDiagnosis("11,12");

        verify(karteServiceBean).removeDiagnosis(List.of(11L, 12L));
    }

    @Test
    void deleteDiagnosisRejectsWholeBatchOnCrossFacilityId() {
        when(karteServiceBean.findFacilityIdByDiagnosisId(11L)).thenReturn("FAC_A");
        when(karteServiceBean.findFacilityIdByDiagnosisId(12L)).thenReturn("FAC_B");

        assertForbidden(() -> resource.deleteDiagnosis("11,12"));
        verify(karteServiceBean, never()).removeDiagnosis(anyList());
    }

    @Test
    void deleteObservationsAllowsSameFacilityIds() {
        when(karteServiceBean.findFacilityIdByObservationId(21L)).thenReturn("FAC_A");
        when(karteServiceBean.findFacilityIdByObservationId(22L)).thenReturn("FAC_A");
        when(karteServiceBean.removeObservations(List.of(21L, 22L))).thenReturn(2);

        resource.deleteObservations("21,22");

        verify(karteServiceBean).removeObservations(List.of(21L, 22L));
    }

    @Test
    void deleteObservationsRejectsWholeBatchOnCrossFacilityId() {
        when(karteServiceBean.findFacilityIdByObservationId(21L)).thenReturn("FAC_A");
        when(karteServiceBean.findFacilityIdByObservationId(22L)).thenReturn("FAC_B");

        assertForbidden(() -> resource.deleteObservations("21,22"));
        verify(karteServiceBean, never()).removeObservations(anyList());
    }

    @Test
    void nullResolvedFacilityIsRejectedFailClosed() {
        when(karteServiceBean.findFacilityIdBySchemaId(55L)).thenReturn(null);

        assertForbidden(() -> resource.getImage(httpServletRequest, "55"));
        verify(karteServiceBean, never()).getImage(anyLong());
    }

    @Test
    void getDocumentListReturnsForbiddenForCrossFacilityKarteId() {
        when(karteServiceBean.findFacilityIdByKarteId(200L)).thenReturn("FAC_B");

        assertForbidden(() -> resource.getDocumentList(httpServletRequest, "200,2026-03-01 00:00:00,false"));
        verify(karteServiceBean, never()).getDocumentList(anyLong(), any(Date.class), anyBoolean());
    }

    @Test
    void getDocumentsReturnsForbiddenForCrossFacilityDocId() {
        when(karteServiceBean.findFacilityIdByDocId(300L)).thenReturn("FAC_B");

        assertForbidden(() -> resource.getDocuments("300"));
        verify(karteServiceBean, never()).getDocuments(anyList());
    }

    private static void assertForbidden(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
        assertThatThrownBy(callable)
                .isInstanceOf(WebApplicationException.class)
                .satisfies(ex -> assertThat(((WebApplicationException) ex).getResponse().getStatus()).isEqualTo(403));
    }
}
