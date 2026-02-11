package open.dolphin.orca.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import open.dolphin.security.SecondFactorSecurityConfig;
import open.dolphin.security.totp.TotpSecretProtector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OrcaConnectionConfigStoreTest {

    @TempDir
    Path tempDir;

    private String originalDataDir;

    @AfterEach
    void tearDown() {
        if (originalDataDir == null) {
            System.clearProperty("jboss.server.data.dir");
        } else {
            System.setProperty("jboss.server.data.dir", originalDataDir);
        }
    }

    @Test
    void updatePersistsEncryptedConfigAndReloadsOnRestart() throws Exception {
        originalDataDir = System.getProperty("jboss.server.data.dir");
        System.setProperty("jboss.server.data.dir", tempDir.toString());

        TotpSecretProtector protector = buildProtector();
        OrcaConnectionConfigStore store = newStore(protector);

        OrcaConnectionConfigStore.UpdateRequest update = new OrcaConnectionConfigStore.UpdateRequest(
                Boolean.TRUE,
                "https://weborca-trial.orca.med.or.jp",
                443,
                "trial",
                "weborcatrial",
                Boolean.FALSE,
                null
        );

        OrcaConnectionConfigRecord saved = store.update(update, null, null, "RUN-TEST", "FACILITY:admin");
        assertNotNull(saved);

        Path file = tempDir.resolve("opendolphin").resolve("orca-connection-config.json");
        assertTrue(Files.exists(file));

        String rawJson = Files.readString(file);
        JsonNode root = new ObjectMapper().readTree(rawJson);
        assertEquals("https://weborca-trial.orca.med.or.jp", root.path("serverUrl").asText());
        assertEquals(443, root.path("port").asInt());
        assertEquals("trial", root.path("username").asText());

        String encryptedPassword = root.path("passwordEncrypted").asText();
        assertTrue(!encryptedPassword.isBlank());
        assertNotEquals("weborcatrial", encryptedPassword);

        OrcaConnectionConfigStore reloaded = newStore(protector);
        OrcaConnectionConfigRecord snapshot = reloaded.getSnapshot();
        assertNotNull(snapshot);
        assertEquals("https://weborca-trial.orca.med.or.jp", snapshot.getServerUrl());
        assertEquals(443, snapshot.getPort());
        assertEquals("trial", snapshot.getUsername());

        OrcaConnectionConfigStore.ResolvedOrcaConnection resolved = reloaded.resolve();
        assertEquals("https://weborca-trial.orca.med.or.jp", resolved.baseUrl());
        assertEquals("trial", resolved.username());
        assertEquals("weborcatrial", resolved.password());
    }

    private OrcaConnectionConfigStore newStore(TotpSecretProtector protector) throws Exception {
        OrcaConnectionConfigStore store = new OrcaConnectionConfigStore();
        SecondFactorSecurityConfig secondFactorSecurityConfig = mock(SecondFactorSecurityConfig.class);
        when(secondFactorSecurityConfig.getTotpSecretProtector()).thenReturn(protector);
        setField(store, "secondFactorSecurityConfig", secondFactorSecurityConfig);
        store.init();
        return store;
    }

    private TotpSecretProtector buildProtector() {
        byte[] key = new byte[32];
        for (int i = 0; i < key.length; i++) {
            key[i] = (byte) (i + 1);
        }
        String keyBase64 = Base64.getEncoder().encodeToString(key);
        return TotpSecretProtector.fromBase64(keyBase64);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
