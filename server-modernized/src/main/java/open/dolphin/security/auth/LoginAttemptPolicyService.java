package open.dolphin.security.auth;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Basic 認証の失敗状態を DB 永続化で管理する。
 */
@ApplicationScoped
@Transactional
public class LoginAttemptPolicyService {

    public static final int ACCOUNT_FAILURE_LIMIT = 5;
    public static final int IP_FAILURE_LIMIT = 20;
    public static final Duration WINDOW = Duration.ofMinutes(15);
    public static final Duration LOCK_DURATION = Duration.ofMinutes(15);
    private static final Duration CLEANUP_RETENTION = Duration.ofDays(1);

    @PersistenceContext
    private EntityManager em;

    public PreCheckResult preCheck(String compositeUser, String clientIp, Instant now) {
        Instant effectiveNow = now != null ? now : Instant.now();
        cleanupExpired(effectiveNow);

        String normalizedIp = normalize(clientIp);
        if (normalizedIp != null) {
            IpState ipState = loadIpState(normalizedIp);
            if (ipState != null && isActive(ipState.until(), effectiveNow)) {
                return PreCheckResult.throttled(secondsUntil(ipState.until(), effectiveNow));
            }
        }

        AccountKey accountKey = parseCompositeUser(compositeUser);
        if (accountKey != null) {
            AccountState accountState = loadAccountState(accountKey);
            if (accountState != null && isActive(accountState.until(), effectiveNow)) {
                return PreCheckResult.locked();
            }
        }
        return PreCheckResult.allowed();
    }

    public FailureResult registerFailure(String compositeUser, String clientIp, Instant now) {
        Instant effectiveNow = now != null ? now : Instant.now();
        cleanupExpired(effectiveNow);

        boolean accountLocked = false;
        AccountKey accountKey = parseCompositeUser(compositeUser);
        if (accountKey != null) {
            AccountState accountState = bumpAccountFailure(accountKey, effectiveNow);
            accountLocked = accountState != null && isActive(accountState.until(), effectiveNow);
        }

        long retryAfter = 0L;
        String normalizedIp = normalize(clientIp);
        if (normalizedIp != null) {
            IpState ipState = bumpIpFailure(normalizedIp, effectiveNow);
            if (ipState != null && isActive(ipState.until(), effectiveNow)) {
                retryAfter = secondsUntil(ipState.until(), effectiveNow);
            }
        }

        return new FailureResult(accountLocked, retryAfter > 0, retryAfter);
    }

    public void registerSuccess(String compositeUser, Instant now) {
        AccountKey accountKey = parseCompositeUser(compositeUser);
        if (accountKey == null) {
            return;
        }
        Instant effectiveNow = now != null ? now : Instant.now();
        upsertAccountState(accountKey, 0, effectiveNow, null, effectiveNow);
    }

    private AccountState bumpAccountFailure(AccountKey accountKey, Instant now) {
        AccountState current = loadAccountState(accountKey);
        Instant threshold = now.minus(WINDOW);
        int baseCount = 0;
        Instant windowStartedAt = now;
        Instant lockUntil = null;

        if (current != null) {
            boolean sameWindow = !current.windowStartedAt().isBefore(threshold);
            if (sameWindow) {
                baseCount = Math.max(0, current.failCount());
                windowStartedAt = current.windowStartedAt();
            }
            if (isActive(current.until(), now)) {
                lockUntil = current.until();
            }
        }

        int nextCount = baseCount + 1;
        if (nextCount >= ACCOUNT_FAILURE_LIMIT) {
            lockUntil = now.plus(LOCK_DURATION);
        }
        upsertAccountState(accountKey, nextCount, windowStartedAt, lockUntil, now);
        return new AccountState(nextCount, windowStartedAt, lockUntil);
    }

    private IpState bumpIpFailure(String clientIp, Instant now) {
        IpState current = loadIpState(clientIp);
        Instant threshold = now.minus(WINDOW);
        int baseCount = 0;
        Instant windowStartedAt = now;
        Instant until = null;

        if (current != null) {
            boolean sameWindow = !current.windowStartedAt().isBefore(threshold);
            if (sameWindow) {
                baseCount = Math.max(0, current.failCount());
                windowStartedAt = current.windowStartedAt();
            }
            if (isActive(current.until(), now)) {
                until = current.until();
            }
        }

        int nextCount = baseCount + 1;
        if (nextCount >= IP_FAILURE_LIMIT) {
            until = now.plus(LOCK_DURATION);
        }
        upsertIpState(clientIp, nextCount, windowStartedAt, until, now);
        return new IpState(nextCount, windowStartedAt, until);
    }

    private void cleanupExpired(Instant now) {
        Instant retentionCutoff = now.minus(CLEANUP_RETENTION);
        Timestamp cutoff = Timestamp.from(retentionCutoff);
        Timestamp current = Timestamp.from(now);
        em.createNativeQuery(
                        "delete from opendolphin.d_auth_account_failure "
                                + "where updated_at < :cutoff and (lock_until is null or lock_until < :now)")
                .setParameter("cutoff", cutoff)
                .setParameter("now", current)
                .executeUpdate();
        em.createNativeQuery(
                        "delete from opendolphin.d_auth_ip_failure "
                                + "where updated_at < :cutoff and (throttle_until is null or throttle_until < :now)")
                .setParameter("cutoff", cutoff)
                .setParameter("now", current)
                .executeUpdate();
    }

