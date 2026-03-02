package open.dolphin.security.xml;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.StringReader;
import org.jdom.JDOMException;
import org.junit.jupiter.api.Test;

class SecureXmlTest {

    @Test
    void rejectsDoctypeDeclaration() {
        String xml = "<!DOCTYPE root [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]><root>&xxe;</root>";

        assertThrows(JDOMException.class, () -> SecureXml.newSaxBuilder().build(new StringReader(xml)));
    }
}
