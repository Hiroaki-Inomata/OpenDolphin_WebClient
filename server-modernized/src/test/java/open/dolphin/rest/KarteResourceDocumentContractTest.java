package open.dolphin.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import open.dolphin.security.audit.AuditTrailService;
import open.dolphin.session.KarteServiceBean;
import open.dolphin.session.PVTServiceBean;
import open.dolphin.session.framework.SessionTraceManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KarteResourceDocumentContractTest {

    @Mock
    KarteServiceBean karteServiceBean;

    @Mock
    PVTServiceBean pvtServiceBean;

    @Mock
    AuditTrailService auditTrailService;

    @Mock
    SessionTraceManager sessionTraceManager;

    @InjectMocks
    KarteResource resource;

    @Test
    void postDocumentReturnsPlainTextNumericPk() throws Exception {
        when(karteServiceBean.addDocument(any())).thenReturn(123L);

        String response = resource.postDocument("{}");

        assertThat(response).isEqualTo("123");
        verify(karteServiceBean).addDocument(any());
    }

    @Test
    void putDocumentReturnsPlainTextNumericPk() throws Exception {
        when(karteServiceBean.updateDocument(any())).thenReturn(123L);

        String response = resource.putDocument("{}");

        assertThat(response).isEqualTo("123");
        verify(karteServiceBean).updateDocument(any());
    }
}
