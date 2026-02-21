package open.dolphin.orca.sync;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import open.dolphin.runtime.RuntimeConfigurationSupport;

/**
 * Persists ORCA patient sync cursor (last successful date) on local filesystem.
 */
@ApplicationScoped
public class OrcaPatientSyncStateStore {

    private static final Logger LOGGER = Logger.getLogger(OrcaPatientSyncStateStore.class.getName());
    private static final String ENV_STATE_PATH = "ORCA_PATIENT_SYNC_STATE_PATH";

    private static final ObjectMapper JSON = new ObjectMapper()
            .configure(SerializationFeature.INDENT_OUTPUT, true)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private final ReentrantLock lock = new ReentrantLock();

    public Path resolvePath() {
        String configured = System.getenv(ENV_STATE_PATH);
        if (configured != null && !configured.isBlank()) {
            return Paths.get(configured.trim()).toAbsolutePath();
        }
        Path dataRoot = RuntimeConfigurationSupport.resolveServerDataDirectoryOrThrow("OrcaPatientSyncStateStore");
        return dataRoot.resolve("opendolphin").resolve("orca").resolve("patient-sync-state.json");
    }

    public FacilityState loadFacilityState(String facilityId) {
        if (facilityId == null || facilityId.isBlank()) {
            return null;
        }
        lock.lock();
        try {
            RootState root = loadRootState();
            return root.facilities.get(facilityId.trim());
        } finally {
            lock.unlock();
        }
    }

    public void markSuccess(String facilityId, LocalDate endDate, String runId) {
        if (facilityId == null || facilityId.isBlank() || endDate == null) {
            return;
        }
        lock.lock();
        try {
            RootState root = loadRootState();
            FacilityState state = root.facilities.computeIfAbsent(facilityId.trim(), (k) -> new FacilityState());
            state.lastSyncDate = endDate.toString();
            state.lastSyncedAt = Instant.now().toString();
            state.lastRunId = runId;
            state.lastError = null;
            saveRootState(root);
        } finally {
            lock.unlock();
        }
    }

    public void markFailure(String facilityId, String error, String runId) {
        if (facilityId == null || facilityId.isBlank()) {
            return;
        }
        lock.lock();
        try {
            RootState root = loadRootState();
            FacilityState state = root.facilities.computeIfAbsent(facilityId.trim(), (k) -> new FacilityState());
            state.lastSyncedAt = Instant.now().toString();
            state.lastRunId = runId;
            state.lastError = error;
            saveRootState(root);
        } finally {
            lock.unlock();
        }
    }

    private RootState loadRootState() {
        Path path = resolvePath();
        if (!Files.exists(path)) {
            return new RootState();
        }
        try {
            byte[] bytes = Files.readAllBytes(path);
            if (bytes.length == 0) {
                return new RootState();
            }
            RootState parsed = JSON.readValue(bytes, RootState.class);
            if (parsed == null) {
                return new RootState();
            }
            if (parsed.facilities == null) {
                parsed.facilities = new LinkedHashMap<>();
            }
            return parsed;
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "Failed to read ORCA patient sync state. path={0} err={1}",
                    new Object[]{path, ex.getMessage()});
            return new RootState();
        }
    }

    private void saveRootState(RootState root) {
        Path path = resolvePath();
        try {
            Files.createDirectories(path.getParent());
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to create state directory: " + path.getParent(), ex);
        }
        String json;
        try {
            json = JSON.writeValueAsString(root);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to serialize state", ex);
        }
        Path tmp = path.resolveSibling(path.getFileName().toString() + ".tmp");
        try {
            Files.writeString(tmp, json, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to persist state: " + path, ex);
        }
    }

    public static class RootState {
        public int version = 1;
        public Map<String, FacilityState> facilities = new LinkedHashMap<>();
    }

    public static class FacilityState {
        public String lastSyncDate;
        public String lastSyncedAt;
        public String lastRunId;
        public String lastError;
    }
}
