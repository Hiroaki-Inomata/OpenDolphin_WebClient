package open.dolphin.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import open.dolphin.infomodel.AttachmentModel;
import open.dolphin.infomodel.BundleDolphin;
import open.dolphin.infomodel.ClaimItem;
import open.dolphin.infomodel.DocInfoModel;
import open.dolphin.infomodel.DocumentModel;
import open.dolphin.infomodel.IInfoModel;
import open.dolphin.infomodel.ModuleModel;
import open.dolphin.storage.attachment.AttachmentStorageManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.doAnswer;

/**
 * addDocument/updateDocument の PK 正数化と docPk 同期を検証する簡易テスト。
 */
class KarteServiceBeanDocPkTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private KarteServiceBean service;
    private EntityManager em;
    private AttachmentStorageManager attachmentStorageManager;
    private Query seqQuery;

    @BeforeEach
    void setUp() throws Exception {
        service = new KarteServiceBean();
        em = mock(EntityManager.class);
        attachmentStorageManager = mock(AttachmentStorageManager.class);
        seqQuery = mock(Query.class);

        setField(service, "em", em);
        setField(service, "attachmentStorageManager", attachmentStorageManager);
    }

    @Test
    void addDocument_assignsPositivePk_andSyncsDocInfo() {
        when(em.createNativeQuery("SELECT nextval('opendolphin.hibernate_sequence')")).thenReturn(seqQuery);
        when(seqQuery.getSingleResult()).thenReturn(100L);
        when(em.merge(any(DocumentModel.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DocumentModel document = buildDocumentWithModule();
        document.setId(-5L);

        long result = service.addDocument(document);

        assertThat(result).isEqualTo(100L);
        assertThat(document.getDocInfoModel().getDocPk()).isEqualTo(100L);
        verify(em).createNativeQuery("SELECT nextval('opendolphin.hibernate_sequence')");
    }

    @Test
    void addThenUpdate_roundTripsWithPositivePk() {
        when(em.createNativeQuery("SELECT nextval('opendolphin.hibernate_sequence')")).thenReturn(seqQuery);
        when(seqQuery.getSingleResult()).thenReturn(200L);
        when(em.merge(any(DocumentModel.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DocumentModel current = buildDocumentWithModule();
        current.setId(200L);
        current.setStatus(IInfoModel.STATUS_TMP);
        when(em.find(DocumentModel.class, 200L)).thenReturn(current);

        DocumentModel incoming = buildDocumentWithModule();
        incoming.setId(-1L); // will be overwritten by addDocument

        long added = service.addDocument(incoming);
        assertThat(added).isEqualTo(200L);

        // simulate client re-using returned PK
        incoming.setId(added);
        incoming.setStatus(IInfoModel.STATUS_TMP);
        long updated = service.updateDocument(incoming);

        assertThat(updated).isEqualTo(200L);

        ArgumentCaptor<DocumentModel> mergeCaptor = ArgumentCaptor.forClass(DocumentModel.class);
        verify(em, times(2)).merge(mergeCaptor.capture());
        List<DocumentModel> merged = mergeCaptor.getAllValues();
        assertThat(merged).hasSize(2);
        assertThat(merged.get(merged.size() - 1).getId()).isEqualTo(200L);
    }

    @Test
    void addDocument_externalizesAttachmentsAfterAssigningIdsAndBeforeMerge() {
        when(em.createNativeQuery("SELECT nextval('opendolphin.hibernate_sequence')")).thenReturn(seqQuery);
        when(seqQuery.getSingleResult()).thenReturn(400L, 401L);
        when(em.merge(any(DocumentModel.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DocumentModel document = buildDocumentWithAttachment();
        document.setId(0L);
        AttachmentModel attachment = document.getAttachment().get(0);

        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Collection<AttachmentModel> attachments = invocation.getArgument(0, Collection.class);
            AttachmentModel uploaded = attachments.iterator().next();
            assertThat(uploaded.getId()).isEqualTo(401L);
            assertThat(uploaded.getDocumentModel().getId()).isEqualTo(400L);
            uploaded.setDigest("digest-401");
            uploaded.setUri("s3://bucket/doc-400/att-401.txt");
            uploaded.setBytes(null);
            return null;
        }).when(attachmentStorageManager).persistExternalAssets(any());

        long result = service.addDocument(document);

        assertThat(result).isEqualTo(400L);
        assertThat(document.getDocInfoModel().getDocPk()).isEqualTo(400L);
        assertThat(attachment.getId()).isEqualTo(401L);
        assertThat(attachment.getBytes()).isNull();
        assertThat(attachment.getDigest()).isEqualTo("digest-401");
        assertThat(attachment.getUri()).isEqualTo("s3://bucket/doc-400/att-401.txt");

        ArgumentCaptor<DocumentModel> mergeCaptor = ArgumentCaptor.forClass(DocumentModel.class);
        verify(em).merge(mergeCaptor.capture());
        AttachmentModel mergedAttachment = mergeCaptor.getValue().getAttachment().get(0);
        assertThat(mergedAttachment.getBytes()).isNull();
        assertThat(mergedAttachment.getDigest()).isEqualTo("digest-401");
        assertThat(mergedAttachment.getUri()).isEqualTo("s3://bucket/doc-400/att-401.txt");
    }

    @Test
    void updateDocument_rejectsFinalizedDocumentWithConflictPayload() {
        DocumentModel current = buildDocumentWithModule();
        current.setId(300L);
        current.setStatus(IInfoModel.STATUS_FINAL);

        DocumentModel incoming = buildDocumentWithModule();
        incoming.setId(300L);
        incoming.setStatus(IInfoModel.STATUS_TMP);

        when(em.find(DocumentModel.class, 300L)).thenReturn(current);

        Throwable thrown = catchThrowable(() -> service.updateDocument(incoming));
        assertThat(thrown).isNotNull();

        ProblemSnapshot problem = extractProblem(thrown);
        assertThat(problem.status()).isEqualTo(409);
        assertThat(problem.errorCode()).isEqualTo("karte.document.finalized_update_denied");
        assertThat(asLong(problem.details().get("documentId"))).isEqualTo(300L);
        assertThat(String.valueOf(problem.details().get("currentStatus"))).isEqualTo(IInfoModel.STATUS_FINAL);
        assertThat(String.valueOf(problem.details().get("requestedStatus"))).isEqualTo(IInfoModel.STATUS_TMP);
    }

    private static DocumentModel buildDocumentWithModule() {
        DocumentModel document = new DocumentModel();
        DocInfoModel info = new DocInfoModel();
        info.setDocId("TESTDOC");
        document.setDocInfoModel(info);
        document.setStatus(IInfoModel.STATUS_TMP);

        ModuleModel module = new ModuleModel();
        BundleDolphin bundle = new BundleDolphin();
        bundle.setClassCode("212");
        ClaimItem item = new ClaimItem();
        item.setCode("100001");
        item.setName("テスト薬剤");
        item.setNumber("1");
        item.setUnit("錠");
        bundle.setClaimItem(new ClaimItem[]{item});
        module.setModel(bundle);

        document.setModules(List.of(module));
        return document;
    }

    private static DocumentModel buildDocumentWithAttachment() {
        DocumentModel document = buildDocumentWithModule();
        AttachmentModel attachment = new AttachmentModel();
        attachment.setFileName("report.txt");
        attachment.setContentType("text/plain");
        attachment.setBytes(new byte[]{1, 2, 3});
        attachment.setDocumentModel(document);
        document.setAttachment(List.of(attachment));
        return document;
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    private static ProblemSnapshot extractProblem(Throwable thrown) {
        int status = extractStatus(thrown);
        String errorCode = extractErrorCode(thrown);
        Map<String, Object> details = extractDetails(thrown);
        return new ProblemSnapshot(status, errorCode, details);
    }

    private static int extractStatus(Throwable thrown) {
        if (thrown instanceof WebApplicationException webEx && webEx.getResponse() != null) {
            return webEx.getResponse().getStatus();
        }
        Object statusCode = invokeNoArgIfPresent(thrown, "getStatusCode");
        if (statusCode instanceof Number number) {
            return number.intValue();
        }
        Object status = invokeNoArgIfPresent(thrown, "getStatus");
        if (status instanceof Number number) {
            return number.intValue();
        }
        Response response = extractResponse(thrown);
        return response != null ? response.getStatus() : -1;
    }

    private static String extractErrorCode(Throwable thrown) {
        Object direct = invokeNoArgIfPresent(thrown, "getErrorCode");
        if (direct instanceof String str && !str.isBlank()) {
            return str;
        }
        Map<String, Object> body = extractResponseBodyMap(thrown);
        if (body.isEmpty()) {
            return null;
        }
        for (String key : List.of("errorCode", "code", "error")) {
            Object value = body.get(key);
            if (value instanceof String str && !str.isBlank()) {
                return str;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> extractDetails(Throwable thrown) {
        Object direct = invokeNoArgIfPresent(thrown, "getDetails");
        if (direct instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }

        Map<String, Object> body = extractResponseBodyMap(thrown);
        if (body.isEmpty()) {
            return Collections.emptyMap();
        }

        Object nested = body.get("details");
        if (nested instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }

        if (body.containsKey("documentId") || body.containsKey("currentStatus") || body.containsKey("requestedStatus")) {
            return body;
        }
        return Collections.emptyMap();
    }

    private static Response extractResponse(Throwable thrown) {
        if (thrown instanceof WebApplicationException webEx) {
            return webEx.getResponse();
        }
        Object response = invokeNoArgIfPresent(thrown, "getResponse");
        if (response instanceof Response res) {
            return res;
        }
        return null;
    }

    private static Map<String, Object> extractResponseBodyMap(Throwable thrown) {
        Response response = extractResponse(thrown);
        if (response == null || !response.hasEntity()) {
            return Collections.emptyMap();
        }
        Object entity = response.getEntity();
        if (entity instanceof Map<?, ?> map) {
            return toStringKeyMap(map);
        }
        if (entity instanceof String text && !text.isBlank()) {
            try {
                return OBJECT_MAPPER.readValue(text, new TypeReference<Map<String, Object>>() {});
            } catch (Exception ignore) {
                return Collections.emptyMap();
            }
        }
        return Collections.emptyMap();
    }

    private static Map<String, Object> toStringKeyMap(Map<?, ?> source) {
        java.util.LinkedHashMap<String, Object> converted = new java.util.LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            converted.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return converted;
    }

    private static Object invokeNoArgIfPresent(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (NoSuchMethodException ignored) {
            return null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Long.parseLong(text);
        }
        throw new IllegalArgumentException("Cannot convert to long: " + value);
    }

    private record ProblemSnapshot(int status, String errorCode, Map<String, Object> details) {}
}
