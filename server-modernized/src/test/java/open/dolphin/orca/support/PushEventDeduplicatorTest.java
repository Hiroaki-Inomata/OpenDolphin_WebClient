package open.dolphin.orca.support;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class PushEventDeduplicatorTest {

    @AfterEach
    void tearDown() {
        System.clearProperty("jboss.server.data.dir");
    }

    @Test
    void createDefaultUsesServerDataDirectoryWhenConfigured() throws Exception {
        Assumptions.assumeTrue(System.getenv("ORCA_PUSH_EVENT_CACHE_PATH") == null
                || System.getenv("ORCA_PUSH_EVENT_CACHE_PATH").isBlank());
        Path dataDir = Files.createTempDirectory("push-event-cache");
        System.setProperty("jboss.server.data.dir", dataDir.toString());

        PushEventDeduplicator deduplicator = PushEventDeduplicator.createDefault();
        deduplicator.filter("""
                {"Event_Information":[{"Event_Id":"E-001"}]}
                """);

        Path expected = dataDir.resolve("orca").resolve("pushevent-cache.json");
        assertTrue(Files.exists(expected), "cache should be persisted under jboss.server.data.dir");
    }
}
