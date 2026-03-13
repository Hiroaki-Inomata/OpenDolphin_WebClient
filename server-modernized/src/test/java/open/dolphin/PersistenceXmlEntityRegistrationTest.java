package open.dolphin;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.NodeList;

class PersistenceXmlEntityRegistrationTest {

    @Test
    void persistenceXmlExplicitlyListsEveryEntityClass() throws Exception {
        Path repoRoot = findRepoRoot();
        Set<String> expected = new TreeSet<>();
        expected.addAll(findEntityClasses(repoRoot.resolve("persistence/src/main/java/open/dolphin/infomodel")));
        expected.addAll(findEntityClasses(repoRoot.resolve("server-modernized/src/main/java/open/dolphin/security/integrity")));

        Set<String> actual = readClassEntries(repoRoot.resolve("server-modernized/src/main/resources/META-INF/persistence.xml"));

        assertThat(actual).containsExactlyElementsOf(expected);
        assertThat(Files.readString(repoRoot.resolve("server-modernized/src/main/resources/META-INF/persistence.xml")))
                .contains("<exclude-unlisted-classes>true</exclude-unlisted-classes>");
    }

    private static Set<String> findEntityClasses(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            throw new IOException("Entity source directory not found: " + directory);
        }
        try (Stream<Path> stream = Files.walk(directory)) {
            return stream
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(PersistenceXmlEntityRegistrationTest::isEntitySource)
                    .map(PersistenceXmlEntityRegistrationTest::toClassName)
                    .collect(Collectors.toCollection(TreeSet::new));
        }
    }

    private static boolean isEntitySource(Path sourceFile) {
        try {
            return Files.readString(sourceFile).matches("(?ms).*^\\s*@Entity\\b.*");
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read " + sourceFile, ex);
        }
    }

    private static String toClassName(Path sourceFile) {
        String normalized = sourceFile.toString().replace('\\', '/');
        int srcIndex = normalized.indexOf("/src/main/java/");
        if (srcIndex < 0) {
            throw new IllegalStateException("Unexpected entity source path: " + sourceFile);
        }
        String classPath = normalized.substring(srcIndex + "/src/main/java/".length(), normalized.length() - ".java".length());
        return classPath.replace('/', '.');
    }

    private static Set<String> readClassEntries(Path persistenceXml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            NodeList classElements = factory.newDocumentBuilder()
                    .parse(Files.newInputStream(persistenceXml))
                    .getElementsByTagNameNS("*", "class");
            return java.util.stream.IntStream.range(0, classElements.getLength())
                    .mapToObj(index -> classElements.item(index).getTextContent().trim())
                    .collect(Collectors.toCollection(TreeSet::new));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse " + persistenceXml, ex);
        }
    }

    private static Path findRepoRoot() {
        Path cursor = Path.of("").toAbsolutePath().normalize();
        while (cursor != null) {
            if (Files.exists(cursor.resolve("pom.server-modernized.xml"))) {
                return cursor;
            }
            cursor = cursor.getParent();
        }
        throw new IllegalStateException("Repository root not found");
    }
}
