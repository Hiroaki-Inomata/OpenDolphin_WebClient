package open.dolphin.orca.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import open.dolphin.orca.transport.OrcaConnectionPolicyException;
import open.dolphin.orca.transport.OrcaTransportSecurityPolicy;
import open.dolphin.runtime.RuntimeConfigurationSupport;
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
        System.clearProperty("orca.base-url");
        System.clearProperty("orca.api.host");
        System.clearProperty("orca.api.port");
        System.clearProperty("orca.api.scheme");
        System.clearProperty("orca.api.user");
        System.clearProperty("orca.api.password");
        System.clearProperty("orca.mode");
        System.clearProperty("orca.api.weborca");
        System.clearProperty(RuntimeConfigurationSupport.PROP_ENVIRONMENT);
        System.clearProperty(OrcaTransportSecurityPolicy.PROP_ALLOW_INSECURE_HTTP);
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

    @Test
    void updateFacilityAlsoRefreshesDefaultFallbackRecord() throws Exception {
        originalDataDir = System.getProperty("jboss.server.data.dir");
        System.setProperty("jboss.server.data.dir", tempDir.toString());

        TotpSecretProtector protector = buildProtector();
        OrcaConnectionConfigStore store = newStore(protector);

        OrcaConnectionConfigStore.UpdateRequest initial = new OrcaConnectionConfigStore.UpdateRequest(
                Boolean.TRUE,
                "https://old.example.orca",
                443,
                "old-user",
                "old-pass",
                Boolean.FALSE,
                null
        );
        store.update(initial, null, null, "RUN-INITIAL", "FACILITY:admin");

        OrcaConnectionConfigStore.UpdateRequest facilityUpdate = new OrcaConnectionConfigStore.UpdateRequest(
                Boolean.TRUE,
                "https://new.example.orca",
                443,
                "new-user",
                "new-pass",
                Boolean.FALSE,
                null
        );
        store.update("F001", facilityUpdate, null, null, "RUN-F001", "FACILITY:admin");

        OrcaConnectionConfigRecord defaultSnapshot = store.getSnapshot();
        assertNotNull(defaultSnapshot);
        assertEquals("https://new.example.orca", defaultSnapshot.getServerUrl());
        assertEquals("new-user", defaultSnapshot.getUsername());

        OrcaConnectionConfigRecord unresolvedFacilitySnapshot = store.getSnapshot("UNKNOWN");
        assertNotNull(unresolvedFacilitySnapshot);
        assertEquals("https://new.example.orca", unresolvedFacilitySnapshot.getServerUrl());
        assertEquals("new-user", unresolvedFacilitySnapshot.getUsername());

        OrcaConnectionConfigRecord facilitySnapshot = store.getSnapshot("F001");
        assertNotNull(facilitySnapshot);
        assertEquals("F001", facilitySnapshot.getFacilityId());
        assertEquals("https://new.example.orca", facilitySnapshot.getServerUrl());

        Path file = tempDir.resolve("opendolphin").resolve("orca-connection-config.json");
        String rawJson = Files.readString(file);
        JsonNode root = new ObjectMapper().readTree(rawJson);
        assertEquals("https://new.example.orca", root.path("records").path("_default").path("serverUrl").asText());
        assertEquals("new-user", root.path("records").path("_default").path("username").asText());

        OrcaConnectionConfigStore.ResolvedOrcaConnection resolvedDefault = store.resolve();
        assertEquals("new-user", resolvedDefault.username());
        assertEquals("new-pass", resolvedDefault.password());
    }

    @Test
    void updateRejectsInsecureHttpInProduction() throws Exception {
        originalDataDir = System.getProperty("jboss.server.data.dir");
        System.setProperty("jboss.server.data.dir", tempDir.toString());
        System.setProperty(RuntimeConfigurationSupport.PROP_ENVIRONMENT, "production");

        TotpSecretProtector protector = buildProtector();
        OrcaConnectionConfigStore store = newStore(protector);

        OrcaConnectionConfigStore.UpdateRequest update = new OrcaConnectionConfigStore.UpdateRequest(
                Boolean.TRUE,
                "http://weborca.example.test",
                80,
                "trial",
                "weborcatrial",
                Boolean.FALSE,
                null
        );

        OrcaConnectionPolicyException ex = org.junit.jupiter.api.Assertions.assertThrows(
                OrcaConnectionPolicyException.class,
                () -> store.update(update, null, null, "RUN-TEST", "FACILITY:admin")
        );

        assertEquals("weborca_requires_https", ex.getErrorCategory());
    }

    @Test
    void initRejectsLegacySingleRecordConfig() throws Exception {
        originalDataDir = System.getProperty("jboss.server.data.dir");
        System.setProperty("jboss.server.data.dir", tempDir.toString());

        Path configFile = tempDir.resolve("opendolphin").resolve("orca-connection-config.json");
        Files.createDirectories(configFile.getParent());
        Files.writeString(configFile, """
                {
                  "version": 1,
                  "serverUrl": "https://legacy.example.orca",
                  "port": 443,
                  "username": "legacy-user",
                  "passwordEncrypted": "encrypted",
                  "useWeborca": true
                }
                """);

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> newStore(buildProtector())
        );

        assertEquals(
                "Legacy single-record ORCA connection config is no longer supported. Migrate to the records format.",
                ex.getMessage()
        );
    }

    @Test
    void initReadsExternalSystemPropertiesWhenEnvIsMissing() throws Exception {
        originalDataDir = System.getProperty("jboss.server.data.dir");
        System.setProperty("jboss.server.data.dir", tempDir.toString());
        System.setProperty("orca.base-url", "https://weborca.example.orca");
        System.setProperty("orca.api.user", "external-user");
        System.setProperty("orca.api.password", "external-password");
        System.setProperty("orca.mode", "weborca");

        OrcaConnectionConfigStore store = newStore(buildProtector());

        OrcaConnectionConfigRecord snapshot = store.getSnapshot();
        assertNotNull(snapshot);
        assertEquals("https://weborca.example.orca", snapshot.getServerUrl());
        assertEquals("external-user", snapshot.getUsername());
        assertEquals(443, snapshot.getPort());

        OrcaConnectionConfigStore.ResolvedOrcaConnection resolved = store.resolve();
        assertEquals("https://weborca.example.orca", resolved.baseUrl());
        assertEquals("external-user", resolved.username());
        assertEquals("external-password", resolved.password());
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
