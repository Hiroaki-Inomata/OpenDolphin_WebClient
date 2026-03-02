package open.dolphin.touch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.ws.rs.WebApplicationException;
import open.dolphin.rest.config.DemoApiSettings;
import open.dolphin.testsupport.RuntimeDelegateTestSupport;
import org.junit.jupiter.api.Test;

class DemoResourceProdGuardTest extends RuntimeDelegateTestSupport {

    @Test
    void demoResourceReturns404WhenDemoDisabled() {
        DemoResource resource = new DemoResource(disabledSettings());

        assertThatThrownBy(() -> resource.getUser("demo,2.100,deadbeef"))
                .isInstanceOf(WebApplicationException.class)
                .satisfies(ex -> assertThat(((WebApplicationException) ex).getResponse().getStatus()).isEqualTo(404));
    }

    @Test
    void demoResourceAspReturns404WhenDemoDisabled() {
        DemoResourceASP resource = new DemoResourceASP(disabledSettings());

        assertThatThrownBy(() -> resource.getUser("demo,2.100,deadbeef"))
                .isInstanceOf(WebApplicationException.class)
                .satisfies(ex -> assertThat(((WebApplicationException) ex).getResponse().getStatus()).isEqualTo(404));
    }

    private static DemoApiSettings disabledSettings() {
        return new DemoApiSettings(
                false,
                "2.100",
                "EHR Clinic",
                "2.100",
                "EHR Clinic",
                "demo",
                "demo",
                "deadbeef",
                "touchTester",
                "2.100",
                "00001");
    }
}
