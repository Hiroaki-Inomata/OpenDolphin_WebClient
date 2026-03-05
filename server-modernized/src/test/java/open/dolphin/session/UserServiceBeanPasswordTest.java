package open.dolphin.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
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
    private PasswordHashService passwordHashService;

    @Mock
    private LoginAttemptPolicyService loginAttemptPolicyService;

    private UserServiceBean userServiceBean;

    @BeforeEach
    void setUp() throws Exception {
        userServiceBean = new UserServiceBean();
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
    void authenticateUpgradesLegacyPasswordOnSuccess() {
        UserModel user = new UserModel();
        user.setUserId("F001:user01");
        user.setPassword("legacy-md5-value");
        when(query.getSingleResult()).thenReturn(user);
        when(passwordHashService.verify("legacy-md5-value", "RawPass123"))
                .thenReturn(PasswordHashService.VerificationResult.successWithUpgrade("pbkdf2_sha256_v1$310000$salt$hash"));

        boolean authenticated = userServiceBean.authenticate("F001:user01", "RawPass123");

        assertThat(authenticated).isTrue();
        assertThat(user.getPassword()).isEqualTo("pbkdf2_sha256_v1$310000$salt$hash");
        verify(entityManager).merge(user);
        verify(loginAttemptPolicyService).registerSuccess(eq("F001:user01"), any());
    }

    @Test
    void updateUserHashesNonManagedPasswordBeforeMerge() {
        UserModel current = new UserModel();
        current.setMemberType("FACILITY_USER");
        current.setRegisteredDate(new Date());
        current.setPassword("pbkdf2_sha256_v1$310000$currentSalt$currentHash");
        when(entityManager.find(UserModel.class, 10L)).thenReturn(current);

        UserModel update = new UserModel();
        update.setId(10L);
        update.setPassword("21232f297a57a5a743894a0e4a801fc3");

        when(passwordHashService.isManagedHash("21232f297a57a5a743894a0e4a801fc3")).thenReturn(false);
        when(passwordHashService.hashForStorage("21232f297a57a5a743894a0e4a801fc3"))
                .thenReturn("pbkdf2_sha256_v1$310000$newSalt$newHash");

        int result = userServiceBean.updateUser(update);

        assertThat(result).isEqualTo(1);
        assertThat(update.getPassword()).isEqualTo("pbkdf2_sha256_v1$310000$newSalt$newHash");
        verify(entityManager).merge(update);
    }

    @Test
    void authenticateReturnsFalseWhenAccountIsLocked() {
        when(loginAttemptPolicyService.preCheck(eq("F001:user01"), eq("192.0.2.40"), any()))
                .thenReturn(LoginAttemptPolicyService.PreCheckResult.locked());

        UserServiceBean.AuthenticationResult result =
                userServiceBean.authenticateWithPolicy("F001:user01", "RawPass123", "192.0.2.40");

        assertThat(result.authenticated()).isFalse();
        assertThat(result.ipThrottled()).isFalse();
        verify(loginAttemptPolicyService).registerFailure(eq("F001:user01"), eq("192.0.2.40"), any());
    }

    @Test
    void authenticateReturnsIpThrottleWhenFailureTriggersIpLimit() {
        UserModel user = new UserModel();
        user.setUserId("F001:user01");
        user.setPassword("stored-hash");
        when(query.getSingleResult()).thenReturn(user);
        when(passwordHashService.verify("stored-hash", "WrongPass!"))
                .thenReturn(PasswordHashService.VerificationResult.failure());
        when(loginAttemptPolicyService.registerFailure(eq("F001:user01"), eq("192.0.2.50"), any()))
                .thenReturn(new LoginAttemptPolicyService.FailureResult(false, true, 120L));

        UserServiceBean.AuthenticationResult result =
                userServiceBean.authenticateWithPolicy("F001:user01", "WrongPass!", "192.0.2.50");

        assertThat(result.authenticated()).isFalse();
        assertThat(result.ipThrottled()).isTrue();
        assertThat(result.retryAfterSeconds()).isEqualTo(120L);
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        var field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
