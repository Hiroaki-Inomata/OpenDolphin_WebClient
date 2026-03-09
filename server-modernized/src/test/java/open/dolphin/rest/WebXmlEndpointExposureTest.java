package open.dolphin.rest;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class WebXmlEndpointExposureTest {

    @Test
    void webXmlDoesNotExposeRemovedLegacyResources() throws IOException {
        String webXml = Files.readString(Path.of("src/main/webapp/WEB-INF/web.xml"));
        assertThat(webXml)
                .doesNotContain("open.dolphin.touch.DolphinResourceASP")
                .doesNotContain("open.dolphin.rest.PatientResource")
                .doesNotContain("open.dolphin.rest.NLabResource")
                .doesNotContain("open.dolphin.rest.ReportingResource")
                .doesNotContain("open.dolphin.rest.ChartEventResource")
                .doesNotContain("open.dolphin.rest.PVTResource2")
                .doesNotContain("open.dolphin.rest.ScheduleResource")
                .doesNotContain("open.dolphin.rest.ServerInfoResource")
                .doesNotContain("open.orca.rest.OrcaMasterApiAliasResource");
    }
}
