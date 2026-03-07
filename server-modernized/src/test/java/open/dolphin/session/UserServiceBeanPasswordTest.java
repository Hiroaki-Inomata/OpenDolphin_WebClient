package open.dolphin.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.Date;
import open.dolphin.infomodel.UserModel;
import open.dolphin.security.auth.LoginAttemptPolicyService;
import open.dolphin.security.auth.PasswordHashService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserServiceBeanPasswordTest {

    @Mock
    private EntityManager entityManager;

    @Mock
    private Query query;

    @Mock
    private LoginAttemptPolicyService loginAttemptPolicyService;

    private UserServiceBean userServiceBean;
    private PasswordHashService passwordHashService;

    @BeforeEach
    void setUp() throws Exception {
        userServiceBean = new UserServiceBean();
        passwordHashService = new PasswordHashService();
        setField(userServiceBean, "em", entityManager);
        setField(userServiceBean, "passwordHashService", passwordHashService);
        setField(userServiceBean, "loginAttemptPolicyService", loginAttemptPolicyService);

        lenient().when(entityManager.createQuery(anyString())).thenReturn(query);
        lenient().when(query.setParameter(anyString(), any())).thenReturn(query);
        lenient().when(loginAttemptPolicyService.preCheck(anyString(), any(), any()))
                .thenReturn(LoginAttemptPolicyService.PreCheckResult.allowed());
        lenient().when(loginAttemptPolicyService.registerFailure(anyString(), any(), any()))
                .thenReturn(new LoginAttemptPolicyService.FailureResult(false, false, 0L));
    }

    @Test
    void authenticateSucceedsWithCurrentPasswordHash() {
        UserModel user = new UserModel();
        user.setUserId("F001:user01");
        user.setPassword(passwordHashService.hashForStorage("RawPass123"));
        when(query.getSingleResult()).thenReturn(user);

        boolean authenticated = userServiceBean.authenticate("F001:user01", "RawPass123");

        assertThat(authenticated).isTrue();
        verify(entityManager, never()).merge(user);
        verify(loginAttemptPolicyService).registerSuccess(eq("F001:user01"), any());
    }

    @Test
    void authenticateRejectsLegacyMd5DigestWithoutUpgrade() {
        UserModel user = new UserModel();
        user.setUserId("F001:user01");
        user.setPassword("21232f297a57a5a743894a0e4a801fc3");
        when(query.getSingleResult()).thenReturn(user);

        boolean authenticated = userServiceBean.authenticate("F001:user01", "admin");

        assertThat(authenticated).isFalse();
        assertThat(user.getPassword()).isEqualTo("21232f297a57a5a743894a0e4a801fc3");
        verify(entityManager, never()).merge(user);
    }

    @Test
    void authenticateRejectsLegacyPlainPasswordWithoutUpgrade() {
        UserModel user = new UserModel();
        user.setUserId("F001:user01");
        user.setPassword("plain-password");
        when(query.getSingleResult()).thenReturn(user);

        boolean authenticated = userServiceBean.authenticate("F001:user01", "plain-password");

        assertThat(authenticated).isFalse();
        assertThat(user.getPassword()).isEqualTo("plain-password");
        verify(entityManager, never()).merge(user);
    }

    @Test
    void authenticateRejectsLegacyManagedHashWithoutUpgrade() {
        UserModel user = new UserModel();
        user.setUserId("F001:user01");
        user.setPassword("pbkdf2_md5$200000$c2FsdA==$aGFzaA==");
        when(query.getSingleResult()).thenReturn(user);

        boolean authenticated = userServiceBean.authenticate("F001:user01", "RawPass123");

        assertThat(authenticated).isFalse();
        assertThat(user.getPassword()).isEqualTo("pbkdf2_md5$200000$c2FsdA==$aGFzaA==");
        verify(entityManager, never()).merge(user);
    }

    @Test
    void updateUserHashesPlainPasswordBeforeMerge() {
        UserModel current = new UserModel();
        current.setMemberType("FACILITY_USER");
        current.setRegisteredDate(new Date());
        current.setPassword("pbkdf2_sha256_v1$310000$currentSalt$currentHash");
        when(entityManager.find(UserModel.class, 10L)).thenReturn(current);

        UserModel update = new UserModel();
        update.setId(10L);
        update.setPassword("NewPlainPassword123!");

        int result = userServiceBean.updateUser(update);

        assertThat(result).isEqualTo(1);
        assertThat(update.getPassword()).startsWith(PasswordHashService.FORMAT_PREFIX + "$");
        verify(entityManager).merge(update);
    }

    @Test
    void updateUserRejectsLegacyMd5HashString() {
        UserModel current = new UserModel();
        current.setMemberType("FACILITY_USER");
        current.setRegisteredDate(new Date());
        current.setPassword(passwordHashService.hashForStorage("CurrentPassword123!"));
        when(entityManager.find(UserModel.class, 10L)).thenReturn(current);

        UserModel update = new UserModel();
        update.setId(10L);
        update.setPassword("21232f297a57a5a743894a0e4a801fc3");

        assertThatThrownBy(() -> userServiceBean.updateUser(update))
                .isInstanceOf(IllegalArgumentException.class);
        verify(entityManager, never()).merge(update);
    }

    @Test
    void updateUserRejectsLegacyManagedHashString() {
        UserModel current = new UserModel();
        current.setMemberType("FACILITY_USER");
        current.setRegisteredDate(new Date());
        current.setPassword(passwordHashService.hashForStorage("CurrentPassword123!"));
        when(entityManager.find(UserModel.class, 10L)).thenReturn(current);

        UserModel update = new UserModel();
        update.setId(10L);
        update.setPassword("pbkdf2_md5$200000$c2FsdA==$aGFzaA==");

        assertThatThrownBy(() -> userServiceBean.updateUser(update))
                .isInstanceOf(IllegalArgumentException.class);
        verify(entityManager, never()).merge(update);
    }

    @Test
    void authenticateReturnsFalseWhenAccountIsLocked() {
        when(loginAttemptPolicyService.preCheck(eq("F001:user01"), eq("192.0.2.40"), any()))
                .thenReturn(LoginAttemptPolicyService.PreCheckResult.locked());

        UserServiceBean.AuthenticationResult result =
                userServiceBean.authenticateWithPolicy("F001:user01", "RawPass123", "192.0.2.40");

        assertThat(result.authenticated()).isFalse();
        assertThat(result.ipThrottled()).isFalse();
        assertThat(result.secondFactorRequired()).isFalse();
        verify(loginAttemptPolicyService).registerFailure(eq("F001:user01"), eq("192.0.2.40"), any());
    }

    @Test
    void authenticateReturnsIpThrottleWhenFailureTriggersIpLimit() {
        UserModel user = new UserModel();
        user.setUserId("F001:user01");
        user.setPassword(passwordHashService.hashForStorage("RawPass123"));
        when(query.getSingleResult()).thenReturn(user);
        when(loginAttemptPolicyService.registerFailure(eq("F001:user01"), eq("192.0.2.50"), any()))
                .thenReturn(new LoginAttemptPolicyService.FailureResult(false, true, 120L));

        UserServiceBean.AuthenticationResult result =
                userServiceBean.authenticateWithPolicy("F001:user01", "WrongPass!", "192.0.2.50");

        assertThat(result.authenticated()).isFalse();
        assertThat(result.ipThrottled()).isTrue();
        assertThat(result.retryAfterSeconds()).isEqualTo(120L);
    }

    @Test
    void authenticateReturnsSecondFactorRequiredWhenFactor2Enabled() {
        UserModel user = new UserModel();
        user.setUserId("F001:user01");
        user.setPassword(passwordHashService.hashForStorage("RawPass123"));
        user.setFactor2Auth("totp");
        when(query.getSingleResult()).thenReturn(user);

        UserServiceBean.AuthenticationResult result =
                userServiceBean.authenticateWithPolicy("F001:user01", "RawPass123", "192.0.2.60");

        assertThat(result.authenticated()).isFalse();
        assertThat(result.secondFactorRequired()).isTrue();
        assertThat(result.ipThrottled()).isFalse();
        verify(loginAttemptPolicyService).registerSuccess(eq("F001:user01"), any());
    }

    @Test
    void systemAdminAliasesAreRecognizedButAdminIsExcluded() {
        when(query.getSingleResult()).thenReturn(userWithRoles("system_admin"));
        assertThat(userServiceBean.isSystemAdmin("F001:user01")).isTrue();

        when(query.getSingleResult()).thenReturn(userWithRoles("system-admin"));
        assertThat(userServiceBean.isSystemAdmin("F001:user01")).isTrue();

        when(query.getSingleResult()).thenReturn(userWithRoles("system-administrator"));
        assertThat(userServiceBean.isSystemAdmin("F001:user01")).isTrue();

        when(query.getSingleResult()).thenReturn(userWithRoles("system_administrator"));
        assertThat(userServiceBean.isSystemAdmin("F001:user01")).isTrue();

        when(query.getSingleResult()).thenReturn(userWithRoles("admin"));
        assertThat(userServiceBean.isSystemAdmin("F001:user01")).isFalse();
    }

    private static UserModel userWithRoles(String roleName) {
        UserModel user = new UserModel();
        open.dolphin.infomodel.RoleModel role = new open.dolphin.infomodel.RoleModel();
        role.setRole(roleName);
        user.setRoles(java.util.List.of(role));
        return user;
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        var field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
