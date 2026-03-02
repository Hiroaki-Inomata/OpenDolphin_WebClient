package open.dolphin.security.integrity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import jakarta.ws.rs.WebApplicationException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import open.dolphin.infomodel.AttachmentModel;
import open.dolphin.infomodel.DocInfoModel;
import open.dolphin.infomodel.DocumentModel;
import open.dolphin.infomodel.ExtRefModel;
import open.dolphin.infomodel.KarteBean;
import open.dolphin.infomodel.ModuleModel;
import open.dolphin.infomodel.PatientModel;
import open.dolphin.infomodel.SchemaModel;
import open.dolphin.infomodel.UserModel;
import open.dolphin.security.audit.SessionAuditDispatcher;
import open.dolphin.session.framework.SessionTraceManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DocumentIntegrityServiceTest {

    private static final String MODE_KEY = "DOCUMENT_INTEGRITY_MODE";
    private static final String HMAC_KEY_B64_KEY = "DOCUMENT_INTEGRITY_HMAC_KEY_B64";
    private static final String KEY_ID_KEY = "DOCUMENT_INTEGRITY_KEY_ID";
    private static final String HMAC_KEY_B64 = Base64.getEncoder()
            .encodeToString("01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8));

    private DocumentIntegrityService service;
    private EntityManager em;

    @BeforeEach
    void setUp() throws Exception {
        System.setProperty(MODE_KEY, "enforce");
        System.setProperty(HMAC_KEY_B64_KEY, HMAC_KEY_B64);
        System.setProperty(KEY_ID_KEY, "v1");

        service = new DocumentIntegrityService();
        em = mock(EntityManager.class);
        setField(service, "em", em);
        setField(service, "config", new DocumentIntegrityConfig());
        setField(service, "sessionAuditDispatcher", mock(SessionAuditDispatcher.class));
        setField(service, "sessionTraceManager", mock(SessionTraceManager.class));
    }

    @AfterEach
    void tearDown() {
        System.clearProperty(MODE_KEY);
        System.clearProperty(HMAC_KEY_B64_KEY);
        System.clearProperty(KEY_ID_KEY);
    }

    @Test
    void canonicalBytes_areStable_whenCollectionOrderDiffers() throws Exception {
        DocumentModel ordered = buildDocument(false);
        DocumentModel reversed = buildDocument(true);

        byte[] left = invokeCanonicalBytes(service, ordered);
        byte[] right = invokeCanonicalBytes(service, reversed);

        assertThat(left).isEqualTo(right);
    }

    @Test
    void verify_fails_onOneByteTamper() throws Exception {
        DocumentModel original = buildDocument(false);
        DocumentIntegrityEntity stored = buildStoredSeal(service, original);
        when(em.find(DocumentIntegrityEntity.class, original.getId())).thenReturn(stored);

        assertThatCode(() -> service.verifyDocumentOnRead(original))
                .doesNotThrowAnyException();

        DocumentModel tampered = buildDocument(false);
        tampered.getModules().get(0).getBeanBytes()[0] ^= 0x01;

        assertThatThrownBy(() -> service.verifyDocumentOnRead(tampered))
                .isInstanceOf(WebApplicationException.class)
                .satisfies(throwable -> {
                    WebApplicationException ex = (WebApplicationException) throwable;
                    assertThat(ex.getResponse().getStatus()).isEqualTo(409);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> body = (Map<String, Object>) ex.getResponse().getEntity();
                    assertThat(body.get("errorCode")).isEqualTo("document_integrity_conflict");
                });
    }

    private static DocumentIntegrityEntity buildStoredSeal(DocumentIntegrityService service,
                                                           DocumentModel document) throws Exception {
        byte[] canonicalBytes = invokeCanonicalBytes(service, document);
        String contentHash = invokeSha256Hex(service, canonicalBytes);
        byte[] hmacKey = Base64.getDecoder().decode(HMAC_KEY_B64);
        String seal = invokeHmacSha256Hex(service, hmacKey, contentHash);

        DocumentIntegrityEntity entity = new DocumentIntegrityEntity();
        entity.setDocumentId(document.getId());
        entity.setSealVersion("v1");
        entity.setHashAlg("SHA-256");
        entity.setContentHash(contentHash);
        entity.setSealAlg("HMAC-SHA256");
        entity.setSeal(seal);
        entity.setKeyId("v1");
        entity.setSealedAt(Instant.now());
        entity.setCreatedAt(Instant.now());
        entity.setSealedBy("test-user");
        return entity;
    }

    private static byte[] invokeCanonicalBytes(DocumentIntegrityService service, DocumentModel document)
            throws Exception {
        Method method = DocumentIntegrityService.class.getDeclaredMethod("canonicalBytes", DocumentModel.class);
        method.setAccessible(true);
        return (byte[]) method.invoke(service, document);
    }

    private static String invokeSha256Hex(DocumentIntegrityService service, byte[] value) throws Exception {
        Method method = DocumentIntegrityService.class.getDeclaredMethod("sha256Hex", byte[].class);
        method.setAccessible(true);
        return (String) method.invoke(service, (Object) value);
    }

    private static String invokeHmacSha256Hex(DocumentIntegrityService service, byte[] key, String value)
            throws Exception {
        Method method = DocumentIntegrityService.class.getDeclaredMethod("hmacSha256Hex", byte[].class, String.class);
        method.setAccessible(true);
        return (String) method.invoke(service, key, value);
    }

    private static DocumentModel buildDocument(boolean reverseOrder) {
        Date now = Date.from(Instant.parse("2026-03-02T00:00:00Z"));

        DocumentModel document = new DocumentModel();
        document.setId(100L);
        document.setStarted(now);
        document.setConfirmed(now);
        document.setRecorded(now);
        document.setStatus("T");

        DocInfoModel docInfo = new DocInfoModel();
        docInfo.setDocId("DOC-100");
        docInfo.setDocType("KARTE");
        document.setDocInfoModel(docInfo);

        KarteBean karte = new KarteBean();
        karte.setId(200L);
        PatientModel patient = new PatientModel();
        patient.setPatientId("P-001");
        karte.setPatientModel(patient);
        document.setKarteBean(karte);

        UserModel creator = new UserModel();
        creator.setUserId("fid:test-user");
        document.setUserModel(creator);

        ModuleModel module1 = new ModuleModel();
        module1.setId(10L);
        module1.getModuleInfoBean().setEntity("medOrder");
        module1.setBeanBytes(new byte[]{0x01, 0x02, 0x03});

        ModuleModel module2 = new ModuleModel();
        module2.setId(20L);
        module2.getModuleInfoBean().setEntity("progressCourse");
        module2.setBeanJson("{\"text\":\"SOAP\"}");

        List<ModuleModel> modules = reverseOrder ? List.of(module2, module1) : List.of(module1, module2);
        document.setModules(modules);

        SchemaModel schema1 = new SchemaModel();
        schema1.setId(11L);
        ExtRefModel ext1 = new ExtRefModel();
        ext1.setHref("schema://1");
        schema1.setExtRefModel(ext1);
        schema1.setJpegByte(new byte[]{0x0A, 0x0B, 0x0C});

        SchemaModel schema2 = new SchemaModel();
        schema2.setId(12L);
        ExtRefModel ext2 = new ExtRefModel();
        ext2.setHref("schema://2");
        schema2.setExtRefModel(ext2);
        schema2.setJpegByte(new byte[]{0x1A, 0x1B, 0x1C});

        List<SchemaModel> schemas = reverseOrder ? List.of(schema2, schema1) : List.of(schema1, schema2);
        document.setSchema(schemas);

        AttachmentModel attachment1 = new AttachmentModel();
        attachment1.setId(21L);
        attachment1.setFileName("a.txt");
        attachment1.setContentType("text/plain");
        attachment1.setContentSize(3L);
        attachment1.setUri("file://a");
        attachment1.setBytes(new byte[]{0x21, 0x22, 0x23});

        AttachmentModel attachment2 = new AttachmentModel();
        attachment2.setId(22L);
        attachment2.setFileName("b.txt");
        attachment2.setContentType("text/plain");
        attachment2.setContentSize(4L);
        attachment2.setUri("file://b");
        attachment2.setBytes(new byte[]{0x31, 0x32, 0x33, 0x34});

        List<AttachmentModel> attachments = reverseOrder ? List.of(attachment2, attachment1)
                : List.of(attachment1, attachment2);
        document.setAttachment(attachments);

        return document;
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
