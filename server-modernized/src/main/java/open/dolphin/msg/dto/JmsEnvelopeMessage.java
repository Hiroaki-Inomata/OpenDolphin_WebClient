package open.dolphin.msg.dto;

import java.util.Map;
import open.dolphin.audit.AuditEventEnvelope;

/**
 * JMS テキストメッセージの明示 DTO。
 */
public class JmsEnvelopeMessage {

    public static final String TYPE_AUDIT_EVENT = "AUDIT_EVENT";
    public static final String TYPE_PVT_XML = "PVT_XML";

    private String type;
    private String pvtXml;
    private AuditMessage audit;

    public static JmsEnvelopeMessage forAudit(AuditEventEnvelope envelope) {
        JmsEnvelopeMessage message = new JmsEnvelopeMessage();
        message.setType(TYPE_AUDIT_EVENT);
        message.setAudit(AuditMessage.fromEnvelope(envelope));
        return message;
    }

    public static JmsEnvelopeMessage forPvt(String pvtXml) {
        JmsEnvelopeMessage message = new JmsEnvelopeMessage();
        message.setType(TYPE_PVT_XML);
        message.setPvtXml(pvtXml);
        return message;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getPvtXml() {
        return pvtXml;
    }

    public void setPvtXml(String pvtXml) {
        this.pvtXml = pvtXml;
    }

    public AuditMessage getAudit() {
        return audit;
    }

    public void setAudit(AuditMessage audit) {
        this.audit = audit;
    }

    public static class AuditMessage {
        private String action;
        private String resource;
        private String requestId;
        private String traceId;
        private String runId;
        private String actorId;
        private String facilityId;
        private String patientId;
        private String operation;
        private String outcome;
        private String errorCode;
        private String errorMessage;
        private Map<String, Object> details;

        public static AuditMessage fromEnvelope(AuditEventEnvelope envelope) {
            AuditMessage payload = new AuditMessage();
            payload.setAction(envelope.getAction());
            payload.setResource(envelope.getResource());
            payload.setRequestId(envelope.getRequestId());
            payload.setTraceId(envelope.getTraceId());
            payload.setRunId(envelope.getRunId());
            payload.setActorId(envelope.getActorId());
            payload.setFacilityId(envelope.getFacilityId());
            payload.setPatientId(envelope.getPatientId());
            payload.setOperation(envelope.getOperation());
            payload.setOutcome(envelope.getOutcome() != null ? envelope.getOutcome().name() : null);
            payload.setErrorCode(envelope.getErrorCode());
            payload.setErrorMessage(envelope.getErrorMessage());
            payload.setDetails(envelope.getDetails());
            return payload;
        }

        public String getAction() {
            return action;
        }

        public void setAction(String action) {
            this.action = action;
        }

        public String getResource() {
            return resource;
        }

        public void setResource(String resource) {
            this.resource = resource;
        }

        public String getRequestId() {
            return requestId;
        }

        public void setRequestId(String requestId) {
            this.requestId = requestId;
        }

        public String getTraceId() {
            return traceId;
        }

        public void setTraceId(String traceId) {
            this.traceId = traceId;
        }

        public String getRunId() {
            return runId;
        }

        public void setRunId(String runId) {
            this.runId = runId;
        }

        public String getActorId() {
            return actorId;
        }

        public void setActorId(String actorId) {
            this.actorId = actorId;
        }

        public String getFacilityId() {
            return facilityId;
        }

        public void setFacilityId(String facilityId) {
            this.facilityId = facilityId;
        }

        public String getPatientId() {
            return patientId;
        }

        public void setPatientId(String patientId) {
            this.patientId = patientId;
        }

        public String getOperation() {
            return operation;
        }

        public void setOperation(String operation) {
            this.operation = operation;
        }

        public String getOutcome() {
            return outcome;
        }

        public void setOutcome(String outcome) {
            this.outcome = outcome;
        }

        public String getErrorCode() {
            return errorCode;
        }

        public void setErrorCode(String errorCode) {
            this.errorCode = errorCode;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }

        public Map<String, Object> getDetails() {
            return details;
        }

        public void setDetails(Map<String, Object> details) {
            this.details = details;
        }
    }
}
