package open.dolphin.worker.pvt;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DateFormat;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * PVT socket reception worker. Owns socket lifecycle and delegates payload processing.
 */
public final class PvtSocketWorker implements Runnable {

    private static final int EOT = 0x04;
    private static final int ACK = 0x06;
    private static final int NAK = 0x15;
    private static final int DEFAULT_MAX_HANDLE_ATTEMPTS = 3;
    private static final int DEFAULT_RETRY_BACKOFF_MILLIS = 200;
    private static final long DEFAULT_IDEMPOTENCY_WINDOW_MILLIS = 5 * 60 * 1000L;
    private static final int DEFAULT_POISON_QUEUE_CAPACITY = 200;
    private static final int DEFAULT_PAYLOAD_PREVIEW_LENGTH = 2048;
    private static final int CLEANUP_INTERVAL = 64;

    private final ThreadFactory threadFactory;
    private final InetSocketAddress bindAddress;
    private final String encoding;
    private final int acceptTimeoutMillis;
    private final int readTimeoutMillis;
    private final int maxConnectionThreads;
    private final int connectionQueueCapacity;
    private final boolean debugEnabled;
    private final PayloadHandler payloadHandler;
    private final Consumer<String> infoLogger;
    private final Consumer<String> warnLogger;
    private final Consumer<String> debugLogger;
    private final int maxHandleAttempts;
    private final int handleRetryBackoffMillis;
    private final long idempotencyWindowMillis;
    private final int poisonQueueCapacity;
    private final ConcurrentHashMap<String, Long> processedPayloadHashes = new ConcurrentHashMap<>();
    private final AtomicInteger processedCount = new AtomicInteger();
    private final AtomicLong receivedCount = new AtomicLong();
    private final AtomicLong acknowledgedCount = new AtomicLong();
    private final AtomicLong failedCount = new AtomicLong();
    private final AtomicLong duplicateCount = new AtomicLong();
    private final AtomicLong retryAttemptCount = new AtomicLong();
    private final AtomicLong poisonTotalCount = new AtomicLong();
    private final AtomicLong lastReceivedEpochMillis = new AtomicLong();
    private final AtomicLong lastSuccessEpochMillis = new AtomicLong();
    private final AtomicLong lastFailureEpochMillis = new AtomicLong();
    private final AtomicLong maxProcessingMillis = new AtomicLong();
    private final AtomicLong totalProcessingMillis = new AtomicLong();
    private final AtomicInteger processingCount = new AtomicInteger();
    private final AtomicInteger consecutiveFailureCount = new AtomicInteger();
    private final Object poisonLock = new Object();
    private final ArrayDeque<PoisonPayloadRecord> poisonPayloads = new ArrayDeque<>();
    private volatile String lastFailureReason = "";
    private volatile long startedAtEpochMillis;
    private volatile long stoppedAtEpochMillis;
    private volatile boolean running;

    private volatile Thread acceptThread;
    private volatile ServerSocket listenSocket;
    private volatile ExecutorService connectionExecutor;

    public interface PayloadHandler {
        int handle(String payload) throws Exception;
    }

