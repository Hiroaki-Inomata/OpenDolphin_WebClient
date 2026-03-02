package open.dolphin.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.Date;
import open.dolphin.infomodel.UserModel;
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

    private UserServiceBean userServiceBean;

    @BeforeEach
    void setUp() throws Exception {
        userServiceBean = new UserServiceBean();
        setField(userServiceBean, "em", entityManager);
        setField(userServiceBean, "passwordHashService", passwordHashService);

        lenient().when(entityManager.createQuery(anyString())).thenReturn(query);
        lenient().when(query.setParameter(anyString(), any())).thenReturn(query);
    }

    @Test
    void authenticateUpgradesLegacyPasswordOnSuccess() {
        UserModel user = new UserModel();
        user.setUserId("F001:user01");
        user.setPassword("legacy-md5-value");
        when(query.getSingleResult()).thenReturn(user);
        when(passwordHashService.verify("legacy-md5-value", "RawPass123"))
                .thenReturn(PasswordHashService.VerificationResult.successWithUpgrade("pbkdf2_md5$200000$salt$hash"));

        boolean authenticated = userServiceBean.authenticate("F001:user01", "RawPass123");

        assertThat(authenticated).isTrue();
        assertThat(user.getPassword()).isEqualTo("pbkdf2_md5$200000$salt$hash");
        verify(entityManager).merge(user);
    }

    @Test
    void updateUserHashesNonManagedPasswordBeforeMerge() {
        UserModel current = new UserModel();
        current.setMemberType("FACILITY_USER");
        current.setRegisteredDate(new Date());
        current.setPassword("pbkdf2_md5$200000$currentSalt$currentHash");
        when(entityManager.find(UserModel.class, 10L)).thenReturn(current);

        UserModel update = new UserModel();
        update.setId(10L);
        update.setPassword("21232f297a57a5a743894a0e4a801fc3");

        when(passwordHashService.isManagedHash("21232f297a57a5a743894a0e4a801fc3")).thenReturn(false);
        when(passwordHashService.hashForStorage("21232f297a57a5a743894a0e4a801fc3"))
                .thenReturn("pbkdf2_md5$200000$newSalt$newHash");

        int result = userServiceBean.updateUser(update);

        assertThat(result).isEqualTo(1);
        assertThat(update.getPassword()).isEqualTo("pbkdf2_md5$200000$newSalt$newHash");
        verify(entityManager).merge(update);
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        var field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
