package open.dolphin.orca.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import open.dolphin.runtime.RuntimeConfigurationSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class OrcaTransportSettingsSecurityPolicyTest {

    @AfterEach
    void tearDown() {
        System.clearProperty(RuntimeConfigurationSupport.PROP_ENVIRONMENT);
        System.clearProperty(OrcaTransportSecurityPolicy.PROP_ALLOW_INSECURE_HTTP);
    }

    @Test
    void fromAdminConfigRejectsInsecureWeborcaInProduction() {
        System.setProperty(RuntimeConfigurationSupport.PROP_ENVIRONMENT, "production");

        OrcaConnectionPolicyException ex = assertThrows(
                OrcaConnectionPolicyException.class,
                () -> OrcaTransportSettings.fromAdminConfig("http://weborca.example.test", true, "orca", "orca")
        );

        assertEquals("weborca_requires_https", ex.getErrorCategory());
        assertTrue(ex.getMessage().contains("HTTPS"));
    }

    @Test
    void fromAdminConfigRejectsInsecureHttpInProductionWhenFlagDisabled() {
        System.setProperty(RuntimeConfigurationSupport.PROP_ENVIRONMENT, "production");

        OrcaConnectionPolicyException ex = assertThrows(
                OrcaConnectionPolicyException.class,
                () -> OrcaTransportSettings.fromAdminConfig("http://192.168.10.20:8000", false, "orca", "orca")
        );

        assertEquals("insecure_http_disallowed", ex.getErrorCategory());
        assertTrue(ex.getMessage().contains("HTTP"));
    }

    @Test
    void fromAdminConfigAllowsInsecurePrivateHttpOnlyWhenFlagEnabled() {
        System.setProperty(RuntimeConfigurationSupport.PROP_ENVIRONMENT, "production");
        System.setProperty(OrcaTransportSecurityPolicy.PROP_ALLOW_INSECURE_HTTP, "true");

        OrcaTransportSettings settings = OrcaTransportSettings.fromAdminConfig(
                "http://192.168.10.20:8000",
                false,
                "orca",
                "orca"
        );

        assertEquals("http://192.168.10.20:8000", settings.getBaseUrl());
    }

    @Test
    void fromAdminConfigRejectsInsecurePublicHttpEvenWhenFlagEnabled() {
        System.setProperty(RuntimeConfigurationSupport.PROP_ENVIRONMENT, "production");
        System.setProperty(OrcaTransportSecurityPolicy.PROP_ALLOW_INSECURE_HTTP, "true");

        OrcaConnectionPolicyException ex = assertThrows(
                OrcaConnectionPolicyException.class,
                () -> OrcaTransportSettings.fromAdminConfig("http://203.0.113.10:8000", false, "orca", "orca")
        );

        assertEquals("insecure_http_target_not_allowed", ex.getErrorCategory());
        assertTrue(ex.getMessage().contains("private range"));
    }
}
