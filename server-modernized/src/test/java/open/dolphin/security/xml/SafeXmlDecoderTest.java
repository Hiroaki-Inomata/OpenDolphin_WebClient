package open.dolphin.security.xml;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class SafeXmlDecoderTest {

    @Test
    void rejectsDoctypeDeclaration() {
        String xml = "<?xml version=\"1.0\"?><!DOCTYPE root><java version=\"1.8.0\" class=\"java.beans.XMLDecoder\"></java>";

        assertThrows(IllegalArgumentException.class,
                () -> SafeXmlDecoder.decode(xml.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void rejectsClassOutsideAllowlist() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<java version=\"1.8.0\" class=\"java.beans.XMLDecoder\">"
                + "<object class=\"java.io.File\"><string>/tmp/unsafe</string></object>"
                + "</java>";

        assertThrows(IllegalArgumentException.class,
                () -> SafeXmlDecoder.decode(xml.getBytes(StandardCharsets.UTF_8)));
    }
}
