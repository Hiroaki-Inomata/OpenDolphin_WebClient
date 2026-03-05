package open.dolphin.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.WebApplicationException;
import java.util.List;
import open.dolphin.infomodel.RoleModel;
import open.dolphin.infomodel.UserModel;
import open.dolphin.session.UserServiceBean;
import open.dolphin.touch.JsonTouchSharedService;
import open.dolphin.testsupport.RuntimeDelegateTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserResourceTest extends RuntimeDelegateTestSupport {

    private static final String USER_01 = "F001:user01";
    private static final String USER_02 = "F001:user02";
    private static final String ADMIN = "F001:admin";

    @Mock
    private UserServiceBean userServiceBean;

    @Mock
    private HttpServletRequest request;

    private UserResource resource;

    @BeforeEach
    void setUp() throws Exception {
        resource = new UserResource();
        setField(resource, "userServiceBean", userServiceBean);
    }

    @Test
    void getUserReturnsSafeDtoWithoutPassword() throws Exception {
        when(request.getRemoteUser()).thenReturn(USER_01);
        when(userServiceBean.getUser(USER_01)).thenReturn(userWithRole(USER_01, "user"));
        JsonTouchSharedService.SafeUserResponse response = resource.getUser(request, USER_01);
        String json = new ObjectMapper().writeValueAsString(response);
        assertThat(json)
                .doesNotContain("password")
                .doesNotContain("temporaryPassword")
                .doesNotContain("credential")
                .doesNotContain("hash")
                .doesNotContain("salt");
    }

    @Test
    void getUserReturns404WhenRequestingOtherUserWithoutAdmin() {
        when(request.getRemoteUser()).thenReturn(USER_01);
        when(userServiceBean.getUser(USER_02)).thenReturn(userWithRole(USER_02, "user"));
        when(userServiceBean.isAdmin(USER_01)).thenReturn(false);

        assertThatThrownBy(() -> resource.getUser(request, USER_02))
                .isInstanceOf(WebApplicationException.class)
                .satisfies(ex -> assertThat(((WebApplicationException) ex).getResponse().getStatus()).isEqualTo(404));
    }

    @Test
    void nonAdminCannotUpdateOtherUser() throws Exception {
        when(request.getRemoteUser()).thenReturn(USER_01);
        when(userServiceBean.isAdmin(USER_01)).thenReturn(false);

        String payload = """
                {
                  "id":2,
                  "userId":"F001:user02",
                  "commonName":"Other User",
                  "roles":[{"role":"user"}]
                }
                """;

        assertThatThrownBy(() -> resource.putUser(request, payload))
                .isInstanceOf(WebApplicationException.class)
                .satisfies(ex -> assertThat(((WebApplicationException) ex).getResponse().getStatus()).isEqualTo(403));

        verify(userServiceBean, never()).updateUser(any());
    }

    @Test
    void nonAdminCanUpdateOwnProfileWhenRolesUnchanged() throws Exception {
        when(request.getRemoteUser()).thenReturn(USER_01);
        when(userServiceBean.isAdmin(USER_01)).thenReturn(false);

        UserModel current = new UserModel();
        current.setUserId(USER_01);
        current.setRoles(List.of(role("user")));
        when(userServiceBean.getUser(USER_01)).thenReturn(current);
        when(userServiceBean.updateUser(any(UserModel.class))).thenReturn(1);

        String payload = """
                {
                  "id":1,
                  "userId":"F001:user01",
                  "commonName":"Self Update",
                  "roles":[{"role":"user"}]
                }
                """;

        String result = resource.putUser(request, payload);

        assertThat(result).isEqualTo("1");
        verify(userServiceBean).updateUser(any(UserModel.class));
    }

    @Test
    void nonAdminCannotChangeRoles() throws Exception {
        when(request.getRemoteUser()).thenReturn(USER_01);
        when(userServiceBean.isAdmin(USER_01)).thenReturn(false);

        UserModel current = new UserModel();
        current.setUserId(USER_01);
        current.setRoles(List.of(role("user")));
        when(userServiceBean.getUser(USER_01)).thenReturn(current);

        String payload = """
                {
                  "id":1,
                  "userId":"F001:user01",
                  "commonName":"Self Update",
                  "roles":[{"role":"admin"}]
                }
                """;

        assertThatThrownBy(() -> resource.putUser(request, payload))
                .isInstanceOf(WebApplicationException.class)
                .satisfies(ex -> assertThat(((WebApplicationException) ex).getResponse().getStatus()).isEqualTo(403));
        verify(userServiceBean, never()).updateUser(any());
    }

    @Test
    void nonAdminDeleteIsForbidden() {
        when(request.getRemoteUser()).thenReturn(USER_01);
        when(userServiceBean.isAdmin(USER_01)).thenReturn(false);

        assertThatThrownBy(() -> resource.deleteUser(request, USER_02))
                .isInstanceOf(WebApplicationException.class)
                .satisfies(ex -> assertThat(((WebApplicationException) ex).getResponse().getStatus()).isEqualTo(403));
        verify(userServiceBean, never()).removeUser(any());
    }

    @Test
    void adminCanUpdateAndDelete() throws Exception {
        when(request.getRemoteUser()).thenReturn(ADMIN);
        when(userServiceBean.isAdmin(ADMIN)).thenReturn(true);
        when(userServiceBean.updateUser(any(UserModel.class))).thenReturn(1);

        String payload = """
                {
                  "id":2,
                  "userId":"F001:user02",
                  "commonName":"Managed User",
                  "roles":[{"role":"admin"}]
                }
                """;

        String result = resource.putUser(request, payload);
        assertThat(result).isEqualTo("1");

        resource.deleteUser(request, USER_02);

        verify(userServiceBean).updateUser(any(UserModel.class));
        verify(userServiceBean).removeUser(USER_02);
    }

    private static RoleModel role(String value) {
        RoleModel role = new RoleModel();
        role.setRole(value);
        return role;
    }

    private static UserModel userWithRole(String userId, String roleValue) {
        UserModel user = new UserModel();
        user.setUserId(userId);
        user.setPassword("secret");
        user.setRoles(List.of(role(roleValue)));
        return user;
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        var field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