    public PvtSocketWorker(ThreadFactory threadFactory,
            InetSocketAddress bindAddress,
            String encoding,
            int acceptTimeoutMillis,
            int readTimeoutMillis,
            int maxConnectionThreads,
            int connectionQueueCapacity,
            boolean debugEnabled,
            int maxHandleAttempts,
            int handleRetryBackoffMillis,
            long idempotencyWindowMillis,
            int poisonQueueCapacity,
            PayloadHandler payloadHandler,
            Consumer<String> infoLogger,
            Consumer<String> warnLogger,
            Consumer<String> debugLogger) {
        this.threadFactory = threadFactory;
        this.bindAddress = bindAddress;
        this.encoding = encoding;
        this.acceptTimeoutMillis = acceptTimeoutMillis;
        this.readTimeoutMillis = readTimeoutMillis;
        this.maxConnectionThreads = maxConnectionThreads;
        this.connectionQueueCapacity = connectionQueueCapacity;
        this.debugEnabled = debugEnabled;
        this.maxHandleAttempts = maxHandleAttempts > 0 ? maxHandleAttempts : DEFAULT_MAX_HANDLE_ATTEMPTS;
        this.handleRetryBackoffMillis = Math.max(0, handleRetryBackoffMillis);
        this.idempotencyWindowMillis = idempotencyWindowMillis > 0L ? idempotencyWindowMillis : DEFAULT_IDEMPOTENCY_WINDOW_MILLIS;
        this.poisonQueueCapacity = poisonQueueCapacity > 0 ? poisonQueueCapacity : DEFAULT_POISON_QUEUE_CAPACITY;
        this.payloadHandler = payloadHandler;
        this.infoLogger = infoLogger;
        this.warnLogger = warnLogger;
        this.debugLogger = debugLogger;
    }

    public synchronized void start() throws IOException {
        if (acceptThread != null) {
            return;
        }
        running = true;
        startedAtEpochMillis = System.currentTimeMillis();
        stoppedAtEpochMillis = 0L;
        listenSocket = new ServerSocket();
        listenSocket.bind(bindAddress);
        listenSocket.setSoTimeout(acceptTimeoutMillis);

        connectionExecutor = new ThreadPoolExecutor(
                maxConnectionThreads,
                maxConnectionThreads,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(connectionQueueCapacity),
                new NamedThreadFactory(threadFactory, "pvt-connection-"),
                new ThreadPoolExecutor.AbortPolicy());

        acceptThread = new NamedThreadFactory(threadFactory, "pvt-accept-").newThread(this);
        acceptThread.start();

        info("PVT Server is binded " + bindAddress + " with encoding: " + encoding);
        info("server thread started");
    }

