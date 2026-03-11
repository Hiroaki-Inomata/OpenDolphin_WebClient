package open.dolphin.session;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import jakarta.jms.Message;
import java.lang.reflect.Field;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MessageSenderTest {

    private MessageSender sender;
    private SessionMessageHandler sessionMessageHandler;

    @BeforeEach
    void setUp() throws Exception {
        sender = new MessageSender();
        sessionMessageHandler = mock(SessionMessageHandler.class);
        setField(sender, "sessionMessageHandler", sessionMessageHandler);
    }

    @Test
    void delegatesMessageToSessionHandler() throws Exception {
        Message message = mock(Message.class);
        sender.onMessage(message);
        verify(sessionMessageHandler).onMessage(message);
    }

    @Test
    void nullHandlerDoesNotThrow() throws Exception {
        setField(sender, "sessionMessageHandler", null);
        Message message = mock(Message.class);

        sender.onMessage(message);
        verify(sessionMessageHandler, org.mockito.Mockito.never()).onMessage(any());
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
