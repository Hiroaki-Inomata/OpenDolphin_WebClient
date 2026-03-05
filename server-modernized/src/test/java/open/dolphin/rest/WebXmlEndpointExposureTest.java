package open.dolphin.rest;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class WebXmlEndpointExposureTest {

    @Test
    void webXmlDoesNotExposeDolphinResourceAsp() throws IOException {
        String webXml = Files.readString(Path.of("src/main/webapp/WEB-INF/web.xml"));
        assertThat(webXml).doesNotContain("open.dolphin.touch.DolphinResourceASP");
    }
}

