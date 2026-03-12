package open.dolphin.msg.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import open.dolphin.runtime.RuntimeConfigurationSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class SmsGatewayConfigTest {

    @AfterEach
    void tearDown() {
        System.clearProperty(RuntimeConfigurationSupport.PROP_CUSTOM_PROPERTIES_PATH);
        System.clearProperty("plivo.auth.id");
        System.clearProperty("plivo.auth.token");
        System.clearProperty("plivo.source.number");
        System.clearProperty("plivo.baseUrl");
    }

    @Test
    void reloadReadsSystemPropertiesWhenLegacyIsMissing() {
        System.setProperty("plivo.auth.id", "prop-auth-id");
        System.setProperty("plivo.auth.token", "prop-auth-token");
        System.setProperty("plivo.source.number", "+819000000000");

        SmsGatewayConfig.PlivoSettings settings = new SmsGatewayConfig().reload();

        assertEquals("prop-auth-id", settings.authId());
        assertEquals("prop-auth-token", settings.authToken());
        assertEquals("+819000000000", settings.sourceNumber());
    }

    @Test
    void reloadFallsBackToLegacyCustomProperties() throws Exception {
        Path temp = Files.createTempFile("sms-gateway", ".properties");
        Files.writeString(temp,
                "plivo.auth.id=legacy-auth-id\n"
                        + "plivo.auth.token=legacy-auth-token\n"
                        + "plivo.source.number=09012345678\n"
                        + "plivo.baseUrl=https://api.sandbox.plivo.com/v1/\n");
        System.setProperty(RuntimeConfigurationSupport.PROP_CUSTOM_PROPERTIES_PATH, temp.toString());

        SmsGatewayConfig.PlivoSettings settings = new SmsGatewayConfig().reload();

        assertEquals("legacy-auth-id", settings.authId());
        assertEquals("legacy-auth-token", settings.authToken());
        assertEquals("09012345678", settings.sourceNumber());
        assertEquals("https://api.sandbox.plivo.com/v1/", settings.baseUrl());
    }
}
