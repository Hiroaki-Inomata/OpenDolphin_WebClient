package open.orca.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import jakarta.ws.rs.WebApplicationException;
import org.junit.jupiter.api.Test;

class OrcaResourceInteractionSecurityTest {

    @Test
    void buildInteractionSqlUsesPreparedPlaceholders() {
        String sql = OrcaResource.buildInteractionSql(2, 1);

        assertTrue(sql.contains("drugcd in (?,?)"));
        assertTrue(sql.contains("drugcd2 in (?)"));
    }

    @Test
    void normalizeInteractionCodesRejectsInvalidCharacterWithBadRequest() {
        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> OrcaResource.normalizeInteractionCodes(List.of("123' OR '1'='1"), "codes1"));

        assertEquals(400, ex.getResponse().getStatus());
    }

    @Test
    void normalizeInteractionCodesTrimsAndKeepsValidCode() {
        List<String> normalized = OrcaResource.normalizeInteractionCodes(List.of(" 123ABC_-. "), "codes1");

        assertEquals(1, normalized.size());
        assertEquals("123ABC_-.", normalized.get(0));
        assertFalse(normalized.get(0).contains(" "));
    }
}
