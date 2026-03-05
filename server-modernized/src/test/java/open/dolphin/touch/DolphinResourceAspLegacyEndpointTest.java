package open.dolphin.touch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.WebApplicationException;
import org.junit.jupiter.api.Test;

class DolphinResourceAspLegacyEndpointTest {

    @Test
    void legacyTouchUserEndpointReturnsGone() {
        DolphinResourceASP resource = new DolphinResourceASP();
        HttpServletRequest request = mock(HttpServletRequest.class);
        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> resource.getUser(request, "user,facility,password"));
        assertEquals(410, ex.getResponse().getStatus());
    }
}

