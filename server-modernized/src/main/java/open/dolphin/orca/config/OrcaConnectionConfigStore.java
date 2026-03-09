package open.dolphin.orca.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import open.dolphin.rest.AbstractResource;
import open.dolphin.runtime.RuntimeConfigurationSupport;
import open.dolphin.security.SecondFactorSecurityConfig;
import open.dolphin.orca.transport.OrcaTransportSecurityPolicy;
import open.dolphin.security.totp.TotpSecretProtector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class OrcaConnectionConfigStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(OrcaConnectionConfigStore.class);

    private static final String STORAGE_DIR = "opendolphin";
    private static final String STORAGE_FILE = "orca-connection-config.json";

    private static final String ENV_ORCA_BASE_URL = "ORCA_BASE_URL";
    private static final String ENV_ORCA_API_HOST = "ORCA_API_HOST";
    private static final String ENV_ORCA_API_PORT = "ORCA_API_PORT";
    private static final String ENV_ORCA_API_SCHEME = "ORCA_API_SCHEME";
    private static final String ENV_ORCA_API_USER = "ORCA_API_USER";
    private static final String ENV_ORCA_API_PASSWORD = "ORCA_API_PASSWORD";
    private static final String ENV_ORCA_MODE = "ORCA_MODE";
    private static final String ENV_ORCA_API_WEBORCA = "ORCA_API_WEBORCA";

    private static final int DEFAULT_PORT_WEBORCA = 443;
    private static final int DEFAULT_PORT_ONPREM = 8000;

    private static final long DEFAULT_MAX_P12_BYTES = 10L * 1024L * 1024L; // 10MiB
    private static final long DEFAULT_MAX_CA_BYTES = 2L * 1024L * 1024L; // 2MiB

    private static final String DEFAULT_FACILITY_RECORD_KEY = "_default";
    private static final int MULTI_FACILITY_FORMAT_VERSION = 2;

    private final ObjectMapper mapper = AbstractResource.getSerializeMapper();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Path storagePath;

    @Inject
    private SecondFactorSecurityConfig secondFactorSecurityConfig;

    private TotpSecretProtector protector;
    private OrcaConnectionConfigRecord current;
    private Map<String, OrcaConnectionConfigRecord> records = new LinkedHashMap<>();

    public OrcaConnectionConfigStore() {
        this.storagePath = resolveStoragePath();
    }

    @PostConstruct
    public void init() {
        this.protector = secondFactorSecurityConfig != null ? secondFactorSecurityConfig.getTotpSecretProtector() : null;
        lock.writeLock().lock();
        try {
            Map<String, OrcaConnectionConfigRecord> loaded = loadRecords();
            if (loaded == null || loaded.isEmpty()) {
                OrcaConnectionConfigRecord initial = applyDefaults(defaultFromEnvironment());
                initial.setFacilityId(null);
                this.records = new LinkedHashMap<>();
                this.records.put(DEFAULT_FACILITY_RECORD_KEY, copyWithoutRecords(initial));
            } else {
                this.records = loaded;
            }
            refreshCurrentFromRecordsLocked();
            persistBestEffort(buildStorageRecord(this.records));
        } finally {
            lock.writeLock().unlock();
        }
    }

    public OrcaConnectionConfigRecord getSnapshot() {
        return getSnapshot(null);
    }

    public OrcaConnectionConfigRecord getSnapshot(String facilityId) {
        lock.readLock().lock();
        try {
            OrcaConnectionConfigRecord snapshot = selectRecordForFacilityLocked(facilityId);
            return copyWithoutRecords(snapshot);
        } finally {
            lock.readLock().unlock();
        }
    }

    public ResolvedOrcaConnection resolve() {
        return resolve(null);
    }

    public ResolvedOrcaConnection resolve(String facilityId) {
        OrcaConnectionConfigRecord snapshot = getSnapshot(facilityId);
        if (snapshot == null) {
            throw new IllegalStateException("ORCA connection config is not available");
        }
        return resolveFromRecord(snapshot);
    }

    public OrcaConnectionConfigRecord update(UpdateRequest update,
                                             UploadedBinary clientCertificate,
                                             UploadedBinary caCertificate,
                                             String runId,
                                             String actor) {
        return update(null, update, clientCertificate, caCertificate, runId, actor);
    }

    public OrcaConnectionConfigRecord update(String facilityId,
                                             UpdateRequest update,
                                             UploadedBinary clientCertificate,
                                             UploadedBinary caCertificate,
                                             String runId,
                                             String actor) {
        Objects.requireNonNull(update, "update");
        String normalizedFacilityId = normalizeFacilityId(facilityId);
        String recordKey = toRecordKey(normalizedFacilityId);

        lock.writeLock().lock();
        try {
            OrcaConnectionConfigRecord base = selectRecordForFacilityLocked(normalizedFacilityId);
            OrcaConnectionConfigRecord merged = base != null ? copyWithoutRecords(base) : new OrcaConnectionConfigRecord();
            merged.setFacilityId(fromRecordKey(recordKey));
            String now = Instant.now().toString();

            Boolean useWeborca = update.useWeborca();
            if (useWeborca != null) merged.setUseWeborca(useWeborca);

            String serverUrl = trimToNull(update.serverUrl());
            if (serverUrl != null) merged.setServerUrl(serverUrl);

            Integer port = update.port();
            if (port != null) merged.setPort(port);

            String username = trimToNull(update.username());
            if (username != null) merged.setUsername(username);

            String passwordPlain = trimToNull(update.password());
            if (passwordPlain != null) {
                merged.setPasswordEncrypted(encryptText(passwordPlain));
                merged.setPasswordUpdatedAt(now);
            }

            Boolean clientAuthEnabled = update.clientAuthEnabled();
            if (clientAuthEnabled != null) merged.setClientAuthEnabled(clientAuthEnabled);

            String passphrasePlain = trimToNull(update.clientCertificatePassphrase());
            if (passphrasePlain != null) {
                merged.setClientCertificatePassphraseEncrypted(encryptText(passphrasePlain));
                merged.setClientCertificatePassphraseUpdatedAt(now);
            }

            if (clientCertificate != null && clientCertificate.bytes != null && clientCertificate.bytes.length > 0) {
                requireMaxBytes(clientCertificate.bytes.length, DEFAULT_MAX_P12_BYTES, "clientCertificate");
                String fileName = trimToNull(clientCertificate.fileName);
                if (fileName != null && !fileName.toLowerCase(Locale.ROOT).endsWith(".p12") && !fileName.toLowerCase(Locale.ROOT).endsWith(".pfx")) {
                    throw new IllegalArgumentException("クライアント証明書は .p12（または .pfx）を指定してください。");
                }
                merged.setClientCertificateFileName(fileName);
                merged.setClientCertificateUploadedAt(now);
                merged.setClientCertificateP12Encrypted(encryptBytes(clientCertificate.bytes));
            }

            if (caCertificate != null && caCertificate.bytes != null && caCertificate.bytes.length > 0) {
                requireMaxBytes(caCertificate.bytes.length, DEFAULT_MAX_CA_BYTES, "caCertificate");
                String fileName = trimToNull(caCertificate.fileName);
                merged.setCaCertificateFileName(fileName);
                merged.setCaCertificateUploadedAt(now);
                merged.setCaCertificateEncrypted(encryptBytes(caCertificate.bytes));
            }

            merged.setUpdatedAt(now);

            merged = applyDefaults(merged);
            validateReadyForUpdate(merged);

            // Validate TLS materials (when enabled) early so we can show a clear message on save.
            if (Boolean.TRUE.equals(merged.getClientAuthEnabled())) {
                ResolvedOrcaConnection resolved = resolveFromRecord(merged);
                if (resolved.clientAuthEnabled()) {
                    try {
                        open.dolphin.orca.transport.OrcaTlsSupport.buildSslContext(
                                resolved.clientCertificateP12(),
                                resolved.clientCertificatePassphrase(),
                                resolved.caCertificate());
                    } catch (RuntimeException ex) {
                        throw new IllegalArgumentException("クライアント証明書またはパスフレーズが不正です。", ex);
                    }
                }
            } else if (merged.getCaCertificateEncrypted() != null && !merged.getCaCertificateEncrypted().isBlank()) {
                byte[] caBytes = decryptToBytes(merged.getCaCertificateEncrypted(), "caCertificateEncrypted");
                open.dolphin.orca.transport.OrcaTlsSupport.validateCaCertificateBundle(caBytes);
            }

            Map<String, OrcaConnectionConfigRecord> nextRecords = new LinkedHashMap<>(records != null ? records : Map.of());
            nextRecords.put(recordKey, copyWithoutRecords(merged));
            // Keep the unresolved-facility fallback in sync with the latest saved settings.
            OrcaConnectionConfigRecord fallback = copyWithoutRecords(merged);
            fallback.setFacilityId(null);
            nextRecords.put(DEFAULT_FACILITY_RECORD_KEY, fallback);

            persistStrict(buildStorageRecord(nextRecords));
            this.records = nextRecords;
            refreshCurrentFromRecordsLocked();

            LOGGER.info("ORCA connection config updated. runId={} actor={} facilityId={} weborca={} clientAuthEnabled={} caProvided={}",
                    safe(runId),
                    maskActor(actor),
                    safe(normalizedFacilityId),
                    Boolean.TRUE.equals(merged.getUseWeborca()),
                    Boolean.TRUE.equals(merged.getClientAuthEnabled()),
                    merged.getCaCertificateEncrypted() != null && !merged.getCaCertificateEncrypted().isBlank());
            return copyWithoutRecords(nextRecords.get(recordKey));
        } finally {
            lock.writeLock().unlock();
        }
    }

    private ResolvedOrcaConnection resolveFromRecord(OrcaConnectionConfigRecord record) {
        if (record == null) {
            throw new IllegalStateException("record is null");
        }
        validateReady(record);
        String baseUrl = buildBaseUrl(record.getServerUrl(), record.getPort(), Boolean.TRUE.equals(record.getUseWeborca()));
        OrcaTransportSecurityPolicy.validateBaseUrl(baseUrl, Boolean.TRUE.equals(record.getUseWeborca()));
        String password = decryptToText(record.getPasswordEncrypted(), "passwordEncrypted");
        boolean clientAuthEnabled = Boolean.TRUE.equals(record.getClientAuthEnabled());
        byte[] p12 = null;
        String passphrase = null;
        if (clientAuthEnabled) {
            p12 = decryptToBytes(record.getClientCertificateP12Encrypted(), "clientCertificateP12Encrypted");
            passphrase = decryptToText(record.getClientCertificatePassphraseEncrypted(), "clientCertificatePassphraseEncrypted");
        }
        byte[] ca = null;
        if (record.getCaCertificateEncrypted() != null && !record.getCaCertificateEncrypted().isBlank()) {
            ca = decryptToBytes(record.getCaCertificateEncrypted(), "caCertificateEncrypted");
        }
        return new ResolvedOrcaConnection(
                Boolean.TRUE.equals(record.getUseWeborca()),
                baseUrl,
                trimToNull(record.getUsername()),
                password,
                clientAuthEnabled,
                p12,
                passphrase,
                ca
        );
    }

    private Map<String, OrcaConnectionConfigRecord> loadRecords() {
        OrcaConnectionConfigRecord raw = loadRaw();
        if (raw == null) {
            return null;
        }

        Map<String, OrcaConnectionConfigRecord> loaded = new LinkedHashMap<>();
        Map<String, OrcaConnectionConfigRecord> mapped = raw.getRecords();
        if (mapped != null && !mapped.isEmpty()) {
            for (Map.Entry<String, OrcaConnectionConfigRecord> entry : mapped.entrySet()) {
                if (entry == null || entry.getValue() == null) {
                    continue;
                }
                String key = toRecordKey(entry.getKey());
                OrcaConnectionConfigRecord scoped = applyDefaults(copyWithoutRecords(entry.getValue()));
                scoped.setFacilityId(fromRecordKey(key));
                loaded.put(key, scoped);
            }
        }

        if (hasConnectionPayload(raw) && !loaded.containsKey(DEFAULT_FACILITY_RECORD_KEY)) {
            throw new IllegalStateException(
                    "Legacy single-record ORCA connection config is no longer supported. Migrate to the records format.");
        }

        if (loaded.isEmpty()) {
            return null;
        }
        if (!loaded.containsKey(DEFAULT_FACILITY_RECORD_KEY)) {
            OrcaConnectionConfigRecord first = loaded.values().iterator().next();
            OrcaConnectionConfigRecord fallback = copyWithoutRecords(first);
            fallback.setFacilityId(null);
            loaded.put(DEFAULT_FACILITY_RECORD_KEY, fallback);
        }
        return loaded;
    }

    private OrcaConnectionConfigRecord loadRaw() {
        if (storagePath == null || !Files.exists(storagePath)) {
            return null;
        }
        try {
            return mapper.readValue(storagePath.toFile(), OrcaConnectionConfigRecord.class);
        } catch (IOException ex) {
            LOGGER.warn("Failed to load ORCA connection config from {}: {}", storagePath, ex.getMessage());
            return null;
        }
    }

    private void persistBestEffort(OrcaConnectionConfigRecord record) {
        try {
            persistStrict(record);
        } catch (RuntimeException ex) {
            LOGGER.warn("Failed to persist ORCA connection config in init phase: {}", ex.getMessage());
        }
    }

    private void persistStrict(OrcaConnectionConfigRecord record) {
        if (record == null) {
            throw new IllegalStateException("ORCA connection config record is null");
        }
        if (storagePath == null) {
            throw new IllegalStateException("ORCA connection config storage path is not available");
        }
        try {
            mapper.writeValue(storagePath.toFile(), record);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to persist ORCA connection config", ex);
        }
    }

    private OrcaConnectionConfigRecord buildStorageRecord(Map<String, OrcaConnectionConfigRecord> byFacility) {
        if (byFacility == null || byFacility.isEmpty()) {
            throw new IllegalStateException("ORCA connection records are empty");
        }

        OrcaConnectionConfigRecord root = new OrcaConnectionConfigRecord();
        Map<String, OrcaConnectionConfigRecord> serialized = new LinkedHashMap<>();
        for (Map.Entry<String, OrcaConnectionConfigRecord> entry : byFacility.entrySet()) {
            if (entry == null || entry.getValue() == null) {
                continue;
            }
            String key = toRecordKey(entry.getKey());
            OrcaConnectionConfigRecord scoped = applyDefaults(copyWithoutRecords(entry.getValue()));
            scoped.setFacilityId(fromRecordKey(key));
            scoped.setRecords(null);
            serialized.put(key, scoped);
        }
        if (serialized.isEmpty()) {
            throw new IllegalStateException("ORCA connection records are empty");
        }

        OrcaConnectionConfigRecord fallback = serialized.get(DEFAULT_FACILITY_RECORD_KEY);
        if (fallback == null) {
            fallback = copyWithoutRecords(serialized.values().iterator().next());
            fallback.setFacilityId(null);
            serialized.put(DEFAULT_FACILITY_RECORD_KEY, fallback);
        }

        copyFlatFields(fallback, root);
        root.setVersion(Math.max(MULTI_FACILITY_FORMAT_VERSION, fallback.getVersion()));
        root.setUpdatedAt(Instant.now().toString());
        root.setFacilityId(null);
        root.setRecords(serialized);
        return root;
    }

    private OrcaConnectionConfigRecord defaultFromEnvironment() {
        OrcaConnectionConfigRecord record = new OrcaConnectionConfigRecord();
        record.setUpdatedAt(Instant.now().toString());

        boolean useWeborca = resolveUseWeborca();
        record.setUseWeborca(useWeborca);

        String baseUrl = trimToNull(env(ENV_ORCA_BASE_URL));
        String scheme = trimToNull(env(ENV_ORCA_API_SCHEME));
        String host = trimToNull(env(ENV_ORCA_API_HOST));
        Integer port = parsePort(env(ENV_ORCA_API_PORT));

        if (baseUrl != null) {
            // Try to derive host/scheme/port from baseUrl, but keep baseUrl as-is for UI.
            record.setServerUrl(baseUrl);
            URI uri = tryParseUri(baseUrl);
            if (uri != null) {
                if (scheme == null && uri.getScheme() != null) scheme = uri.getScheme();
                if (port == null && uri.getPort() > 0) port = uri.getPort();
            }
        } else if (host != null) {
            String resolvedScheme = scheme != null ? scheme : (useWeborca ? "https" : "http");
            if (host.contains("://")) {
                record.setServerUrl(host);
                URI uri = tryParseUri(host);
                if (uri != null) {
                    if (port == null && uri.getPort() > 0) port = uri.getPort();
                }
            } else {
                record.setServerUrl(resolvedScheme + "://" + host);
            }
        }

        if (port != null) {
            record.setPort(port);
        }

        String username = trimToNull(env(ENV_ORCA_API_USER));
        if (username != null) record.setUsername(username);

        String password = trimToNull(env(ENV_ORCA_API_PASSWORD));
        if (password != null) {
            record.setPasswordEncrypted(encryptText(password));
            record.setPasswordUpdatedAt(record.getUpdatedAt());
        }

        record.setClientAuthEnabled(Boolean.FALSE);
        record.setFacilityId(null);
        return applyDefaults(record);
    }

    private OrcaConnectionConfigRecord applyDefaults(OrcaConnectionConfigRecord record) {
        OrcaConnectionConfigRecord resolved = record != null ? record : new OrcaConnectionConfigRecord();
        if (resolved.getUseWeborca() == null) resolved.setUseWeborca(Boolean.FALSE);
        if (resolved.getClientAuthEnabled() == null) resolved.setClientAuthEnabled(Boolean.FALSE);

        String serverUrl = trimToNull(resolved.getServerUrl());
        if (serverUrl != null) {
            resolved.setServerUrl(serverUrl);
        }

        if (resolved.getPort() == null || resolved.getPort() <= 0) {
            int fallback = Boolean.TRUE.equals(resolved.getUseWeborca()) ? DEFAULT_PORT_WEBORCA : DEFAULT_PORT_ONPREM;
            URI uri = tryParseUri(serverUrl);
            if (uri != null && uri.getPort() > 0) {
                resolved.setPort(uri.getPort());
            } else {
                resolved.setPort(fallback);
            }
        }

        String username = trimToNull(resolved.getUsername());
        if (username != null) resolved.setUsername(username);

        resolved.setFacilityId(normalizeFacilityId(resolved.getFacilityId()));
        resolved.setRecords(null);

        if (resolved.getUpdatedAt() == null || resolved.getUpdatedAt().isBlank()) {
            resolved.setUpdatedAt(Instant.now().toString());
        }
        if (resolved.getVersion() <= 0) resolved.setVersion(1);
        return resolved;
    }

    private void validateReadyForUpdate(OrcaConnectionConfigRecord record) {
        if (record == null) {
            throw new IllegalArgumentException("設定が不正です。");
        }
        if (trimToNull(record.getServerUrl()) == null) {
            throw new IllegalArgumentException("サーバURLは必須です。");
        }
        if (record.getPort() == null || record.getPort() <= 0 || record.getPort() > 65535) {
            throw new IllegalArgumentException("ポート番号が不正です。");
        }
        if (trimToNull(record.getUsername()) == null) {
            throw new IllegalArgumentException("ユーザー名は必須です。");
        }
        String baseUrl = buildBaseUrl(record.getServerUrl(), record.getPort(), Boolean.TRUE.equals(record.getUseWeborca()));
        OrcaTransportSecurityPolicy.validateBaseUrl(baseUrl, Boolean.TRUE.equals(record.getUseWeborca()));
        // Password can be omitted only when it was previously configured.
        if (record.getPasswordEncrypted() == null || record.getPasswordEncrypted().isBlank()) {
            throw new IllegalArgumentException("パスワードまたはAPIキーは必須です。");
        }
        boolean clientAuthEnabled = Boolean.TRUE.equals(record.getClientAuthEnabled());
        if (clientAuthEnabled) {
            if (record.getClientCertificateP12Encrypted() == null || record.getClientCertificateP12Encrypted().isBlank()) {
                throw new IllegalArgumentException("クライアント証明書（.p12）は必須です。");
            }
            if (record.getClientCertificatePassphraseEncrypted() == null || record.getClientCertificatePassphraseEncrypted().isBlank()) {
                throw new IllegalArgumentException("クライアント証明書のパスフレーズは必須です。");
            }
        }
    }

    private void validateReady(OrcaConnectionConfigRecord record) {
        // Same as validateReadyForUpdate, but keep this method name for call-sites.
        validateReadyForUpdate(record);
    }

    private static Path resolveStoragePath() {
        Path base = RuntimeConfigurationSupport.resolveServerDataDirectoryOrThrow("OrcaConnectionConfigStore");
        try {
            Path dir = base.resolve(STORAGE_DIR);
            Files.createDirectories(dir);
            return dir.resolve(STORAGE_FILE);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to create ORCA config directory: " + base, ex);
        }
    }

    private static OrcaConnectionConfigRecord copy(OrcaConnectionConfigRecord record) {
        if (record == null) {
            return null;
        }
        OrcaConnectionConfigRecord copy = copyWithoutRecords(record);
        Map<String, OrcaConnectionConfigRecord> sourceRecords = record.getRecords();
        if (sourceRecords != null && !sourceRecords.isEmpty()) {
            Map<String, OrcaConnectionConfigRecord> copiedRecords = new LinkedHashMap<>();
            for (Map.Entry<String, OrcaConnectionConfigRecord> entry : sourceRecords.entrySet()) {
                if (entry == null || entry.getValue() == null) {
                    continue;
                }
                copiedRecords.put(entry.getKey(), copyWithoutRecords(entry.getValue()));
            }
            copy.setRecords(copiedRecords);
        }
        return copy;
    }

    private static OrcaConnectionConfigRecord copyWithoutRecords(OrcaConnectionConfigRecord record) {
        if (record == null) {
            return null;
        }
        OrcaConnectionConfigRecord copy = new OrcaConnectionConfigRecord();
        copyFlatFields(record, copy);
        copy.setFacilityId(normalizeFacilityId(record.getFacilityId()));
        copy.setRecords(null);
        return copy;
    }

    private static void copyFlatFields(OrcaConnectionConfigRecord from, OrcaConnectionConfigRecord to) {
        if (from == null || to == null) {
            return;
        }
        to.setVersion(from.getVersion());
        to.setUpdatedAt(from.getUpdatedAt());
        to.setUseWeborca(from.getUseWeborca());
        to.setServerUrl(from.getServerUrl());
        to.setPort(from.getPort());
        to.setUsername(from.getUsername());
        to.setPasswordEncrypted(from.getPasswordEncrypted());
        to.setPasswordUpdatedAt(from.getPasswordUpdatedAt());
        to.setClientAuthEnabled(from.getClientAuthEnabled());
        to.setClientCertificateFileName(from.getClientCertificateFileName());
        to.setClientCertificateUploadedAt(from.getClientCertificateUploadedAt());
        to.setClientCertificateP12Encrypted(from.getClientCertificateP12Encrypted());
        to.setClientCertificatePassphraseEncrypted(from.getClientCertificatePassphraseEncrypted());
        to.setClientCertificatePassphraseUpdatedAt(from.getClientCertificatePassphraseUpdatedAt());
        to.setCaCertificateFileName(from.getCaCertificateFileName());
        to.setCaCertificateUploadedAt(from.getCaCertificateUploadedAt());
        to.setCaCertificateEncrypted(from.getCaCertificateEncrypted());
    }

    private OrcaConnectionConfigRecord selectRecordForFacilityLocked(String facilityId) {
        String key = toRecordKey(facilityId);
        if (records != null) {
            OrcaConnectionConfigRecord scoped = records.get(key);
            if (scoped != null) {
                return scoped;
            }
            OrcaConnectionConfigRecord fallback = records.get(DEFAULT_FACILITY_RECORD_KEY);
            if (fallback != null) {
                return fallback;
            }
            if (!records.isEmpty()) {
                return records.values().iterator().next();
            }
        }
        return current;
    }

    private void refreshCurrentFromRecordsLocked() {
        OrcaConnectionConfigRecord fallback = null;
        if (records != null) {
            fallback = records.get(DEFAULT_FACILITY_RECORD_KEY);
            if (fallback == null && !records.isEmpty()) {
                fallback = copyWithoutRecords(records.values().iterator().next());
                fallback.setFacilityId(null);
                records.put(DEFAULT_FACILITY_RECORD_KEY, fallback);
            }
        }
        current = copyWithoutRecords(fallback);
    }

    private String encryptText(String plainText) {
        if (plainText == null) {
            return null;
        }
        TotpSecretProtector p = requireProtector();
        return p.encrypt(plainText);
    }

    private String encryptBytes(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        String base64 = Base64.getEncoder().encodeToString(bytes);
        return encryptText(base64);
    }

    private String decryptToText(String cipherText, String field) {
        if (cipherText == null || cipherText.isBlank()) {
            throw new IllegalStateException(field + " is missing");
        }
        TotpSecretProtector p = requireProtector();
        return p.decrypt(cipherText);
    }

    private byte[] decryptToBytes(String cipherText, String field) {
        String base64 = decryptToText(cipherText, field);
        try {
            return Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Failed to decode decrypted " + field + " as base64", ex);
        }
    }

    private TotpSecretProtector requireProtector() {
        if (protector == null) {
            throw new IllegalStateException("TotpSecretProtector is not available");
        }
        return protector;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static Integer parsePort(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static URI tryParseUri(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return null;
        }
        try {
            String trimmed = baseUrl.trim();
            if (!trimmed.contains("://")) {
                // URI requires scheme.
                return null;
            }
            return URI.create(trimmed);
        } catch (Exception ex) {
            return null;
        }
    }

    private boolean resolveUseWeborca() {
        String mode = env(ENV_ORCA_MODE);
        if (mode != null && !mode.isBlank()) {
            String normalized = mode.trim().toLowerCase(Locale.ROOT);
            return "weborca".equals(normalized) || "cloud".equals(normalized);
        }
        String explicit = env(ENV_ORCA_API_WEBORCA);
        if (explicit == null) {
            return false;
        }
        String normalized = explicit.trim().toLowerCase(Locale.ROOT);
        return "1".equals(normalized) || "true".equals(normalized) || "yes".equals(normalized) || "on".equals(normalized);
    }

    private String env(String key) {
        return key != null ? System.getenv(key) : null;
    }

    private static String buildBaseUrl(String serverUrl, Integer port, boolean useWeborca) {
        String normalized = trimToNull(serverUrl);
        if (normalized == null) {
            throw new IllegalArgumentException("serverUrl is required");
        }
        String withScheme = normalized.contains("://")
                ? normalized
                : (useWeborca ? "https://" : "http://") + normalized;
        URI uri;
        try {
            uri = URI.create(withScheme);
        } catch (Exception ex) {
            throw new IllegalArgumentException("サーバURLが不正です。", ex);
        }
        String scheme = uri.getScheme() != null ? uri.getScheme() : "https";
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            // URI parsing can treat raw "host:port" without scheme incorrectly; try fallback.
            host = uri.getAuthority();
        }
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("サーバURLが不正です。");
        }
        int resolvedPort = port != null && port > 0 ? port : uri.getPort();
        String path = uri.getRawPath();
        StringBuilder builder = new StringBuilder();
        builder.append(scheme).append("://").append(host);
        if (resolvedPort > 0 && !isDefaultPort(scheme, resolvedPort)) {
            builder.append(":").append(resolvedPort);
        }
        if (path != null && !path.isBlank() && !"/".equals(path)) {
            // Keep base path if user provided one (e.g. reverse-proxy prefix or /api).
            if (!path.startsWith("/")) {
                builder.append("/");
            }
            builder.append(trimTrailingSlash(path));
        }
        return builder.toString();
    }

    private static String trimTrailingSlash(String path) {
        if (path == null) {
            return null;
        }
        String resolved = path;
        while (resolved.endsWith("/") && resolved.length() > 1) {
            resolved = resolved.substring(0, resolved.length() - 1);
        }
        return resolved;
    }

    private static boolean isDefaultPort(String scheme, int port) {
        if (scheme == null) {
            return false;
        }
        String normalized = scheme.toLowerCase(Locale.ROOT);
        return ("https".equals(normalized) && port == 443) || ("http".equals(normalized) && port == 80);
    }

    private static void requireMaxBytes(long actual, long limit, String field) {
        if (limit <= 0) {
            return;
        }
        if (actual > limit) {
            throw new IllegalArgumentException(field + " が大きすぎます。最大 " + limit + " bytes までです。");
        }
    }

    private static boolean hasConnectionPayload(OrcaConnectionConfigRecord record) {
        if (record == null) {
            return false;
        }
        return trimToNull(record.getServerUrl()) != null
                || record.getPort() != null
                || trimToNull(record.getUsername()) != null
                || (record.getPasswordEncrypted() != null && !record.getPasswordEncrypted().isBlank())
                || record.getUseWeborca() != null
                || record.getClientAuthEnabled() != null
                || (record.getClientCertificateP12Encrypted() != null && !record.getClientCertificateP12Encrypted().isBlank())
                || (record.getCaCertificateEncrypted() != null && !record.getCaCertificateEncrypted().isBlank());
    }

    private static String normalizeFacilityId(String facilityId) {
        return trimToNull(facilityId);
    }

    private static String toRecordKey(String facilityId) {
        String normalized = normalizeFacilityId(facilityId);
        return normalized != null ? normalized : DEFAULT_FACILITY_RECORD_KEY;
    }

    private static String fromRecordKey(String key) {
        String normalized = normalizeFacilityId(key);
        if (normalized == null || DEFAULT_FACILITY_RECORD_KEY.equals(normalized)) {
            return null;
        }
        return normalized;
    }

    private static String safe(String value) {
        return value != null ? value : "";
    }

    private static String maskActor(String actor) {
        if (actor == null || actor.isBlank()) {
            return "unknown";
        }
        String trimmed = actor.trim();
        if (trimmed.length() <= 4) {
            return "***";
        }
        return trimmed.substring(0, 2) + "***" + trimmed.substring(trimmed.length() - 2);
    }

    public record UpdateRequest(
            Boolean useWeborca,
            String serverUrl,
            Integer port,
            String username,
            String password,
            Boolean clientAuthEnabled,
            String clientCertificatePassphrase
    ) {
    }

    public static final class UploadedBinary {
        private final String fileName;
        private final byte[] bytes;

        public UploadedBinary(String fileName, byte[] bytes) {
            this.fileName = fileName;
            this.bytes = bytes;
        }
    }

    public record ResolvedOrcaConnection(
            boolean useWeborca,
            String baseUrl,
            String username,
            String password,
            boolean clientAuthEnabled,
            byte[] clientCertificateP12,
            String clientCertificatePassphrase,
            byte[] caCertificate
    ) {
    }
}
