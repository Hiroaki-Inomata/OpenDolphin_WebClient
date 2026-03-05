package open.dolphin.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ejb.ActivationConfigProperty;
import jakarta.ejb.MessageDriven;
import jakarta.inject.Inject;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageListener;
import jakarta.jms.TextMessage;
import java.io.BufferedReader;
import java.io.StringReader;
import java.util.Collection;
import java.util.Properties;
import open.dolphin.infomodel.HealthInsuranceModel;
import open.dolphin.infomodel.PatientVisitModel;
import open.dolphin.mbean.PVTBuilder;
import open.dolphin.msg.dto.JmsEnvelopeMessage;
import open.dolphin.msg.gateway.MessagingHeaders;
import open.orca.rest.ORCAConnection;
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
    private static final String TRACE_ID_PROPERTY = MessagingHeaders.TRACE_ID;
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();

    @Inject
    private PVTServiceBean pvtServiceBean;

    @Override
    public void onMessage(Message message) {
        String traceId = readTraceId(message);
        try {
            if (!(message instanceof TextMessage textMessage)) {
                LOGGER.warn("Unsupported JMS message type received: {}", message.getClass().getName());
                return;
            }
            String body = textMessage.getText();
            if (body == null || body.isBlank()) {
                LOGGER.warn("Empty JMS TextMessage body was rejected [traceId={}]", traceId);
                return;
            }
            JmsEnvelopeMessage envelope = JSON.readValue(body, JmsEnvelopeMessage.class);
            handleEnvelope(envelope, traceId);
        } catch (Exception ex) {
            LOGGER.warn("MessageSender rejected JMS message [traceId={}]", traceId, ex);
        }
    }

    private void handleEnvelope(JmsEnvelopeMessage envelope, String traceId) throws Exception {
        if (envelope == null || envelope.getType() == null || envelope.getType().isBlank()) {
            LOGGER.warn("JMS envelope without type was rejected [traceId={}]", traceId);
            return;
        }
        String type = envelope.getType().trim();
        if (JmsEnvelopeMessage.TYPE_PVT_XML.equals(type)) {
            handlePvt(envelope.getPvtXml(), traceId);
            return;
        }
        if (JmsEnvelopeMessage.TYPE_AUDIT_EVENT.equals(type)) {
            handleAuditEvent(envelope.getAudit(), traceId);
            return;
        }
        LOGGER.warn("Unsupported JMS envelope type was rejected [traceId={}, type={}]", traceId, type);
    }

    private void handleAuditEvent(JmsEnvelopeMessage.AuditMessage envelope, String traceId) {
        if (envelope == null) {
            LOGGER.warn("Audit envelope payload was empty [traceId={}]", traceId);
            return;
        }
        LOGGER.info("Audit envelope drained from JMS queue [traceId={}, action={}, resource={}, outcome={}]",
                traceId,
                envelope.getAction(),
                envelope.getResource(),
                envelope.getOutcome());
    }

    private void handlePvt(String pvtXml, String traceId) throws Exception {
        if (pvtXml == null || pvtXml.isBlank()) {
            LOGGER.warn("PVT XML payload was empty [traceId={}]", traceId);
            return;
        }
        String facilityId = resolveFacilityId();
        if (facilityId == null || facilityId.isBlank()) {
            LOGGER.warn("Facility ID unavailable; skipping PVT import [traceId={}]", traceId);
            return;
        }
        LOGGER.info("Processing PVT JMS message [traceId={}]", traceId);
        PatientVisitModel model = parsePvt(pvtXml, facilityId);
        if (model == null) {
            LOGGER.debug("Parsed PVT model is null; skipping addPvt [traceId={}]", traceId);
            return;
        }
        pvtServiceBean.addPvt(model);
    }

    private PatientVisitModel parsePvt(String pvtXml, String facilityId) throws Exception {
        BufferedReader reader = new BufferedReader(new StringReader(pvtXml));
        PVTBuilder builder = new PVTBuilder();
        builder.parse(reader);
        PatientVisitModel model = builder.getProduct();
        if (model == null) {
            return null;
        }

        model.setFacilityId(facilityId);
        if (model.getPatientModel() != null) {
            model.getPatientModel().setFacilityId(facilityId);
            Collection<HealthInsuranceModel> insurances = model.getPatientModel().getHealthInsurances();
            if (insurances != null) {
                for (HealthInsuranceModel insurance : insurances) {
                    insurance.setPatient(model.getPatientModel());
                }
            }
        }
        return model;
    }

    private String resolveFacilityId() {
        String systemProp = System.getProperty("dolphin.facilityId");
        if (systemProp != null && !systemProp.isBlank()) {
            return systemProp;
        }
        try {
            Properties props = ORCAConnection.getInstance().getProperties();
            if (props != null) {
                return props.getProperty("dolphin.facilityId");
            }
        } catch (Exception ex) {
            LOGGER.debug("Failed to resolve facilityId from ORCAConnection properties", ex);
        }
        return null;
    }

    private String readTraceId(Message message) {
        try {
            if (message.propertyExists(TRACE_ID_PROPERTY)) {
                return message.getStringProperty(TRACE_ID_PROPERTY);
            }
        } catch (JMSException ex) {
            LOGGER.debug("Failed to read traceId from JMS message", ex);
        }
        return null;
    }
}
