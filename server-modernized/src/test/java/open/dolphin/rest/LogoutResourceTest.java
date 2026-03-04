package open.dolphin.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LogoutResourceTest {

    private LogoutResource resource;

    @BeforeEach
    void setUp() {
        resource = new LogoutResource();
    }

    @Test
    void logoutInvalidatesSessionAndExpiresCookie() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpSession session = mock(HttpSession.class);
        when(request.getSession(false)).thenReturn(session);
        when(request.getContextPath()).thenReturn("/openDolphin");
        when(request.isSecure()).thenReturn(true);

        Response response = resource.logout(request);

        verify(session).invalidate();
        assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
        NewCookie cookie = response.getCookies().get("JSESSIONID");
        assertThat(cookie).isNotNull();
        assertThat(cookie.getMaxAge()).isZero();
        assertThat(cookie.getPath()).isEqualTo("/openDolphin");
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.isSecure()).isTrue();
    }

    @Test
    void logoutIsIdempotentWithoutSession() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getSession(false)).thenReturn(null);
        when(request.getContextPath()).thenReturn("/openDolphin");
        when(request.isSecure()).thenReturn(false);

        Response response = resource.logout(request);

        assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
        NewCookie cookie = response.getCookies().get("JSESSIONID");
        assertThat(cookie).isNotNull();
        assertThat(cookie.getMaxAge()).isZero();
        assertThat(cookie.getPath()).isEqualTo("/openDolphin");
        assertThat(cookie.isSecure()).isFalse();
    }
}
