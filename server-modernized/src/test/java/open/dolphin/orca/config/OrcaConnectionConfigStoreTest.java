package open.dolphin.orca.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import open.dolphin.orca.transport.OrcaConnectionPolicyException;
import open.dolphin.orca.transport.OrcaTransportSecurityPolicy;
import open.dolphin.runtime.RuntimeConfigurationSupport;
import open.dolphin.runtime.RuntimeStateRepository;
import open.dolphin.security.SecondFactorSecurityConfig;
import open.dolphin.security.totp.TotpSecretProtector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class OrcaConnectionConfigStoreTest {

    private static final String STATE_CATEGORY = "orca_connection_config";
    private static final String STATE_KEY = "default";

    @AfterEach
    void tearDown() {
        System.clearProperty(OrcaTransportSecurityPolicy.PROP_ALLOW_INSECURE_HTTP);
        System.clearProperty(RuntimeConfigurationSupport.PROP_ENVIRONMENT);
    }

    @Test
    void updatePersistsEncryptedConfigAndReloadsOnRestart() throws Exception {
        TotpSecretProtector protector = buildProtector();
        Map<String, String> db = new LinkedHashMap<>();
        OrcaConnectionConfigStore store = newStore(protector, db);

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

        String rawJson = db.get(STATE_CATEGORY + ":" + STATE_KEY);
        assertNotNull(rawJson);
        assertTrue(rawJson.contains("\"serverUrl\":\"https://weborca-trial.orca.med.or.jp\""));
        assertTrue(rawJson.contains("\"username\":\"trial\""));
        assertTrue(rawJson.contains("passwordEncrypted"));
        assertTrue(!rawJson.contains("\"passwordEncrypted\":\"weborcatrial\""));

        OrcaConnectionConfigStore reloaded = newStore(protector, db);
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
        TotpSecretProtector protector = buildProtector();
        Map<String, String> db = new LinkedHashMap<>();
        OrcaConnectionConfigStore store = newStore(protector, db);

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

        String rawJson = db.get(STATE_CATEGORY + ":" + STATE_KEY);
        assertNotNull(rawJson);
        assertTrue(rawJson.contains("\"records\""));
        assertTrue(rawJson.contains("\"_default\""));
        assertTrue(rawJson.contains("\"F001\""));

        OrcaConnectionConfigStore.ResolvedOrcaConnection resolvedDefault = store.resolve();
        assertEquals("new-user", resolvedDefault.username());
        assertEquals("new-pass", resolvedDefault.password());
    }

    @Test
    void updateRejectsInsecureHttpInProduction() throws Exception {
        System.setProperty(RuntimeConfigurationSupport.PROP_ENVIRONMENT, "production");
        System.clearProperty(OrcaTransportSecurityPolicy.PROP_ALLOW_INSECURE_HTTP);

        TotpSecretProtector protector = buildProtector();
        Map<String, String> db = new LinkedHashMap<>();
        OrcaConnectionConfigStore store = newStore(protector, db);

        OrcaConnectionConfigStore.UpdateRequest update = new OrcaConnectionConfigStore.UpdateRequest(
                Boolean.TRUE,
                "http://weborca.example.test",
                80,
                "trial",
                "weborcatrial",
                Boolean.FALSE,
                null
        );

        OrcaConnectionPolicyException ex = assertThrows(
                OrcaConnectionPolicyException.class,
                () -> store.update(update, null, null, "RUN-TEST", "FACILITY:admin")
        );

        assertEquals("weborca_requires_https", ex.getErrorCategory());
    }

    @Test
    void initRejectsLegacySingleRecordConfig() throws Exception {
        TotpSecretProtector protector = buildProtector();
        Map<String, String> db = new LinkedHashMap<>();
        db.put(STATE_CATEGORY + ":" + STATE_KEY, """
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
                () -> newStore(protector, db)
        );

        assertEquals(
                "Legacy single-record ORCA connection config is no longer supported. Migrate to the records format.",
                ex.getMessage()
        );
    }

    private OrcaConnectionConfigStore newStore(TotpSecretProtector protector, Map<String, String> db) throws Exception {
        OrcaConnectionConfigStore store = new OrcaConnectionConfigStore();

        RuntimeStateRepository repository = mock(RuntimeStateRepository.class);
        when(repository.findPayload(eq(STATE_CATEGORY), eq(STATE_KEY)))
                .thenAnswer(invocation -> Optional.ofNullable(db.get(STATE_CATEGORY + ":" + STATE_KEY)));
        doAnswer(invocation -> {
            String key = invocation.getArgument(0, String.class) + ":" + invocation.getArgument(1, String.class);
            String payload = invocation.getArgument(2, String.class);
            db.put(key, payload);
            return null;
        }).when(repository).upsertPayload(any(String.class), any(String.class), any(String.class), any(Instant.class));

        SecondFactorSecurityConfig secondFactorSecurityConfig = mock(SecondFactorSecurityConfig.class);
        when(secondFactorSecurityConfig.getTotpSecretProtector()).thenReturn(protector);

        setField(store, "secondFactorSecurityConfig", secondFactorSecurityConfig);
        setField(store, "stateRepository", repository);
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
