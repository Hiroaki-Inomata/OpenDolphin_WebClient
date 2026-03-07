package open.dolphin.mbean;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import open.dolphin.runtime.RuntimeConfigurationSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ServletStartupSecurityGuardTest {

    @AfterEach
    void tearDown() {
        System.clearProperty(RuntimeConfigurationSupport.PROP_ENVIRONMENT);
        System.clearProperty(ServletStartup.ORCA_MASTER_BASIC_USER_KEY);
        System.clearProperty(ServletStartup.ORCA_MASTER_BASIC_PASSWORD_KEY);
    }

    @Test
    void productionLikeEnvironmentRejectsLegacyOrcaMasterCredential() {
        System.setProperty(RuntimeConfigurationSupport.PROP_ENVIRONMENT, "production");
        System.setProperty(ServletStartup.ORCA_MASTER_BASIC_PASSWORD_KEY, "legacy-secret");

        IllegalStateException ex = assertThrows(IllegalStateException.class, ServletStartup::enforceStartupSecurityGuards);

        assertTrue(ex.getMessage().contains(ServletStartup.ORCA_MASTER_BASIC_PASSWORD_KEY));
    }

    @Test
    void nonProductionEnvironmentSkipsGuards() {
        System.setProperty(RuntimeConfigurationSupport.PROP_ENVIRONMENT, "local");
        System.setProperty(ServletStartup.ORCA_MASTER_BASIC_PASSWORD_KEY, "legacy-secret");

        assertDoesNotThrow(ServletStartup::enforceStartupSecurityGuards);
    }
}
