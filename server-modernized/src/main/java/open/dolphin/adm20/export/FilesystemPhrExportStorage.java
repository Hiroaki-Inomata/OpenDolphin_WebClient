package open.dolphin.adm20.export;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import open.dolphin.infomodel.PHRAsyncJob;

@ApplicationScoped
public class FilesystemPhrExportStorage implements PhrExportStorage {

    private static final Logger LOGGER = Logger.getLogger(FilesystemPhrExportStorage.class.getName());

    @Inject
    private PhrExportConfig config;

    @Override
    public StorageResult storeArtifact(PHRAsyncJob job, InputStream data, long size, String contentType) throws IOException {
        if (job == null || job.getJobId() == null) {
            throw new IOException("PHR export job is missing.");
        }
        Path base = resolveBasePath();
        Files.createDirectories(base);
        Path target = base.resolve(job.getJobId().toString() + ".zip").normalize();
        Files.copy(data, target, StandardCopyOption.REPLACE_EXISTING);
        LOGGER.log(Level.FINE, "Stored PHR export artifact for jobId={0}", job.getJobId());
        return new StorageResult(target.getFileName().toString(), size);
    }

    @Override
    public StoredArtifact loadArtifact(PHRAsyncJob job, String location) throws IOException {
        Path base = resolveBasePath();
        Path target = resolveArtifactPath(base, location);
        if (!Files.exists(target)) {
            throw new IOException("Export artifact not found.");
        }
        return new StoredArtifact(target, "application/zip");
    }

    private Path resolveBasePath() throws IOException {
        Path base = config != null ? config.getFilesystemBasePath() : null;
        if (base == null) {
            throw new IOException("PHR export filesystem base path is not configured.");
        }
        return base.toAbsolutePath().normalize();
    }

    private Path resolveArtifactPath(Path base, String location) throws IOException {
        Objects.requireNonNull(base, "base");
        if (location == null || location.isBlank()) {
            throw new IOException("PHR export artifact location is missing.");
        }
        Path candidate;
        try {
            candidate = Path.of(location).normalize();
        } catch (RuntimeException ex) {
            throw new IOException("Invalid artifact location.", ex);
        }
        if (candidate.isAbsolute()) {
            throw new IOException("Absolute artifact path is not allowed.");
        }
        Path target = base.resolve(candidate).normalize();
        if (!target.startsWith(base)) {
            throw new IOException("Artifact path escapes base directory.");
        }
        return target;
    }
}
