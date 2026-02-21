package open.dolphin.db;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class FlywayMigrationConsistencyTest {

    private static final Path CANONICAL_DIR = Path.of("tools", "flyway", "sql");
    private static final Path MIRROR_DIR = Path.of("src", "main", "resources", "db", "migration");
    private static final Pattern MIGRATION_PATTERN = Pattern.compile("^V([0-9]+(?:_[0-9]+)?)__.+\\.sql$");

    @Test
    void canonicalMigrationsHaveNoDuplicateVersions() throws IOException {
        assertNoDuplicateVersions(CANONICAL_DIR, "canonical migrations (tools/flyway/sql)");
    }

    @Test
    void mirroredMigrationsHaveNoDuplicateVersions() throws IOException {
        assertNoDuplicateVersions(MIRROR_DIR, "mirrored migrations (src/main/resources/db/migration)");
    }

    @Test
    void canonicalAndMirrorAreSynchronized() throws IOException {
        Map<String, Path> canonical = toMigrationMap(CANONICAL_DIR);
        Map<String, Path> mirror = toMigrationMap(MIRROR_DIR);

        assertEquals(canonical.keySet(), mirror.keySet(),
                "Flyway migration filenames diverged between canonical and mirror directories");

        for (String fileName : canonical.keySet()) {
            byte[] canonicalBytes = Files.readAllBytes(canonical.get(fileName));
            byte[] mirrorBytes = Files.readAllBytes(mirror.get(fileName));
            assertArrayEquals(canonicalBytes, mirrorBytes,
                    "Flyway migration content differs for file: " + fileName);
        }
    }

    private static void assertNoDuplicateVersions(Path directory, String label) throws IOException {
        Map<String, List<String>> versions = new LinkedHashMap<>();
        for (Path file : listMigrationFiles(directory)) {
            String fileName = file.getFileName().toString();
            Matcher matcher = MIGRATION_PATTERN.matcher(fileName);
            if (!matcher.matches()) {
                continue;
            }
            String version = matcher.group(1);
            versions.computeIfAbsent(version, ignored -> new ArrayList<>()).add(fileName);
        }

        List<String> duplicates = versions.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .map(entry -> entry.getKey() + " -> " + entry.getValue())
                .toList();

        assertTrue(duplicates.isEmpty(),
                "Duplicate Flyway versions detected in " + label + ": " + duplicates);
    }

    private static Map<String, Path> toMigrationMap(Path directory) throws IOException {
        Map<String, Path> map = new LinkedHashMap<>();
        for (Path file : listMigrationFiles(directory)) {
            map.put(file.getFileName().toString(), file);
        }
        return map;
    }

    private static List<Path> listMigrationFiles(Path directory) throws IOException {
        assertTrue(Files.isDirectory(directory), "Migration directory not found: " + directory.toAbsolutePath());
        try (Stream<Path> stream = Files.list(directory)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> MIGRATION_PATTERN.matcher(path.getFileName().toString()).matches())
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        }
    }
}
