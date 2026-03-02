package open.dolphin.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.WebApplicationException;
import java.util.Date;
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

