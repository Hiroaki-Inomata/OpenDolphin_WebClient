package open.dolphin.msg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import open.dolphin.session.AccountSummary;
import open.orca.rest.ORCAConnection;
import open.stamp.seed.CopyStampTreeBuilder;
import org.junit.jupiter.api.Test;

class MessagingDefensiveCopyTest {

    @Test
    void accountSummaryClonesDate() {
        AccountSummary summary = new AccountSummary();
        Date registered = new Date();
        summary.setRegisteredDate(registered);

        registered.setTime(0);

        Date snapshot = summary.getRegisteredDate();
        assertTrue(snapshot.getTime() != 0);
        snapshot.setTime(0);
        assertTrue(summary.getRegisteredDate().getTime() != 0);
        summary.setMemberType("type");
        assertEquals(DateFormat.getDateInstance().format(summary.getRegisteredDate()), summary.getRdDate());
    }

    @Test
    void orcaConnectionReturnsPropertiesCopy() throws IOException {
        Path tempDir = Files.createTempDirectory("orca");
        Path customProperties = tempDir.resolve("custom.properties");
        Files.writeString(customProperties, String.join(System.lineSeparator(),
                "orca.orcaapi.ip=127.0.0.1",
                "dolphin.facilityId=facility01",
                "orca.jdbc.url=jdbc:h2:mem:test",
                "orca.password=pass"));
        String originalJbossHome = System.getProperty("jboss.home.dir");
        System.setProperty("jboss.home.dir", tempDir.toString());

        try {
            ORCAConnection connection = newIsolatedOrcaConnection();
            Properties props = connection.getProperties();
            props.setProperty("new", "value");

            assertEquals("127.0.0.1", connection.getProperty("orca.orcaapi.ip"));
            assertEquals("facility01", connection.getProperties().getProperty("dolphin.facilityId"));
            assertNull(connection.getProperty("orca.password"));
            assertNull(connection.getProperties().getProperty("orca.jdbc.url"));
            assertNull(connection.getProperties().getProperty("new"));
        } finally {
            if (originalJbossHome == null) {
                System.clearProperty("jboss.home.dir");
            } else {
                System.setProperty("jboss.home.dir", originalJbossHome);
            }
        }
    }

    private ORCAConnection newIsolatedOrcaConnection() {
        try {
            Constructor<ORCAConnection> constructor = ORCAConnection.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to instantiate ORCAConnection for test", e);
        }
    }

    @Test
    void copyStampTreeBuilderReturnsImmutableLists() throws Exception {
        CopyStampTreeBuilder builder = new CopyStampTreeBuilder();
        builder.buildStart();
        builder.buildRoot("root", "entity");
        builder.buildStampInfo("name", "role", "entity", "true", "memo", "seed-id");
        builder.buildRootEnd();
        builder.buildEnd();

        List<String> seeds = builder.getSeedStampList();
        List<open.dolphin.infomodel.StampModel> models = builder.getStampModelToPersist();

        assertEquals(List.of("seed-id"), seeds);
        assertEquals(1, models.size());
        assertThrows(UnsupportedOperationException.class, () -> seeds.add("mutated"));
        assertThrows(UnsupportedOperationException.class, () -> models.clear());
    }
}
