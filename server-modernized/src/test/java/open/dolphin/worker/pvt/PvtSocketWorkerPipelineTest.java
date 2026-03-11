package open.dolphin.worker.pvt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class PvtSocketWorkerPipelineTest {

    @Test
    void duplicatePayloadIsAcknowledgedWithoutReprocess() {
        AtomicInteger calls = new AtomicInteger();
        PvtSocketWorker worker = newWorker(payload -> calls.incrementAndGet());

        PvtSocketWorker.PayloadProcessingResult first = worker.processPayload("<pvt>same</pvt>");
        PvtSocketWorker.PayloadProcessingResult second = worker.processPayload("<pvt>same</pvt>");

        assertTrue(first.acknowledged());
        assertFalse(first.duplicate());
        assertTrue(second.acknowledged());
        assertTrue(second.duplicate());
        assertEquals(1, calls.get());
        PvtSocketWorker.RuntimeSnapshot snapshot = worker.snapshotRuntime();
        assertEquals(2, snapshot.receivedCount());
        assertEquals(2, snapshot.acknowledgedCount());
        assertEquals(1, snapshot.duplicateCount());
        assertEquals(0, snapshot.failedCount());
        assertTrue(snapshot.lastSuccessEpochMillis() > 0L);
    }

    @Test
    void retryUntilSuccessWithinMaxAttempts() {
        AtomicInteger calls = new AtomicInteger();
        PvtSocketWorker worker = newWorker(payload -> {
            if (calls.incrementAndGet() < 3) {
                throw new IllegalStateException("temporary");
            }
            return 1;
        });

        PvtSocketWorker.PayloadProcessingResult result = worker.processPayload("<pvt>retry</pvt>");

        assertTrue(result.acknowledged());
        assertEquals(3, result.attempts());
        assertEquals(3, calls.get());
        PvtSocketWorker.RuntimeSnapshot snapshot = worker.snapshotRuntime();
        assertEquals(1, snapshot.receivedCount());
        assertEquals(1, snapshot.acknowledgedCount());
        assertEquals(2, snapshot.retryAttemptCount());
        assertEquals(0, snapshot.failedCount());
    }

    @Test
    void poisonQueueStoresPayloadAfterRetryExhausted() {
        AtomicInteger calls = new AtomicInteger();
        PvtSocketWorker worker = newWorker(payload -> {
            calls.incrementAndGet();
            throw new IllegalStateException("fatal");
        });

        PvtSocketWorker.PayloadProcessingResult result = worker.processPayload("<pvt>fatal</pvt>");
        List<PvtSocketWorker.PoisonPayloadRecord> poison = worker.snapshotPoisonPayloads();

        assertFalse(result.acknowledged());
        assertEquals("max_retry_exceeded", result.reason());
        assertEquals(3, calls.get());
        assertEquals(1, poison.size());
        assertEquals(3, poison.get(0).attempts());
        assertEquals("max_retry_exceeded", poison.get(0).reason());
        assertTrue(poison.get(0).payloadPreview().contains("fatal"));
        PvtSocketWorker.RuntimeSnapshot snapshot = worker.snapshotRuntime();
        assertEquals(1, snapshot.receivedCount());
        assertEquals(0, snapshot.acknowledgedCount());
        assertEquals(1, snapshot.failedCount());
        assertEquals(2, snapshot.retryAttemptCount());
        assertEquals(1, snapshot.poisonTotalCount());
        assertEquals(1, snapshot.poisonQueueSize());
        assertEquals("max_retry_exceeded", snapshot.lastFailureReason());
    }

    private PvtSocketWorker newWorker(PvtSocketWorker.PayloadHandler handler) {
        return new PvtSocketWorker(
                Thread::new,
                new InetSocketAddress("127.0.0.1", 0),
                "UTF-8",
                1000,
                1000,
                1,
                1,
                false,
                3,
                0,
                300_000L,
                5,
                handler,
                msg -> { },
                msg -> { },
                msg -> { });
    }
}
