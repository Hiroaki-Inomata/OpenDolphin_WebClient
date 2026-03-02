package open.dolphin.security.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class PasswordHashServiceTest {

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
    void verifyAcceptsMd5InputForManagedHash() {
        String rawPassword = "CompatRawPass!";
        String stored = service.hashForStorage(rawPassword);
        String md5Password = md5(rawPassword);

        PasswordHashService.VerificationResult verification = service.verify(stored, md5Password);

        assertThat(verification.matched()).isTrue();
        assertThat(verification.requiresUpgrade()).isFalse();
    }

    @Test
    void legacyMd5AuthenticationRequiresUpgrade() {
        String rawPassword = "LegacyPass!";
        String legacyMd5 = md5(rawPassword);

        PasswordHashService.VerificationResult fromRaw = service.verify(legacyMd5, rawPassword);
        assertThat(fromRaw.matched()).isTrue();
        assertThat(fromRaw.requiresUpgrade()).isTrue();
        String upgradedHash = fromRaw.upgradedHash().orElseThrow();
        assertThat(service.verify(upgradedHash, rawPassword).matched()).isTrue();

        PasswordHashService.VerificationResult fromMd5 = service.verify(legacyMd5, legacyMd5);
        assertThat(fromMd5.matched()).isTrue();
        assertThat(fromMd5.requiresUpgrade()).isTrue();
    }

    @Test
    void legacyPlainAuthenticationRequiresUpgrade() {
        PasswordHashService.VerificationResult verification = service.verify("plain-password", "plain-password");

        assertThat(verification.matched()).isTrue();
        assertThat(verification.requiresUpgrade()).isTrue();
        assertThat(verification.upgradedHash()).isPresent();
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
