package open.dolphin.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.lang.reflect.Field;
import java.util.List;
import open.dolphin.infomodel.FacilityModel;
import open.dolphin.infomodel.RoleModel;
import open.dolphin.infomodel.UserModel;
import open.dolphin.session.UserServiceBean;
import open.dolphin.testsupport.RuntimeDelegateTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SessionAuthResourceTest extends RuntimeDelegateTestSupport {

    private static final String USER_ID = "F001:user01";

    private SessionAuthResource resource;
    private UserServiceBean userServiceBean;

    @BeforeEach
    void setUp() throws Exception {
        resource = new SessionAuthResource();
        userServiceBean = mock(UserServiceBean.class);
        setField(resource, "userServiceBean", userServiceBean);
    }

    @Test
    void loginEstablishesSessionAndReturnsSafeResponse() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpSession session = mock(HttpSession.class);
        when(request.getSession(true)).thenReturn(session);
        when(request.getSession(false)).thenReturn(session);
        when(request.getHeader("X-Run-Id")).thenReturn("run-123");
        when(request.getRemoteAddr()).thenReturn("192.0.2.10");
        when(request.getRequestURI()).thenReturn("/openDolphin/resources/api/session/login");
        when(userServiceBean.authenticateWithPolicy(USER_ID, "plain-password", "192.0.2.10"))
                .thenReturn(UserServiceBean.AuthenticationResult.success());
        when(userServiceBean.getUser(USER_ID)).thenReturn(userWithRole(USER_ID, "system_admin"));

        Response response = resource.login(request,
                new SessionAuthResource.LoginRequest("F001", "user01", "plain-password", "client-1"));

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeaderString("Cache-Control")).isEqualTo("private, no-store, max-age=0, must-revalidate");
        AuthSessionSupport.SessionUserResponse entity = (AuthSessionSupport.SessionUserResponse) response.getEntity();
        assertThat(entity.facilityId()).isEqualTo("F001");
        assertThat(entity.userId()).isEqualTo(USER_ID);
        assertThat(entity.userPk()).isEqualTo(101L);
        assertThat(entity.clientUuid()).isEqualTo("client-1");
        assertThat(entity.runId()).isEqualTo("run-123");
        assertThat(entity.roles()).containsExactly("system_admin");
        verify(session).setAttribute(AuthSessionSupport.AUTH_ACTOR_ID, USER_ID);
        verify(session).setAttribute(AuthSessionSupport.AUTH_FACILITY_ID, "F001");
        verify(session).setAttribute(AuthSessionSupport.AUTH_LOGIN_ID, "user01");
    }

    @Test
    void loginReturns429WhenIpThrottled() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("192.0.2.20");
        when(request.getRequestURI()).thenReturn("/openDolphin/resources/api/session/login");
        when(userServiceBean.authenticateWithPolicy(USER_ID, "plain-password", "192.0.2.20"))
                .thenReturn(UserServiceBean.AuthenticationResult.ipThrottled(90));

        Response response = resource.login(request,
                new SessionAuthResource.LoginRequest("F001", "user01", "plain-password", null));

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeaderString("Retry-After")).isEqualTo("90");
    }

    @Test
    void meRequiresAuthenticatedSession() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getSession(false)).thenReturn(null);

        assertThatThrownBy(() -> resource.me(request))
                .isInstanceOf(WebApplicationException.class)
                .satisfies(ex -> assertThat(((WebApplicationException) ex).getResponse().getStatus()).isEqualTo(401));
    }

    @Test
    void meReloadsCurrentUserStateFromDatabase() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpSession session = mock(HttpSession.class);
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute(AuthSessionSupport.AUTH_ACTOR_ID)).thenReturn(USER_ID);
        when(session.getAttribute(AuthSessionSupport.AUTH_CLIENT_UUID)).thenReturn("client-2");
        when(userServiceBean.getUser(USER_ID)).thenReturn(userWithRole(USER_ID, "user"));

        Response response = resource.me(request);

        assertThat(response.getStatus()).isEqualTo(200);
        AuthSessionSupport.SessionUserResponse entity = (AuthSessionSupport.SessionUserResponse) response.getEntity();
        assertThat(entity.userPk()).isEqualTo(101L);
        assertThat(entity.roles()).containsExactly("user");
        assertThat(entity.clientUuid()).isEqualTo("client-2");
    }

    private static UserModel userWithRole(String userId, String roleValue) {
        RoleModel role = new RoleModel();
        role.setRole(roleValue);
        FacilityModel facility = new FacilityModel();
        facility.setFacilityId("F001");
        UserModel user = new UserModel();
        user.setId(101L);
        user.setUserId(userId);
        user.setCommonName("Doctor One");
        user.setFacilityModel(facility);
        user.setRoles(List.of(role));
        return user;
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
