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
import java.text.DateFormat;
import java.util.Date;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * PVT socket reception worker. Owns socket lifecycle and delegates payload processing.
 */
public final class PvtSocketWorker implements Runnable {

    private static final int EOT = 0x04;
    private static final int ACK = 0x06;
    private static final int NAK = 0x15;

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
        this.payloadHandler = payloadHandler;
        this.infoLogger = infoLogger;
        this.warnLogger = warnLogger;
        this.debugLogger = debugLogger;
    }

    public synchronized void start() throws IOException {
        if (acceptThread != null) {
            return;
        }
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

                        payloadHandler.handle(received);
                        writeRetCode(writer, ACK);
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
}
