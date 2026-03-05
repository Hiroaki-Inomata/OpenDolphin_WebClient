package open.dolphin.security.auth;

import jakarta.enterprise.context.ApplicationScoped;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import java.util.Optional;
import java.util.regex.Pattern;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

@ApplicationScoped
public class PasswordHashService {

    public static final String FORMAT_PREFIX = "pbkdf2_sha256_v1";
    public static final int MIN_ITERATIONS = 310_000;
    public static final int MIN_SALT_BYTES = 16;

    private static final String LEGACY_FORMAT_PREFIX = "pbkdf2_md5";
    private static final int DEFAULT_ITERATIONS = MIN_ITERATIONS;
    private static final int DEFAULT_SALT_BYTES = MIN_SALT_BYTES;
    private static final int KEY_LENGTH_BITS = 256;
    private static final String KDF_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final Pattern LEGACY_MD5_PATTERN = Pattern.compile("(?i)^[0-9a-f]{32}$");

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 新規保存時は必ず raw password を直接 PBKDF2-HMAC-SHA256 へ適用する。
     */
    public String hashForStorage(String rawPassword) {
        if (rawPassword == null) {
            throw new IllegalArgumentException("password must not be null");
        }
        return hashRawWithParameters(rawPassword, DEFAULT_ITERATIONS, randomSalt(DEFAULT_SALT_BYTES));
    }

    public String hashRaw(String rawPassword) {
        return hashForStorage(rawPassword);
    }

    public boolean isManagedHash(String storedPassword) {
        return parseManagedHash(storedPassword) != null;
    }

    public VerificationResult verify(String storedPassword, String presentedPassword) {
        if (storedPassword == null || presentedPassword == null) {
            return VerificationResult.failure();
        }

        ParsedHash managed = parseManagedHash(storedPassword);
        if (managed != null) {
            return verifyManagedHash(managed, presentedPassword);
        }

        if (isLegacyMd5(storedPassword)) {
            String expectedMd5 = md5Hex(presentedPassword);
            if (!storedPassword.equalsIgnoreCase(expectedMd5)) {
                return VerificationResult.failure();
            }
            return VerificationResult.successWithUpgrade(hashForStorage(presentedPassword));
        }

        if (!storedPassword.equals(presentedPassword)) {
            return VerificationResult.failure();
        }
        return VerificationResult.successWithUpgrade(hashForStorage(presentedPassword));
    }

    private VerificationResult verifyManagedHash(ParsedHash parsed, String presentedPassword) {
        byte[] actual;
        if (parsed.algorithm() == HashAlgorithm.PBKDF2_SHA256_RAW) {
            actual = derive(presentedPassword, parsed.iterations(), parsed.salt());
            boolean matched = MessageDigest.isEqual(parsed.hash(), actual);
            if (!matched) {
                return VerificationResult.failure();
            }
            boolean needsParamUpgrade = parsed.iterations() < MIN_ITERATIONS || parsed.salt().length < MIN_SALT_BYTES;
            if (needsParamUpgrade) {
                return VerificationResult.successWithUpgrade(hashForStorage(presentedPassword));
            }
            return VerificationResult.success();
        }

        String canonicalMd5 = md5Hex(presentedPassword);
        actual = derive(canonicalMd5, parsed.iterations(), parsed.salt());
        boolean matched = MessageDigest.isEqual(parsed.hash(), actual);
        if (!matched) {
            return VerificationResult.failure();
        }
        return VerificationResult.successWithUpgrade(hashForStorage(presentedPassword));
    }

    private byte[] randomSalt(int length) {
        int effectiveLength = Math.max(length, MIN_SALT_BYTES);
        byte[] salt = new byte[effectiveLength];
        secureRandom.nextBytes(salt);
        return salt;
    }

    private String hashRawWithParameters(String rawPassword, int iterations, byte[] salt) {
        int effectiveIterations = Math.max(iterations, MIN_ITERATIONS);
        byte[] effectiveSalt = salt != null && salt.length >= MIN_SALT_BYTES ? salt : randomSalt(DEFAULT_SALT_BYTES);
        byte[] hash = derive(rawPassword, effectiveIterations, effectiveSalt);
        String saltB64 = Base64.getEncoder().encodeToString(effectiveSalt);
        String hashB64 = Base64.getEncoder().encodeToString(hash);
        return FORMAT_PREFIX + "$" + effectiveIterations + "$" + saltB64 + "$" + hashB64;
    }

    private byte[] derive(String value, int iterations, byte[] salt) {
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance(KDF_ALGORITHM);
            PBEKeySpec spec = new PBEKeySpec(value.toCharArray(), salt, iterations, KEY_LENGTH_BITS);
            return factory.generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Password hash algorithm is not available", e);
        }
    }

    private ParsedHash parseManagedHash(String storedPassword) {
        if (storedPassword == null) {
            return null;
        }
        String[] parts = storedPassword.split("\\$", 4);
        if (parts.length != 4) {
            return null;
        }

        HashAlgorithm algorithm = resolveAlgorithm(parts[0]);
        if (algorithm == null) {
            return null;
        }

        int iterations;
        try {
            iterations = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return null;
        }
        if (iterations <= 0) {
            return null;
        }

        try {
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] hash = Base64.getDecoder().decode(parts[3]);
            if (salt.length == 0 || hash.length == 0) {
                return null;
            }
            return new ParsedHash(algorithm, iterations, salt, hash);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private HashAlgorithm resolveAlgorithm(String prefix) {
        if (FORMAT_PREFIX.equals(prefix)) {
            return HashAlgorithm.PBKDF2_SHA256_RAW;
        }
        if (LEGACY_FORMAT_PREFIX.equals(prefix)) {
            return HashAlgorithm.PBKDF2_SHA256_MD5;
        }
        return null;
    }

    private boolean isLegacyMd5(String value) {
        return value != null && LEGACY_MD5_PATTERN.matcher(value).matches();
    }

    private String md5Hex(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] bytes = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 algorithm is not available", e);
        }
    }

    public static final class VerificationResult {
        private static final VerificationResult FAILURE = new VerificationResult(false, false, null);
        private static final VerificationResult SUCCESS = new VerificationResult(true, false, null);

        private final boolean matched;
        private final boolean requiresUpgrade;
        private final String upgradedHash;

        private VerificationResult(boolean matched, boolean requiresUpgrade, String upgradedHash) {
            this.matched = matched;
            this.requiresUpgrade = requiresUpgrade;
            this.upgradedHash = upgradedHash;
        }

        public static VerificationResult failure() {
            return FAILURE;
        }

        public static VerificationResult success() {
            return SUCCESS;
        }

        public static VerificationResult successWithUpgrade(String upgradedHash) {
            return new VerificationResult(true, true, upgradedHash);
        }

        public boolean matched() {
            return matched;
        }

        public boolean requiresUpgrade() {
            return requiresUpgrade;
        }

        public Optional<String> upgradedHash() {
            return Optional.ofNullable(upgradedHash);
        }
    }

    private enum HashAlgorithm {
        PBKDF2_SHA256_RAW,
        PBKDF2_SHA256_MD5
    }

    private record ParsedHash(HashAlgorithm algorithm, int iterations, byte[] salt, byte[] hash) {
    }
}
