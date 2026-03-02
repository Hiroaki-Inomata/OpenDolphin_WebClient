package open.dolphin.security.sql;

/**
 * Utility for creating SQL placeholders for variable-length IN clauses.
 */
public final class SqlPlaceholders {

    private SqlPlaceholders() {
    }

    public static String inClause(int n) {
        if (n <= 0) {
            throw new IllegalArgumentException("Placeholder count must be greater than 0");
        }
        StringBuilder sb = new StringBuilder((n * 2) + 1);
        sb.append('(');
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('?');
        }
        sb.append(')');
        return sb.toString();
    }
}
