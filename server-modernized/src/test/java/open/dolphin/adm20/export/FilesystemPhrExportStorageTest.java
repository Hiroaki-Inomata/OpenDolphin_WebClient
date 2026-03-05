package open.dolphin.adm20.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import open.dolphin.infomodel.PHRAsyncJob;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FilesystemPhrExportStorageTest {

    @InjectMocks
    private FilesystemPhrExportStorage storage;

    @Mock
    private PhrExportConfig config;

    @TempDir
    Path tempDir;

    @Test
    void storeAndLoadArtifact_withinBaseDirectory() throws Exception {
        when(config.getFilesystemBasePath()).thenReturn(tempDir);
        PHRAsyncJob job = new PHRAsyncJob();
        job.setJobId(UUID.randomUUID());

        byte[] content = "zip-content".getBytes();
        PhrExportStorage.StorageResult result = storage.storeArtifact(
                job,
                new ByteArrayInputStream(content),
                content.length,
                "application/zip");

        assertThat(result.getLocation()).isEqualTo(job.getJobId() + ".zip");
        PhrExportStorage.StoredArtifact artifact = storage.loadArtifact(job, result.getLocation());
        assertThat(artifact.getPath()).exists();
        assertThat(artifact.getPath().normalize().startsWith(tempDir.toAbsolutePath().normalize())).isTrue();
    }

    @Test
    void loadArtifact_rejectsTraversalLocation() throws Exception {
        when(config.getFilesystemBasePath()).thenReturn(tempDir);
        PHRAsyncJob job = new PHRAsyncJob();
        job.setJobId(UUID.randomUUID());

        assertThatThrownBy(() -> storage.loadArtifact(job, "../outside.zip"))
                .isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("escapes base directory");
    }

    @Test
    void loadArtifact_rejectsAbsoluteLocation() throws Exception {
        when(config.getFilesystemBasePath()).thenReturn(tempDir);
        PHRAsyncJob job = new PHRAsyncJob();
        job.setJobId(UUID.randomUUID());
        Path absolute = Files.createTempFile("phr-abs-", ".zip");
        absolute.toFile().deleteOnExit();

        assertThatThrownBy(() -> storage.loadArtifact(job, absolute.toString()))
                .isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("Absolute artifact path is not allowed");
    }

    @Test
    void storeArtifact_requiresJobId() throws Exception {
        PHRAsyncJob job = new PHRAsyncJob();
        byte[] content = "x".getBytes();

        assertThatThrownBy(() -> storage.storeArtifact(job, new ByteArrayInputStream(content), content.length, "application/zip"))
                .isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("job is missing");
    }
}
