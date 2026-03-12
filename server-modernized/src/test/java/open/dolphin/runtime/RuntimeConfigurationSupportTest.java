package open.dolphin.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class RuntimeConfigurationSupportTest {

    @AfterEach
    void tearDown() {
        System.clearProperty("test.unified.setting");
        System.clearProperty(RuntimeConfigurationSupport.PROP_CUSTOM_PROPERTIES_PATH);
    }

    @Test
    void resolveUnifiedSettingPrefersSystemPropertyOverJsonYamlLegacy() {
        System.setProperty("test.unified.setting", "system-value");
        Properties legacy = new Properties();
        legacy.setProperty("legacy.key", "legacy-value");

        String resolved = RuntimeConfigurationSupport.resolveUnifiedSetting(
                        null,
                        "test.unified.setting",
                        () -> "json-value",
                        () -> "yaml-value",
                        legacy,
                        "legacy.key")
                .orElseThrow();

        assertEquals("system-value", resolved);
    }

    @Test
    void loadLegacyCustomPropertiesUsesExplicitPath() throws Exception {
        Path temp = Files.createTempFile("runtime-config", ".properties");
        Files.writeString(temp, "alpha=one\n");
        System.setProperty(RuntimeConfigurationSupport.PROP_CUSTOM_PROPERTIES_PATH, temp.toString());

        Properties loaded = RuntimeConfigurationSupport.loadLegacyCustomProperties();

        assertEquals("one", loaded.getProperty("alpha"));
        assertTrue(loaded.containsKey("alpha"));
    }
}
