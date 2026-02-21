package open.dolphin.msg.gateway;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

class ExternalServiceAuditLoggerTest {

    @Test
    void smsDestinationsAreMaskedInAuditLog() {
        Logger logger = Logger.getLogger("open.dolphin.audit.external");
        CapturingHandler handler = new CapturingHandler();
        Level originalLevel = logger.getLevel();
        boolean originalUseParent = logger.getUseParentHandlers();
        logger.setLevel(Level.INFO);
        logger.setUseParentHandlers(false);
        logger.addHandler(handler);
        try {
            ExternalServiceAuditLogger.logSmsRequest(
                    "trace-sms",
                    List.of("+819012345678", "090-1111-2222"),
                    null);
        } finally {
            logger.removeHandler(handler);
            logger.setUseParentHandlers(originalUseParent);
            logger.setLevel(originalLevel);
        }

        LogRecord record = handler.records().stream()
                .filter(r -> r.getMessage() != null && r.getMessage().contains("SMS_REQUEST"))
                .findFirst()
                .orElse(null);
        assertNotNull(record);
        String message = record.getMessage();
        assertFalse(message.contains("+819012345678"));
        assertFalse(message.contains("090-1111-2222"));
        assertTrue(message.contains("5678"));
        assertTrue(message.contains("2222"));
    }

    private static final class CapturingHandler extends Handler {
        private final List<LogRecord> records = new CopyOnWriteArrayList<>();

        @Override
        public void publish(LogRecord record) {
            records.add(record);
        }

        @Override
        public void flush() {
            // no-op
        }

        @Override
        public void close() {
            records.clear();
        }

        private List<LogRecord> records() {
            return records;
        }
    }
}
