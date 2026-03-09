package open.dolphin.storage.attachment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import open.dolphin.infomodel.AttachmentModel;
import open.dolphin.infomodel.DocumentModel;
import open.dolphin.infomodel.KarteBean;
import open.dolphin.infomodel.PatientModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

class AttachmentStorageManagerTest {

    private AttachmentStorageManager manager;
    private S3Client s3Client;

    @BeforeEach
    void setUp() throws Exception {
        manager = new AttachmentStorageManager();
        s3Client = mock(S3Client.class);

        AttachmentStorageSettings.S3Settings s3Settings = new AttachmentStorageSettings.S3Settings(
                "test-bucket",
                "ap-northeast-1",
                URI.create("https://example.invalid"),
                "attachments",
                true,
                null,
                null,
                5,
                "access",
                "secret");
        AttachmentStorageSettings settings = new AttachmentStorageSettings(
                AttachmentStorageMode.S3,
                new AttachmentStorageSettings.DatabaseSettings(null),
                s3Settings,
                null);

        setField(manager, "settings", settings);
        setField(manager, "keyResolver", new AttachmentKeyResolver(s3Settings));
        setField(manager, "s3Client", s3Client);
    }

    @Test
    void uploadToS3OutsideTransaction_setsUriDigestAndClearsBytes() {
        AttachmentModel attachment = buildAttachment("report.txt", "payload".getBytes(StandardCharsets.UTF_8));

        boolean uploaded = manager.uploadToS3OutsideTransaction(attachment);

        assertThat(uploaded).isTrue();
        assertThat(attachment.getUri()).isEqualTo("s3://test-bucket/attachments/doc-20/att-10-report.txt");
        assertThat(attachment.getDigest()).isEqualTo(sha256Hex("payload".getBytes(StandardCharsets.UTF_8)));
        assertThat(attachment.getBytes()).isNull();
        verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void uploadToS3OutsideTransaction_isIdempotentWithoutTransientLocation() {
        AttachmentModel attachment = buildAttachment("report.txt", null);
        attachment.setUri("s3://test-bucket/attachments/doc-20/att-10-report.txt");
        attachment.setDigest(sha256Hex("payload".getBytes(StandardCharsets.UTF_8)));
        attachment.setLocation(null);

        boolean uploaded = manager.uploadToS3OutsideTransaction(attachment);

        assertThat(uploaded).isFalse();
        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void populateBinary_downloadsFromUriWhenBytesAreExternalized() {
        byte[] payload = "from-s3".getBytes(StandardCharsets.UTF_8);
        AttachmentModel attachment = buildAttachment("report.txt", null);
        attachment.setUri("s3://test-bucket/attachments/doc-20/att-10-report.txt");
        when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(new ResponseInputStream<>(
                GetObjectResponse.builder().build(),
                AbortableInputStream.create(new ByteArrayInputStream(payload))));

        manager.populateBinary(attachment);

        assertThat(attachment.getBytes()).containsExactly(payload);
        verify(s3Client).getObject(any(GetObjectRequest.class));
    }

    @Test
    void populateBinary_rejectsAttachmentWithoutBytesAndUri() {
        AttachmentModel attachment = buildAttachment("report.txt", null);

        assertThatThrownBy(() -> manager.populateBinary(attachment))
                .isInstanceOf(AttachmentStorageException.class)
                .hasMessageContaining("neither inline bytes nor external uri");
    }

    private static AttachmentModel buildAttachment(String fileName, byte[] bytes) {
        AttachmentModel attachment = new AttachmentModel();
        attachment.setId(10L);
        attachment.setFileName(fileName);
        attachment.setContentType("text/plain");
        attachment.setBytes(bytes);

        DocumentModel document = new DocumentModel();
        document.setId(20L);
        KarteBean karte = new KarteBean();
        karte.setId(30L);
        PatientModel patient = new PatientModel();
        patient.setFacilityId("F001");
        patient.setPatientId("P001");
        karte.setPatientModel(patient);
        document.setKarteBean(karte);
        attachment.setDocumentModel(document);
        return attachment;
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static String sha256Hex(byte[] value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
