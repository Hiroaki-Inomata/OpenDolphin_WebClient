package open.dolphin.rest.orca;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class OrcaPostFeatureFlagsTest {

    @AfterEach
    void tearDown() {
        System.clearProperty("orca.post.subjectives.mode");
        System.clearProperty("orca.post.subjectives.useStub");
        System.clearProperty("orca.post.subjectives.real");
        System.clearProperty("orca.post.medical.records.mode");
        System.clearProperty("orca.post.medical.records.useStub");
        System.clearProperty("orca.post.medical.records.real");
        System.clearProperty("orca.post.mode");
    }

    @Test
    void defaultsToRealWhenUnset() {
        assertTrue(OrcaPostFeatureFlags.useRealSubjectives(),
                "No env/props -> REAL by default");
    }

    @Test
    void useStubFlagDisablesReal() {
        System.setProperty("orca.post.subjectives.useStub", "true");

        assertFalse(OrcaPostFeatureFlags.useRealSubjectives(),
                "useStub=true should force stub");
    }

    @Test
    void modeStubOverridesDefault() {
        System.setProperty("orca.post.subjectives.mode", "stub");

        assertFalse(OrcaPostFeatureFlags.useRealSubjectives(),
                "mode=stub should force stub");
    }

    @Test
    void modeRealKeepsReal() {
        System.setProperty("orca.post.subjectives.mode", "real");

        assertTrue(OrcaPostFeatureFlags.useRealSubjectives(),
                "mode=real should force real even if default changes");
    }

    @Test
    void medicalRecordsDefaultsToRealWhenUnset() {
        assertTrue(OrcaPostFeatureFlags.useRealMedicalRecords(),
                "No env/props -> REAL by default");
    }

    @Test
    void medicalRecordsModeStubOverridesDefault() {
        System.setProperty("orca.post.medical.records.mode", "stub");

        assertFalse(OrcaPostFeatureFlags.useRealMedicalRecords(),
                "mode=stub should force stub");
    }

    @Test
    void medicalRecordsUseStubFlagDisablesReal() {
        System.setProperty("orca.post.medical.records.useStub", "true");

        assertFalse(OrcaPostFeatureFlags.useRealMedicalRecords(),
                "useStub=true should force stub");
    }

    @Test
    void medicalRecordsModeRealKeepsReal() {
        System.setProperty("orca.post.medical.records.mode", "real");

        assertTrue(OrcaPostFeatureFlags.useRealMedicalRecords(),
                "mode=real should force real even if default changes");
    }
}