    private AccountState loadAccountState(AccountKey accountKey) {
        List<?> rows = em.createNativeQuery(
                        "select fail_count, window_started_at, lock_until "
                                + "from opendolphin.d_auth_account_failure "
                                + "where facility_id=:facilityId and user_id=:userId")
                .setParameter("facilityId", accountKey.facilityId())
                .setParameter("userId", accountKey.userId())
                .setMaxResults(1)
                .getResultList();
        if (rows.isEmpty()) {
            return null;
        }
        Object rowObj = rows.get(0);
        if (!(rowObj instanceof Object[] row) || row.length < 3) {
            return null;
        }
        Integer failCount = asInt(row[0]);
        Instant windowStartedAt = asInstant(row[1]);
        Instant lockUntil = asInstant(row[2]);
        if (failCount == null || windowStartedAt == null) {
            return null;
        }
        return new AccountState(failCount, windowStartedAt, lockUntil);
    }

    private IpState loadIpState(String clientIp) {
        List<?> rows = em.createNativeQuery(
                        "select fail_count, window_started_at, throttle_until "
                                + "from opendolphin.d_auth_ip_failure where client_ip=:clientIp")
                .setParameter("clientIp", clientIp)
                .setMaxResults(1)
                .getResultList();
        if (rows.isEmpty()) {
            return null;
        }
        Object rowObj = rows.get(0);
        if (!(rowObj instanceof Object[] row) || row.length < 3) {
            return null;
        }
        Integer failCount = asInt(row[0]);
        Instant windowStartedAt = asInstant(row[1]);
        Instant throttleUntil = asInstant(row[2]);
        if (failCount == null || windowStartedAt == null) {
            return null;
        }
        return new IpState(failCount, windowStartedAt, throttleUntil);
    }

    private void upsertAccountState(AccountKey accountKey, int failCount, Instant windowStartedAt, Instant lockUntil, Instant now) {
        em.createNativeQuery(
                        "insert into opendolphin.d_auth_account_failure "
                                + "(facility_id, user_id, fail_count, window_started_at, lock_until, updated_at) "
                                + "values (:facilityId, :userId, :failCount, :windowStartedAt, :lockUntil, :updatedAt) "
                                + "on conflict (facility_id, user_id) do update set "
                                + "fail_count=excluded.fail_count, "
                                + "window_started_at=excluded.window_started_at, "
                                + "lock_until=excluded.lock_until, "
                                + "updated_at=excluded.updated_at")
                .setParameter("facilityId", accountKey.facilityId())
                .setParameter("userId", accountKey.userId())
                .setParameter("failCount", Math.max(0, failCount))
                .setParameter("windowStartedAt", Timestamp.from(windowStartedAt))
                .setParameter("lockUntil", lockUntil == null ? null : Timestamp.from(lockUntil))
                .setParameter("updatedAt", Timestamp.from(now))
                .executeUpdate();
    }

    private void upsertIpState(String clientIp, int failCount, Instant windowStartedAt, Instant throttleUntil, Instant now) {
        em.createNativeQuery(
                        "insert into opendolphin.d_auth_ip_failure "
                                + "(client_ip, fail_count, window_started_at, throttle_until, updated_at) "
                                + "values (:clientIp, :failCount, :windowStartedAt, :throttleUntil, :updatedAt) "
                                + "on conflict (client_ip) do update set "
                                + "fail_count=excluded.fail_count, "
                                + "window_started_at=excluded.window_started_at, "
                                + "throttle_until=excluded.throttle_until, "
                                + "updated_at=excluded.updated_at")
                .setParameter("clientIp", clientIp)
                .setParameter("failCount", Math.max(0, failCount))
                .setParameter("windowStartedAt", Timestamp.from(windowStartedAt))
                .setParameter("throttleUntil", throttleUntil == null ? null : Timestamp.from(throttleUntil))
                .setParameter("updatedAt", Timestamp.from(now))
                .executeUpdate();
    }

    private AccountKey parseCompositeUser(String compositeUser) {
        String normalized = normalize(compositeUser);
        if (normalized == null) {
            return null;
        }
        int separator = normalized.indexOf(':');
        if (separator <= 0 || separator >= normalized.length() - 1) {
            return null;
        }
        String facilityId = normalize(normalized.substring(0, separator));
        String userId = normalize(normalized.substring(separator + 1));
        if (facilityId == null || userId == null) {
            return null;
        }
        return new AccountKey(facilityId, userId);
    }

    private Integer asInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return null;
    }

    private Instant asInstant(Object value) {
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant();
        }
        if (value instanceof java.util.Date date) {
            return date.toInstant();
        }
        return null;
    }

    private boolean isActive(Instant until, Instant now) {
        return until != null && until.isAfter(now);
    }

    private long secondsUntil(Instant until, Instant now) {
        if (until == null || !until.isAfter(now)) {
            return 0L;
        }
        long seconds = Duration.between(now, until).toSeconds();
        return seconds > 0 ? seconds : 1L;
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record PreCheckResult(boolean accountLocked, boolean ipThrottled, long retryAfterSeconds) {
        public static PreCheckResult allowed() {
            return new PreCheckResult(false, false, 0L);
        }

        public static PreCheckResult locked() {
            return new PreCheckResult(true, false, 0L);
        }

        public static PreCheckResult throttled(long retryAfterSeconds) {
            return new PreCheckResult(false, true, Math.max(1L, retryAfterSeconds));
        }
    }

    public record FailureResult(boolean accountLocked, boolean ipThrottled, long retryAfterSeconds) {
    }

    private record AccountKey(String facilityId, String userId) {
    }

    private record AccountState(int failCount, Instant windowStartedAt, Instant until) {
    }

    private record IpState(int failCount, Instant windowStartedAt, Instant until) {
    }
}
