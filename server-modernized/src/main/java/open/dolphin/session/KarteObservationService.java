package open.dolphin.session;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import open.dolphin.infomodel.ObservationModel;

@ApplicationScoped
@Transactional
public class KarteObservationService {

    private static final int WRITE_BATCH_SIZE = 50;
    private static final String KARTE_ID = "karteId";

    private static final String QUERY_DELETE_OBSERVATIONS_BY_IDS =
            "delete from ObservationModel o where o.id in :ids";

    @PersistenceContext
    private EntityManager em;

    public List<ObservationModel> getObservations(long karteId, String observation, String phenomenon, Date firstConfirmed) {
        if (observation != null) {
            if (firstConfirmed != null) {
                return em.createQuery(
                                "from ObservationModel o where o.karte.id=:karteId and o.observation=:observation and o.started >= :firstConfirmed",
                                ObservationModel.class)
                        .setParameter(KARTE_ID, karteId)
                        .setParameter("observation", observation)
                        .setParameter("firstConfirmed", firstConfirmed)
                        .getResultList();
            }
            return em.createQuery(
                            "from ObservationModel o where o.karte.id=:karteId and o.observation=:observation",
                            ObservationModel.class)
                    .setParameter(KARTE_ID, karteId)
                    .setParameter("observation", observation)
                    .getResultList();
        }

        if (phenomenon != null) {
            if (firstConfirmed != null) {
                return em.createQuery(
                                "from ObservationModel o where o.karte.id=:karteId and o.phenomenon=:phenomenon and o.started >= :firstConfirmed",
                                ObservationModel.class)
                        .setParameter(KARTE_ID, karteId)
                        .setParameter("phenomenon", phenomenon)
                        .setParameter("firstConfirmed", firstConfirmed)
                        .getResultList();
            }
            return em.createQuery(
                            "from ObservationModel o where o.karte.id=:karteId and o.phenomenon=:phenomenon",
                            ObservationModel.class)
                    .setParameter(KARTE_ID, karteId)
                    .setParameter("phenomenon", phenomenon)
                    .getResultList();
        }

        return Collections.emptyList();
    }

    public List<Long> addObservations(List<ObservationModel> observations) {
        if (observations == null || observations.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> ret = new ArrayList<>(observations.size());
        int processed = 0;
        for (ObservationModel model : observations) {
            em.persist(model);
            ret.add(model.getId());
            processed++;
            flushAndClearIfNeeded(processed);
        }
        return ret;
    }

    public int updateObservations(List<ObservationModel> observations) {
        if (observations == null || observations.isEmpty()) {
            return 0;
        }

        int cnt = 0;
        for (ObservationModel model : observations) {
            em.merge(model);
            cnt++;
            flushAndClearIfNeeded(cnt);
        }
        return cnt;
    }

    public int removeObservations(List<Long> observations) {
        List<Long> normalized = normalizeIds(observations);
        if (normalized.isEmpty()) {
            return 0;
        }
        return em.createQuery(QUERY_DELETE_OBSERVATIONS_BY_IDS)
                .setParameter("ids", normalized)
                .executeUpdate();
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

    private void flushAndClearIfNeeded(int processed) {
        if (processed > 0 && processed % WRITE_BATCH_SIZE == 0) {
            em.flush();
            em.clear();
        }
    }
}
