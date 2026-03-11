package open.dolphin.worker.pvt;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Replay helper for PVT worker pipeline tests.
 */
public final class PvtReplayTool {

    private PvtReplayTool() {
    }

    public static void main(String[] args) throws Exception {
        CliOptions options = CliOptions.parse(args);
        List<Path> files = collectPayloadFiles(options.inputPath());
        if (files.isEmpty()) {
            throw new IllegalArgumentException("No payload files found: " + options.inputPath());
        }
        ReplaySummary summary = replay(files, options.repeat(), options.retryMax(), options.retryBackoffMillis(),
                options.idempotencyWindowMillis(), options.poisonCapacity(), options.failFirstAttempts());
        System.out.println(summary.toLine());
    }

    static ReplaySummary replay(List<Path> payloadFiles,
            int repeat,
            int retryMax,
            int retryBackoffMillis,
            long idempotencyWindowMillis,
            int poisonCapacity,
            int failFirstAttempts) throws IOException {
        AtomicInteger handlerCalls = new AtomicInteger();
        PvtSocketWorker worker = new PvtSocketWorker(
                Thread::new,
                new InetSocketAddress("127.0.0.1", 0),
                "UTF-8",
                1000,
                1000,
                1,
                1,
                false,
                retryMax,
                retryBackoffMillis,
                idempotencyWindowMillis,
                poisonCapacity,
                payload -> {
                    int call = handlerCalls.incrementAndGet();
                    if (call <= failFirstAttempts) {
                        throw new IllegalStateException("injected-failure-" + call);
                    }
                    return 1;
                },
                msg -> { },
                msg -> { },
                msg -> { });

        int ack = 0;
        int duplicate = 0;
        int nak = 0;
        int total = 0;

        for (int i = 0; i < repeat; i++) {
            for (Path payloadFile : payloadFiles) {
                String payload = Files.readString(payloadFile, StandardCharsets.UTF_8);
                PvtSocketWorker.PayloadProcessingResult result = worker.processPayload(payload);
                total++;
                if (result.acknowledged()) {
                    ack++;
                    if (result.duplicate()) {
                        duplicate++;
                    }
                } else {
                    nak++;
                }
            }
        }

        int poison = worker.snapshotPoisonPayloads().size();
        return new ReplaySummary(total, ack, duplicate, nak, poison, handlerCalls.get());
    }

    private static List<Path> collectPayloadFiles(Path inputPath) throws IOException {
        if (Files.isRegularFile(inputPath)) {
            return List.of(inputPath);
        }
        if (!Files.isDirectory(inputPath)) {
            return List.of();
        }
        try (var stream = Files.walk(inputPath)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        String name = path.getFileName().toString().toLowerCase();
                        return name.endsWith(".xml") || name.endsWith(".txt");
                    })
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        }
    }

    record ReplaySummary(int total,
            int ack,
            int duplicate,
            int nak,
            int poison,
            int handlerCalls) {

        String toLine() {
            return "replay-summary total=" + total
                    + " ack=" + ack
                    + " duplicate=" + duplicate
                    + " nak=" + nak
                    + " poison=" + poison
                    + " handlerCalls=" + handlerCalls;
        }
    }

    private record CliOptions(Path inputPath,
            int repeat,
            int retryMax,
            int retryBackoffMillis,
            long idempotencyWindowMillis,
            int poisonCapacity,
            int failFirstAttempts) {

        static CliOptions parse(String[] args) {
            Path inputPath = null;
            int repeat = 1;
            int retryMax = 3;
            int retryBackoffMillis = 0;
            long idempotencyWindowMillis = 300_000L;
            int poisonCapacity = 200;
            int failFirstAttempts = 0;

            List<String> list = new ArrayList<>(List.of(args));
            for (int i = 0; i < list.size(); i++) {
                String arg = list.get(i);
                if ("--input".equals(arg) && i + 1 < list.size()) {
                    inputPath = Path.of(list.get(++i));
                } else if ("--repeat".equals(arg) && i + 1 < list.size()) {
                    repeat = Integer.parseInt(list.get(++i));
                } else if ("--retry-max".equals(arg) && i + 1 < list.size()) {
                    retryMax = Integer.parseInt(list.get(++i));
                } else if ("--retry-backoff-ms".equals(arg) && i + 1 < list.size()) {
                    retryBackoffMillis = Integer.parseInt(list.get(++i));
                } else if ("--idempotency-window-ms".equals(arg) && i + 1 < list.size()) {
                    idempotencyWindowMillis = Long.parseLong(list.get(++i));
                } else if ("--poison-capacity".equals(arg) && i + 1 < list.size()) {
                    poisonCapacity = Integer.parseInt(list.get(++i));
                } else if ("--fail-first".equals(arg) && i + 1 < list.size()) {
                    failFirstAttempts = Integer.parseInt(list.get(++i));
                }
            }

            if (inputPath == null) {
                throw new IllegalArgumentException("Missing required option: --input <file-or-directory>");
            }

            return new CliOptions(inputPath, Math.max(1, repeat), Math.max(1, retryMax),
                    Math.max(0, retryBackoffMillis), Math.max(1L, idempotencyWindowMillis),
                    Math.max(1, poisonCapacity), Math.max(0, failFirstAttempts));
        }
    }
}
