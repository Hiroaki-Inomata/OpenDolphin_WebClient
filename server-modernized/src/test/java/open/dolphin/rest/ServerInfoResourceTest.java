package open.dolphin.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.WebApplicationException;
import java.nio.file.Files;
import java.nio.file.Path;
import open.dolphin.session.UserServiceBean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ServerInfoResourceTest {

    @TempDir
    Path tempDir;

    @Mock
    private UserServiceBean userServiceBean;

    @Mock
    private HttpServletRequest request;

    private ServerInfoResource resource;
    private String originalJbossHome;

    @BeforeEach
    void setUp() throws Exception {
        originalJbossHome = System.getProperty("jboss.home.dir");
        Files.writeString(tempDir.resolve("custom.properties"), """
                jamri.code=JAMRI-001
                cloud.zero=true
                server.version=secret
                """);
        System.setProperty("jboss.home.dir", tempDir.toString());

        resource = new ServerInfoResource();
        resource.userServiceBean = userServiceBean;
    }

    @AfterEach
    void tearDown() {
        if (originalJbossHome == null) {
            System.clearProperty("jboss.home.dir");
        } else {
            System.setProperty("jboss.home.dir", originalJbossHome);
        }
    }

    @Test
    void getJamri_requiresAdmin() {
        when(request.getRemoteUser()).thenReturn("F001:user01");
        when(userServiceBean.isAdmin("F001:user01")).thenReturn(false);

        assertThatThrownBy(() -> resource.getJamri(request))
                .isInstanceOf(WebApplicationException.class)
                .extracting(ex -> ((WebApplicationException) ex).getResponse().getStatus())
                .isEqualTo(404);
    }

    @Test
    void getJamri_returnsAllowlistedValueForAdmin() {
        when(request.getRemoteUser()).thenReturn("F001:admin");
        when(userServiceBean.isAdmin("F001:admin")).thenReturn(true);

        assertThat(resource.getJamri(request)).isEqualTo("JAMRI-001");
    }

    @Test
    void getProperty_returnsEmptyForNonAllowlistedKey() {
        assertThat(resource.getProperty("server.version")).isEmpty();
    }
}
