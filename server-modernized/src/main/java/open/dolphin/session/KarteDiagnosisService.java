package open.dolphin.session;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import open.dolphin.infomodel.DiagnosisSendWrapper;
import open.dolphin.infomodel.RegisteredDiagnosisModel;
import open.dolphin.session.audit.DiagnosisAuditRecorder;

@ApplicationScoped
@Transactional
public class KarteDiagnosisService {

    private static final int WRITE_BATCH_SIZE = 50;
    private static final String KARTE_ID = "karteId";
    private static final String FROM_DATE = "fromDate";

    private static final String QUERY_DIAGNOSIS_BY_KARTE_DATE =
            "from RegisteredDiagnosisModel r where r.karte.id=:karteId and r.started >= :fromDate";
    private static final String QUERY_DIAGNOSIS_BY_KARTE_DATE_ACTIVEONLY =
            "from RegisteredDiagnosisModel r where r.karte.id=:karteId and r.started >= :fromDate and r.ended is NULL";
    private static final String QUERY_DIAGNOSIS_BY_KARTE =
            "from RegisteredDiagnosisModel r where r.karte.id=:karteId";
    private static final String QUERY_DIAGNOSIS_BY_KARTE_ACTIVEONLY =
            "from RegisteredDiagnosisModel r where r.karte.id=:karteId and r.ended is NULL";
    private static final String QUERY_DELETE_DIAGNOSIS_BY_IDS =
            "delete from RegisteredDiagnosisModel r where r.id in :ids";

    @PersistenceContext
    private EntityManager em;

    @jakarta.inject.Inject
    private DiagnosisAuditRecorder diagnosisAuditRecorder;

    public List<RegisteredDiagnosisModel> getDiagnosis(long karteId, Date fromDate, boolean activeOnly) {
        if (fromDate != null) {
            String query = activeOnly ? QUERY_DIAGNOSIS_BY_KARTE_DATE_ACTIVEONLY : QUERY_DIAGNOSIS_BY_KARTE_DATE;
            return em.createQuery(query, RegisteredDiagnosisModel.class)
                    .setParameter(KARTE_ID, karteId)
                    .setParameter(FROM_DATE, fromDate)
                    .getResultList();
        }

        String query = activeOnly ? QUERY_DIAGNOSIS_BY_KARTE_ACTIVEONLY : QUERY_DIAGNOSIS_BY_KARTE;
        return em.createQuery(query, RegisteredDiagnosisModel.class)
                .setParameter(KARTE_ID, karteId)
                .getResultList();
    }

    public List<Long> postPutSendDiagnosis(DiagnosisSendWrapper wrapper) {
        List<RegisteredDiagnosisModel> deletedList = wrapper.getDeletedDiagnosis();
        if (deletedList != null && !deletedList.isEmpty()) {
            bulkDeleteByIds(collectDiagnosisIds(deletedList));
        }

        List<RegisteredDiagnosisModel> updatedList = wrapper.getUpdatedDiagnosis();
        if (updatedList != null) {
            int processed = 0;
            for (RegisteredDiagnosisModel bean : updatedList) {
                em.merge(bean);
                processed++;
                flushAndClearIfNeeded(processed);
            }
        }

        List<RegisteredDiagnosisModel> addedList = wrapper.getAddedDiagnosis();
        List<Long> ret = new ArrayList<>(addedList != null ? addedList.size() : 0);
        if (addedList != null) {
            int processed = 0;
            for (RegisteredDiagnosisModel bean : addedList) {
                em.persist(bean);
                ret.add(bean.getId());
                processed++;
                flushAndClearIfNeeded(processed);
            }
        }

        diagnosisAuditRecorder.recordCreate(wrapper, addedList, ret);
        diagnosisAuditRecorder.recordUpdate(wrapper, updatedList);

        return ret;
    }

    public List<Long> addDiagnosis(List<RegisteredDiagnosisModel> addList) {
        if (addList == null || addList.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> ret = new ArrayList<>(addList.size());
        int processed = 0;
        for (RegisteredDiagnosisModel bean : addList) {
            em.persist(bean);
            ret.add(bean.getId());
            processed++;
            flushAndClearIfNeeded(processed);
        }
        return ret;
    }

    public int updateDiagnosis(List<RegisteredDiagnosisModel> updateList) {
        if (updateList == null || updateList.isEmpty()) {
            return 0;
        }

        int cnt = 0;
        for (RegisteredDiagnosisModel bean : updateList) {
            em.merge(bean);
            cnt++;
            flushAndClearIfNeeded(cnt);
        }

        return cnt;
    }

    public int removeDiagnosis(List<Long> removeList) {
        return bulkDeleteByIds(normalizeIds(removeList));
    }

    private List<Long> collectDiagnosisIds(List<RegisteredDiagnosisModel> deletedList) {
        if (deletedList == null || deletedList.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> ids = new ArrayList<>(deletedList.size());
        for (RegisteredDiagnosisModel bean : deletedList) {
            if (bean != null && bean.getId() > 0L) {
                ids.add(bean.getId());
            }
        }
        return ids;
    }

    private List<Long> normalizeIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> normalized = new ArrayList<>(ids.size());
        for (Long id : ids) {
            if (id != null && id > 0) {
                normalized.add(id);
            }
        }
        return normalized;
    }

    private int bulkDeleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        return em.createQuery(QUERY_DELETE_DIAGNOSIS_BY_IDS)
                .setParameter("ids", ids)
                .executeUpdate();
    }

    private void flushAndClearIfNeeded(int processed) {
        if (processed > 0 && processed % WRITE_BATCH_SIZE == 0) {
            em.flush();
            em.clear();
        }
    }
}
