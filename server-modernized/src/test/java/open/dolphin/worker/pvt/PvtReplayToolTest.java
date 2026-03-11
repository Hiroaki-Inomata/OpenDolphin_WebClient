package open.dolphin.worker.pvt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URL;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class PvtReplayToolTest {

    @Test
    void replayDetectsDuplicateWhenSamePayloadReplayed() throws Exception {
        Path sample = samplePayload();

        PvtReplayTool.ReplaySummary summary = PvtReplayTool.replay(
                List.of(sample),
                2,
                3,
                0,
                300_000L,
                10,
                0);

        assertEquals(2, summary.total());
        assertEquals(2, summary.ack());
        assertEquals(1, summary.duplicate());
        assertEquals(0, summary.nak());
        assertEquals(0, summary.poison());
        assertEquals(1, summary.handlerCalls());
    }

    @Test
    void replayMovesPayloadToPoisonAfterRetryExhausted() throws Exception {
        Path sample = samplePayload();

        PvtReplayTool.ReplaySummary summary = PvtReplayTool.replay(
                List.of(sample),
                1,
                2,
                0,
                300_000L,
                10,
                5);

        assertEquals(1, summary.total());
        assertEquals(0, summary.ack());
        assertEquals(1, summary.nak());
        assertEquals(1, summary.poison());
        assertEquals(2, summary.handlerCalls());
    }

    private Path samplePayload() {
        URL url = Thread.currentThread()
                .getContextClassLoader()
                .getResource("replay/pvt/normal-message.xml");
        assertTrue(url != null, "sample payload must exist");
        return Path.of(url.getPath());
    }
}
