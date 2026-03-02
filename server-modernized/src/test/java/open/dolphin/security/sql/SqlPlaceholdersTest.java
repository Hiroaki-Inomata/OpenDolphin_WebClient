package open.dolphin.security.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class SqlPlaceholdersTest {

    @Test
    void buildsInClausePlaceholders() {
        assertEquals("(?,?,?)", SqlPlaceholders.inClause(3));
    }

    @Test
    void rejectsNonPositivePlaceholderCount() {
        assertThrows(IllegalArgumentException.class, () -> SqlPlaceholders.inClause(0));
    }
}
