package open.dolphin.session;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.jms.Message;
import jakarta.jms.TextMessage;
import java.lang.reflect.Field;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MessageSenderTest {

    private MessageSender sender;
    private PVTServiceBean pvtServiceBean;

    @BeforeEach
    void setUp() throws Exception {
        sender = new MessageSender();
        pvtServiceBean = mock(PVTServiceBean.class);
        setField(sender, "pvtServiceBean", pvtServiceBean);
    }

    @Test
    void nonTextMessageIsRejectedWithoutProcessing() throws Exception {
        Message message = mock(Message.class);
        when(message.propertyExists(anyString())).thenReturn(false);

        sender.onMessage(message);

        verify(pvtServiceBean, never()).addPvt(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void malformedTextMessageIsRejectedWithoutProcessing() throws Exception {
        TextMessage message = mock(TextMessage.class);
        when(message.propertyExists(anyString())).thenReturn(false);
        when(message.getText()).thenReturn("{invalid-json");

        sender.onMessage(message);

        verify(pvtServiceBean, never()).addPvt(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void objectStylePayloadTypeIsRejectedWithoutProcessing() throws Exception {
        TextMessage message = mock(TextMessage.class);
        when(message.propertyExists(anyString())).thenReturn(false);
        when(message.getText()).thenReturn("{\"type\":\"UNSUPPORTED\",\"payload\":{\"k\":\"v\"}}");

        sender.onMessage(message);

        verify(pvtServiceBean, never()).addPvt(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void auditEnvelopeIsAcceptedWithoutPvtImport() throws Exception {
        TextMessage message = mock(TextMessage.class);
        when(message.propertyExists(anyString())).thenReturn(false);
        when(message.getText()).thenReturn(
                "{\"type\":\"AUDIT_EVENT\",\"audit\":{\"action\":\"ORCA_ACCEPT_LIST\",\"outcome\":\"SUCCESS\"}}");

        sender.onMessage(message);

        verify(pvtServiceBean, never()).addPvt(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void pvtEnvelopeWithBlankXmlIsRejectedWithoutProcessing() throws Exception {
        TextMessage message = mock(TextMessage.class);
        when(message.propertyExists(anyString())).thenReturn(false);
        when(message.getText()).thenReturn("{\"type\":\"PVT_XML\",\"pvtXml\":\"   \"}");

        sender.onMessage(message);

        verify(pvtServiceBean, never()).addPvt(org.mockito.ArgumentMatchers.any());
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
