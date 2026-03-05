package open.dolphin.security.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.junit.jupiter.api.Test;

class PasswordHashServiceTest {

    private static final String LEGACY_PREFIX = "pbkdf2_md5";

    private final PasswordHashService service = new PasswordHashService();

    @Test
    void hashAndVerifyWithRawPassword() {
        String stored = service.hashForStorage("VerySecret123!");

        assertThat(stored).startsWith(PasswordHashService.FORMAT_PREFIX + "$");
        String[] parts = stored.split("\\$", 4);
        assertThat(parts).hasSize(4);
        assertThat(Integer.parseInt(parts[1])).isGreaterThanOrEqualTo(PasswordHashService.MIN_ITERATIONS);
        assertThat(Base64.getDecoder().decode(parts[2]).length).isGreaterThanOrEqualTo(PasswordHashService.MIN_SALT_BYTES);

        PasswordHashService.VerificationResult verification = service.verify(stored, "VerySecret123!");
        assertThat(verification.matched()).isTrue();
        assertThat(verification.requiresUpgrade()).isFalse();
        assertThat(verification.upgradedHash()).isEmpty();
    }

    @Test
    void verifyRejectsMd5InputForManagedHash() {
        String rawPassword = "CompatRawPass!";
        String stored = service.hashForStorage(rawPassword);
        String md5Password = md5(rawPassword);

        PasswordHashService.VerificationResult verification = service.verify(stored, md5Password);

        assertThat(verification.matched()).isFalse();
        assertThat(verification.requiresUpgrade()).isFalse();
    }

    @Test
    void legacyManagedMd5AuthenticationRequiresUpgrade() {
        String rawPassword = "LegacyManagedPass!";
        String legacy = legacyManagedHash(rawPassword, 200_000);

        PasswordHashService.VerificationResult verification = service.verify(legacy, rawPassword);

        assertThat(verification.matched()).isTrue();
        assertThat(verification.requiresUpgrade()).isTrue();
        String upgraded = verification.upgradedHash().orElseThrow();
        assertThat(upgraded).startsWith(PasswordHashService.FORMAT_PREFIX + "$");
        assertThat(service.verify(upgraded, rawPassword).matched()).isTrue();
    }

    @Test
    void legacyMd5AuthenticationRequiresUpgrade() {
        String rawPassword = "LegacyPass!";
        String legacyMd5 = md5(rawPassword);

        PasswordHashService.VerificationResult verification = service.verify(legacyMd5, rawPassword);

        assertThat(verification.matched()).isTrue();
        assertThat(verification.requiresUpgrade()).isTrue();
        assertThat(verification.upgradedHash()).isPresent();
    }

    @Test
    void legacyPlainAuthenticationRequiresUpgrade() {
        PasswordHashService.VerificationResult verification = service.verify("plain-password", "plain-password");

        assertThat(verification.matched()).isTrue();
        assertThat(verification.requiresUpgrade()).isTrue();
        assertThat(verification.upgradedHash()).isPresent();
    }

    private String legacyManagedHash(String rawPassword, int iterations) {
        try {
            String md5 = md5(rawPassword);
            byte[] salt = new byte[16];
            for (int i = 0; i < salt.length; i++) {
                salt[i] = (byte) (i + 3);
            }
            PBEKeySpec spec = new PBEKeySpec(md5.toCharArray(), salt, iterations, 256);
            byte[] hash = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec)
                    .getEncoded();
            return LEGACY_PREFIX + "$" + iterations + "$"
                    + Base64.getEncoder().encodeToString(salt) + "$"
                    + Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException(e);
        }
    }

    private String md5(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 algorithm is not available", e);
        }
    }
}
