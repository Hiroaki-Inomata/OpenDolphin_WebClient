package open.dolphin.session;

import jakarta.ejb.ActivationConfigProperty;
import jakarta.ejb.MessageDriven;
import jakarta.inject.Inject;
import jakarta.jms.Message;
import jakarta.jms.MessageListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * JMS メッセージドリブン Bean。リソースアダプタの指定は Jakarta Connectors のデプロイ記述子
 * （META-INF/ejb-jar.xml）に委譲し、実行時プロパティ {@code messaging.resource.adapter}
 * で外部化している。
 */
@MessageDriven(activationConfig = {
        @ActivationConfigProperty(propertyName = "destinationLookup", propertyValue = "java:/queue/dolphin"),
        @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "jakarta.jms.Queue"),
        @ActivationConfigProperty(propertyName = "acknowledgeMode", propertyValue = "Auto-acknowledge")
})
public class MessageSender implements MessageListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(MessageSender.class);

    @Inject
    private SessionMessageHandler sessionMessageHandler;

    @Override
    public void onMessage(Message message) {
        if (sessionMessageHandler == null) {
            LOGGER.warn("SessionMessageHandler is unavailable; skipping JMS message processing.");
            return;
        }
        sessionMessageHandler.onMessage(message);
    }
}
