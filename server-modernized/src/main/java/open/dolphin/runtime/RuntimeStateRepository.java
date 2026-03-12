package open.dolphin.runtime;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import jakarta.transaction.Transactional;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
public class RuntimeStateRepository {

    @PersistenceContext
    private EntityManager entityManager;

    public Optional<String> findPayload(String stateCategory, String stateKey) {
        if (!isValid(stateCategory) || !isValid(stateKey)) {
            return Optional.empty();
        }
        List<?> rows = entityManager.createNativeQuery(
                        "select cast(payload_json as text) from opendolphin.runtime_state_store where state_category = ? and state_key = ?")
                .setParameter(1, stateCategory.trim())
                .setParameter(2, stateKey.trim())
                .getResultList();
        if (rows == null || rows.isEmpty() || rows.get(0) == null) {
            return Optional.empty();
        }
        return Optional.of(rows.get(0).toString());
    }

    public Map<String, String> findPayloadByCategory(String stateCategory) {
        Map<String, String> result = new LinkedHashMap<>();
        if (!isValid(stateCategory)) {
            return result;
        }
        List<?> rows = entityManager.createNativeQuery(
                        "select state_key, cast(payload_json as text) from opendolphin.runtime_state_store where state_category = ?")
                .setParameter(1, stateCategory.trim())
                .getResultList();
        if (rows == null || rows.isEmpty()) {
            return result;
        }
        for (Object rowObj : rows) {
            if (!(rowObj instanceof Object[] row) || row.length < 2) {
                continue;
            }
            if (row[0] == null || row[1] == null) {
                continue;
            }
            result.put(row[0].toString(), row[1].toString());
        }
        return result;
    }

    @Transactional
    public void upsertPayload(String stateCategory, String stateKey, String payloadJson, Instant updatedAt) {
        if (!isValid(stateCategory) || !isValid(stateKey)) {
            throw new IllegalArgumentException("stateCategory/stateKey are required");
        }
        if (payloadJson == null || payloadJson.isBlank()) {
            throw new IllegalArgumentException("payloadJson is required");
        }
        Instant resolvedUpdatedAt = updatedAt != null ? updatedAt : Instant.now();
        Query query = entityManager.createNativeQuery(
                "insert into opendolphin.runtime_state_store(state_category, state_key, payload_json, updated_at) "
                        + "values (?, ?, cast(? as jsonb), ?) "
                        + "on conflict (state_category, state_key) do update set "
                        + "payload_json = excluded.payload_json, updated_at = excluded.updated_at");
        query.setParameter(1, stateCategory.trim());
        query.setParameter(2, stateKey.trim());
        query.setParameter(3, payloadJson);
        query.setParameter(4, Timestamp.from(resolvedUpdatedAt));
        query.executeUpdate();
    }

    private static boolean isValid(String value) {
        return value != null && !value.isBlank();
    }
}
