package open.dolphin.mbean;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import open.dolphin.infomodel.PVTHealthInsuranceModel;
import org.jdom.Element;
import org.jdom.Namespace;
import org.junit.jupiter.api.Test;

class PVTBuilderTest {

    private static final Namespace MML_HI =
            Namespace.getNamespace("mmlHi", "http://www.medxml.net/MML/ContentModule/HealthInsurance/1.1");

    @Test
    void parseHealthInsurance_handlesMissingInsuranceClassElement() throws Exception {
        PVTBuilder builder = new PVTBuilder();
        PVTHealthInsuranceModel insurance = new PVTHealthInsuranceModel();
        setField(builder, "curInsurance", insurance);

        Element docInfo = new Element("docInfo");
        Element content = new Element("content");
        Element module = new Element("HealthInsuranceModule", MML_HI);
        module.addContent(new Element("insuranceNumber", MML_HI).setText("12345"));
        content.addContent(module);

        Method method = PVTBuilder.class.getDeclaredMethod("parseHealthInsurance", Element.class, Element.class);
        method.setAccessible(true);

        assertDoesNotThrow(() -> method.invoke(builder, docInfo, content));
        assertNull(insurance.getInsuranceClass());
        assertEquals("12345", insurance.getInsuranceNumber());
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