    public synchronized void stop() {
        running = false;
        stoppedAtEpochMillis = System.currentTimeMillis();
        Thread running = acceptThread;
        acceptThread = null;
        if (running != null) {
            running.interrupt();
        }

        if (listenSocket != null) {
            try {
                listenSocket.close();
                info("PVT Server is closed");
            } catch (IOException e) {
                warn(e.getMessage());
            } finally {
                listenSocket = null;
            }
        }

        if (connectionExecutor != null) {
            connectionExecutor.shutdownNow();
            try {
                if (!connectionExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    warn("PVT connection executor did not terminate within timeout");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                warn("Interrupted while waiting for PVT connection executor shutdown");
            } finally {
                connectionExecutor = null;
            }
        }
    }

    @Override
    public void run() {
        Thread thisThread = Thread.currentThread();
        while (thisThread == acceptThread) {
            try {
                ServerSocket socket = listenSocket;
                if (socket == null) {
                    return;
                }
                Socket clientSocket = socket.accept();
                clientSocket.setSoTimeout(readTimeoutMillis);
                ExecutorService executor = connectionExecutor;
                if (executor == null) {
                    warn("PVT connection executor is not initialized; dropping socket");
                    closeQuietly(clientSocket);
                    continue;
                }
                try {
                    executor.execute(new Connection(clientSocket));
                } catch (RejectedExecutionException ex) {
                    warn("PVT connection rejected due to executor saturation: " + ex.getMessage());
                    closeQuietly(clientSocket);
                }
            } catch (SocketTimeoutException timeout) {
                // accept timeout for cooperative shutdown check
            } catch (IOException e) {
                if (thisThread == acceptThread) {
                    warn(e.getMessage());
                }
            }
        }
    }

    private final class Connection implements Runnable {
        private Socket client;

        private Connection(Socket clientSocket) {
            this.client = clientSocket;
        }

        @Override
        public void run() {
            BufferedOutputStream writer = null;
            try {
                printInfo(client);
                BufferedInputStream reader = new BufferedInputStream(new DataInputStream(client.getInputStream()));
                writer = new BufferedOutputStream(new DataOutputStream(client.getOutputStream()));

                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                BufferedOutputStream buf = new BufferedOutputStream(bo);
                byte[] buffer = new byte[16384];

                while (true) {
                    int readLen = reader.read(buffer);
                    if (readLen == -1) {
                        debug("EOF");
                        break;
                    }
                    if (buffer[readLen - 1] == EOT) {
                        buf.write(buffer, 0, readLen - 1);
                        buf.flush();
                        String received = bo.toString(encoding);
                        int len = received.length();
                        bo.close();
                        buf.close();

                        info("length of claim instance = " + len + " bytes");
                        debug(received);
                        info(received);

                        PayloadProcessingResult result = processPayload(received);
                        writeRetCode(writer, result.acknowledged() ? ACK : NAK);
                    } else {
                        buf.write(buffer, 0, readLen);
                    }
                }
                reader.close();
                writer.close();
                client.close();
                client = null;
            } catch (Exception e) {
                writeRetCode(writer, NAK);
                warn(e.getMessage());
            } finally {
                closeQuietly(client);
                client = null;
            }
        }

        private void writeRetCode(BufferedOutputStream writer, int retCode) {
            if (writer == null) {
                return;
            }
            try {
                writer.write(retCode);
                writer.flush();
                info("return code = " + retCode);
            } catch (Exception e) {
                warn(e.getMessage());
            }
        }
    }

    PayloadProcessingResult processPayload(String payload) {
        if (payload == null) {
            return PayloadProcessingResult.nak("empty_payload");
        }
        final long now = System.currentTimeMillis();
        receivedCount.incrementAndGet();
        lastReceivedEpochMillis.set(now);
        long startMillis = now;
        processingCount.incrementAndGet();
        final String hash = sha256(payload);

        if (isDuplicate(hash, now)) {
            acknowledgedCount.incrementAndGet();
            duplicateCount.incrementAndGet();
            lastSuccessEpochMillis.set(now);
            consecutiveFailureCount.set(0);
            finishProcessing(startMillis);
            info("Skip duplicate PVT payload [hash=" + hash + "]");
            return PayloadProcessingResult.duplicateAck();
        }

        Exception lastError = null;
        for (int attempt = 1; attempt <= maxHandleAttempts; attempt++) {
            try {
                payloadHandler.handle(payload);
                processedPayloadHashes.put(hash, now);
                cleanupProcessedHashes(now);
                acknowledgedCount.incrementAndGet();
                if (attempt > 1) {
                    retryAttemptCount.addAndGet(attempt - 1L);
                }
                lastSuccessEpochMillis.set(System.currentTimeMillis());
                consecutiveFailureCount.set(0);
                finishProcessing(startMillis);
                return PayloadProcessingResult.ack(attempt);
            } catch (Exception ex) {
                lastError = ex;
                warn("PVT payload handling failed [attempt=" + attempt + "/" + maxHandleAttempts + ", hash=" + hash + "]: "
                        + ex.getMessage());
                if (attempt >= maxHandleAttempts) {
                    break;
                }
                if (!sleepBackoff()) {
                    if (attempt > 1) {
                        retryAttemptCount.addAndGet(attempt - 1L);
                    }
                    failedCount.incrementAndGet();
                    lastFailureEpochMillis.set(System.currentTimeMillis());
                    lastFailureReason = "interrupted_during_retry";
                    consecutiveFailureCount.incrementAndGet();
                    recordPoison(hash, payload, "interrupted_during_retry", attempt, ex);
                    finishProcessing(startMillis);
                    return PayloadProcessingResult.nak("interrupted");
                }
            }
        }

        if (maxHandleAttempts > 1) {
            retryAttemptCount.addAndGet(maxHandleAttempts - 1L);
        }
        failedCount.incrementAndGet();
        lastFailureEpochMillis.set(System.currentTimeMillis());
        lastFailureReason = "max_retry_exceeded";
        consecutiveFailureCount.incrementAndGet();
        recordPoison(hash, payload, "max_retry_exceeded", maxHandleAttempts, lastError);
        finishProcessing(startMillis);
        return PayloadProcessingResult.nak("max_retry_exceeded");
    }

    List<PoisonPayloadRecord> snapshotPoisonPayloads() {
        synchronized (poisonLock) {
            return new ArrayList<>(poisonPayloads);
        }
    }

    private boolean sleepBackoff() {
        if (handleRetryBackoffMillis <= 0) {
            return true;
        }
        try {
            Thread.sleep(handleRetryBackoffMillis);
            return true;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private boolean isDuplicate(String hash, long now) {
        Long previous = processedPayloadHashes.get(hash);
        if (previous == null) {
            cleanupProcessedHashes(now);
            return false;
        }
        if (now - previous <= idempotencyWindowMillis) {
            cleanupProcessedHashes(now);
            return true;
        }
        processedPayloadHashes.remove(hash, previous);
        cleanupProcessedHashes(now);
        return false;
    }

    private void cleanupProcessedHashes(long now) {
        if (processedCount.incrementAndGet() % CLEANUP_INTERVAL != 0) {
            return;
        }
        processedPayloadHashes.entrySet().removeIf(entry -> now - entry.getValue() > idempotencyWindowMillis);
    }

    private String sha256(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private void recordPoison(String hash, String payload, String reason, int attempts, Exception error) {
        String preview = payload.length() <= DEFAULT_PAYLOAD_PREVIEW_LENGTH
                ? payload
                : payload.substring(0, DEFAULT_PAYLOAD_PREVIEW_LENGTH);
        PoisonPayloadRecord record = new PoisonPayloadRecord(
                hash,
                Instant.now().toEpochMilli(),
                attempts,
                reason,
                preview,
                error != null ? Objects.toString(error.getMessage(), error.getClass().getSimpleName()) : null);
        synchronized (poisonLock) {
            while (poisonPayloads.size() >= poisonQueueCapacity) {
                poisonPayloads.removeFirst();
            }
            poisonPayloads.addLast(record);
        }
        poisonTotalCount.incrementAndGet();
        warn("PVT payload moved to poison queue [hash=" + hash + ", reason=" + reason + ", attempts=" + attempts + "]");
    }

    public RuntimeSnapshot snapshotRuntime() {
        int poisonQueueSize;
        synchronized (poisonLock) {
            poisonQueueSize = poisonPayloads.size();
        }
        return new RuntimeSnapshot(
                running,
                startedAtEpochMillis,
                stoppedAtEpochMillis,
                receivedCount.get(),
                acknowledgedCount.get(),
                failedCount.get(),
                duplicateCount.get(),
                retryAttemptCount.get(),
                poisonTotalCount.get(),
                poisonQueueSize,
                lastReceivedEpochMillis.get(),
                lastSuccessEpochMillis.get(),
                lastFailureEpochMillis.get(),
                lastFailureReason,
                maxProcessingMillis.get(),
                totalProcessingMillis.get(),
                processingCount.get(),
                consecutiveFailureCount.get(),
                maxHandleAttempts,
                handleRetryBackoffMillis,
                idempotencyWindowMillis,
                poisonQueueCapacity);
    }

    private void finishProcessing(long startMillis) {
        long elapsed = Math.max(0L, System.currentTimeMillis() - startMillis);
        totalProcessingMillis.addAndGet(elapsed);
        maxProcessingMillis.accumulateAndGet(elapsed, Math::max);
        processingCount.updateAndGet(current -> current > 0 ? current - 1 : 0);
    }

    private void printInfo(Socket clientSocket) {
        String addr = clientSocket.getInetAddress().getHostAddress();
        String time = DateFormat.getDateTimeInstance().format(new Date());
        info("connected from " + addr + " at " + time);
    }

    private void closeQuietly(Socket socket) {
        if (socket == null) {
            return;
        }
        try {
            socket.close();
        } catch (IOException ignore) {
            // ignore
        }
    }

    private void info(String msg) {
        if (infoLogger != null) {
            infoLogger.accept(msg);
        }
    }

    private void warn(String msg) {
        if (warnLogger != null) {
            warnLogger.accept(msg);
        }
    }

    private void debug(String msg) {
        if (debugEnabled && debugLogger != null) {
            debugLogger.accept(msg);
        }
    }

    private static final class NamedThreadFactory implements ThreadFactory {
        private final ThreadFactory delegate;
        private final String prefix;
        private final AtomicInteger sequence = new AtomicInteger(1);

        private NamedThreadFactory(ThreadFactory delegate, String prefix) {
            this.delegate = delegate;
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = delegate.newThread(runnable);
            thread.setName(prefix + sequence.getAndIncrement());
            return thread;
        }
    }

    static final class PayloadProcessingResult {
        private final boolean acknowledged;
        private final boolean duplicate;
        private final int attempts;
        private final String reason;

        private PayloadProcessingResult(boolean acknowledged, boolean duplicate, int attempts, String reason) {
            this.acknowledged = acknowledged;
            this.duplicate = duplicate;
            this.attempts = attempts;
            this.reason = reason;
        }

        static PayloadProcessingResult ack(int attempts) {
            return new PayloadProcessingResult(true, false, attempts, "ok");
        }

        static PayloadProcessingResult duplicateAck() {
            return new PayloadProcessingResult(true, true, 0, "duplicate");
        }

        static PayloadProcessingResult nak(String reason) {
            return new PayloadProcessingResult(false, false, 0, reason);
        }

        boolean acknowledged() {
            return acknowledged;
        }

        boolean duplicate() {
            return duplicate;
        }

        int attempts() {
            return attempts;
        }

        String reason() {
            return reason;
        }
    }

    static final class PoisonPayloadRecord {
        private final String hash;
        private final long receivedAtEpochMillis;
        private final int attempts;
        private final String reason;
        private final String payloadPreview;
        private final String errorMessage;

        private PoisonPayloadRecord(String hash, long receivedAtEpochMillis, int attempts, String reason,
                String payloadPreview, String errorMessage) {
            this.hash = hash;
            this.receivedAtEpochMillis = receivedAtEpochMillis;
            this.attempts = attempts;
            this.reason = reason;
            this.payloadPreview = payloadPreview;
            this.errorMessage = errorMessage;
        }

        String hash() {
            return hash;
        }

        long receivedAtEpochMillis() {
            return receivedAtEpochMillis;
        }

        int attempts() {
            return attempts;
        }

        String reason() {
            return reason;
        }

        String payloadPreview() {
            return payloadPreview;
        }

        String errorMessage() {
            return errorMessage;
        }
    }

    public record RuntimeSnapshot(
            boolean running,
            long startedAtEpochMillis,
            long stoppedAtEpochMillis,
            long receivedCount,
            long acknowledgedCount,
            long failedCount,
            long duplicateCount,
            long retryAttemptCount,
            long poisonTotalCount,
            int poisonQueueSize,
            long lastReceivedEpochMillis,
            long lastSuccessEpochMillis,
            long lastFailureEpochMillis,
            String lastFailureReason,
            long maxProcessingMillis,
            long totalProcessingMillis,
            int processingCount,
            int consecutiveFailureCount,
            int maxHandleAttempts,
            int handleRetryBackoffMillis,
            long idempotencyWindowMillis,
            int poisonQueueCapacity) {

        public static RuntimeSnapshot disabled() {
            return new RuntimeSnapshot(
                    false,
                    0L,
                    0L,
                    0L,
                    0L,
                    0L,
                    0L,
                    0L,
                    0L,
                    0,
                    0L,
                    0L,
                    0L,
                    "",
                    0L,
                    0L,
                    0,
                    0,
                    DEFAULT_MAX_HANDLE_ATTEMPTS,
                    DEFAULT_RETRY_BACKOFF_MILLIS,
                    DEFAULT_IDEMPOTENCY_WINDOW_MILLIS,
                    DEFAULT_POISON_QUEUE_CAPACITY);
        }
    }
}
