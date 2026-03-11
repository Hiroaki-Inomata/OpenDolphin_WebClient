package open.dolphin.orca.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OrcaTransportSettingsExternalConfigTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void tearDown() {
        System.clearProperty("jboss.home.dir");
        System.clearProperty("orca.base-url");
        System.clearProperty("orca.api.host");
        System.clearProperty("orca.api.port");
        System.clearProperty("orca.api.scheme");
        System.clearProperty("orca.api.user");
        System.clearProperty("orca.api.password");
        System.clearProperty("orca.api.path-prefix");
        System.clearProperty("orca.api.weborca");
        System.clearProperty("orca.mode");
        System.clearProperty("orca.api.retry.max");
        System.clearProperty("orca.api.retry.backoff-ms");
    }

    @Test
    void loadUsesExternalSystemPropertiesWithoutCustomProperties() {
        System.setProperty("orca.api.host", "weborca-trial.orca.med.or.jp");
        System.setProperty("orca.api.port", "443");
        System.setProperty("orca.api.scheme", "https");
        System.setProperty("orca.api.user", "trial-user");
        System.setProperty("orca.api.password", "trial-password");
        System.setProperty("orca.mode", "weborca");
        System.setProperty("orca.api.path-prefix", "/api");

        OrcaTransportSettings settings = OrcaTransportSettings.load();

        assertTrue(settings.isReady());
        assertEquals("https://weborca-trial.orca.med.or.jp/api/orca11/appointmodv2",
                settings.buildOrcaUrl("/orca11/appointmodv2"));
    }

    @Test
    void loadIgnoresLegacyCustomPropertiesFile() throws Exception {
        System.setProperty("jboss.home.dir", tempDir.toString());
        Files.writeString(tempDir.resolve("custom.properties"), String.join("\n",
                "orca.orcaapi.ip=legacy-host",
                "orca.orcaapi.port=8000",
                "orca.id=legacy-user",
                "orca.password=legacy-password"));

        OrcaTransportSettings settings = OrcaTransportSettings.load();

        assertFalse(settings.isReady());
    }
}
