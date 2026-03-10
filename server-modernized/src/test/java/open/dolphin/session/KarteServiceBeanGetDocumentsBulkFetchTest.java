package open.dolphin.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import open.dolphin.infomodel.AttachmentModel;
import open.dolphin.infomodel.BundleDolphin;
import open.dolphin.infomodel.DocumentModel;
import open.dolphin.infomodel.KarteBean;
import open.dolphin.infomodel.ModuleInfoBean;
import open.dolphin.infomodel.ModuleModel;
import open.dolphin.infomodel.SchemaModel;
import open.dolphin.infomodel.UserModel;
import open.dolphin.security.integrity.DocumentIntegrityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class KarteServiceBeanGetDocumentsBulkFetchTest {

    private static final String QUERY_DOCUMENT_BY_IDS =
            "select d from DocumentModel d left join fetch d.karte left join fetch d.creator where d.id in :ids";
    private static final String QUERY_MODULES_BY_DOC_IDS =
            "select m from ModuleModel m left join fetch m.karte left join fetch m.creator "
                    + "where m.document.id in :ids order by m.document.id, m.id";
    private static final String QUERY_SCHEMAS_BY_DOC_IDS =
            "select i from SchemaModel i left join fetch i.karte left join fetch i.creator "
                    + "where i.document.id in :ids order by i.document.id, i.id";
    private static final String QUERY_ATTACHMENTS_BY_DOC_IDS =
            "select a from AttachmentModel a left join fetch a.karte left join fetch a.creator "
                    + "where a.document.id in :ids order by a.document.id, a.id";

    private KarteServiceBean service;
    private EntityManager em;
    private DocumentIntegrityService documentIntegrityService;

    @BeforeEach
    void setUp() throws Exception {
        service = new KarteServiceBean();
        em = mock(EntityManager.class);
        documentIntegrityService = mock(DocumentIntegrityService.class);
        setField(service, "em", em);
        setField(service, "documentIntegrityService", documentIntegrityService);
    }

    @Test
    void getDocumentsUsesConstantQueriesAndPreservesRequestedOrder() {
        List<Long> requestedIds = new ArrayList<>(100);
        for (long id = 1; id <= 100; id++) {
            requestedIds.add(id);
        }

        List<DocumentModel> documents = new ArrayList<>(requestedIds.size());
        List<ModuleModel> modules = new ArrayList<>(requestedIds.size());
        List<SchemaModel> schemas = new ArrayList<>(requestedIds.size());
        List<AttachmentModel> attachments = new ArrayList<>(requestedIds.size());

        for (int index = requestedIds.size() - 1; index >= 0; index--) {
            long id = requestedIds.get(index);
            DocumentModel document = document(id);
            documents.add(document);
            modules.add(module(document, id));
            schemas.add(schema(document, id));
            attachments.add(attachment(document, id));
        }

        TypedQuery<DocumentModel> documentQuery = typedQuery(documents);
        TypedQuery<ModuleModel> moduleQuery = typedQuery(modules);
        TypedQuery<SchemaModel> schemaQuery = typedQuery(schemas);
        TypedQuery<AttachmentModel> attachmentQuery = typedQuery(attachments);

        when(em.createQuery(QUERY_DOCUMENT_BY_IDS, DocumentModel.class)).thenReturn(documentQuery);
        when(em.createQuery(QUERY_MODULES_BY_DOC_IDS, ModuleModel.class)).thenReturn(moduleQuery);
        when(em.createQuery(QUERY_SCHEMAS_BY_DOC_IDS, SchemaModel.class)).thenReturn(schemaQuery);
        when(em.createQuery(QUERY_ATTACHMENTS_BY_DOC_IDS, AttachmentModel.class)).thenReturn(attachmentQuery);

        List<DocumentModel> result = service.getDocuments(requestedIds);

        assertThat(result).hasSize(100);
        assertThat(result).extracting(DocumentModel::getId).containsExactlyElementsOf(requestedIds);
        assertThat(result.get(0).getModules()).hasSize(1);
        assertThat(result.get(0).getSchema()).hasSize(1);
        assertThat(result.get(0).getAttachment()).hasSize(1);
        assertThat(result.get(99).getModules()).hasSize(1);

        verify(em, times(1)).createQuery(QUERY_DOCUMENT_BY_IDS, DocumentModel.class);
        verify(em, times(1)).createQuery(QUERY_MODULES_BY_DOC_IDS, ModuleModel.class);
        verify(em, times(1)).createQuery(QUERY_SCHEMAS_BY_DOC_IDS, SchemaModel.class);
        verify(em, times(1)).createQuery(QUERY_ATTACHMENTS_BY_DOC_IDS, AttachmentModel.class);
        verify(documentQuery).setParameter("ids", requestedIds);
        verify(moduleQuery).setParameter("ids", requestedIds);
        verify(schemaQuery).setParameter("ids", requestedIds);
        verify(attachmentQuery).setParameter("ids", requestedIds);
        verify(documentIntegrityService, times(100)).verifyDocumentOnRead(any(DocumentModel.class));
    }

    private static DocumentModel document(long id) {
        DocumentModel document = new DocumentModel();
        document.setId(id);
        document.setKarteBean(karte(id));
        document.setUserModel(user(id));
        return document;
    }

    private static ModuleModel module(DocumentModel document, long id) {
        ModuleModel module = new ModuleModel();
        module.setId(id * 10);
        module.setDocumentModel(document);
        module.setKarteBean(document.getKarteBean());
        module.setUserModel(document.getUserModel());
        module.setModel(new BundleDolphin());
        ModuleInfoBean info = new ModuleInfoBean();
        info.setEntity("medOrder");
        info.setStampName("stamp-" + id);
        module.setModuleInfoBean(info);
        module.setBeanJson("{\"@class\":\"open.dolphin.infomodel.BundleDolphin\"}");
        return module;
    }

    private static SchemaModel schema(DocumentModel document, long id) {
        SchemaModel schema = new SchemaModel();
        schema.setId(id * 100);
        schema.setDocumentModel(document);
        schema.setKarteBean(document.getKarteBean());
        schema.setUserModel(document.getUserModel());
        return schema;
    }

    private static AttachmentModel attachment(DocumentModel document, long id) {
        AttachmentModel attachment = new AttachmentModel();
        attachment.setId(id * 1000);
        attachment.setDocumentModel(document);
        attachment.setKarteBean(document.getKarteBean());
        attachment.setUserModel(document.getUserModel());
        return attachment;
    }

    private static KarteBean karte(long id) {
        KarteBean karte = new KarteBean();
        karte.setId(id + 10_000);
        return karte;
    }

    private static UserModel user(long id) {
        UserModel user = new UserModel();
        user.setId(id + 20_000);
        return user;
    }

    @SuppressWarnings("unchecked")
    private static <T> TypedQuery<T> typedQuery(List<T> results) {
        TypedQuery<T> query = mock(TypedQuery.class);
        when(query.setParameter(eq("ids"), any())).thenReturn(query);
        when(query.getResultList()).thenReturn(new ArrayList<>(results));
        return query;
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
