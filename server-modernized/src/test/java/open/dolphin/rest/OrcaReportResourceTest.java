package open.dolphin.rest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class OrcaReportResourceTest {

    @Test
    void isPdfRequest_detectsPrintModePdfWithCaseVariations() throws Exception {
        String payload = "<data><PrescriptionReq><PRINT_MODE>Pdf</PRINT_MODE></PrescriptionReq></data>";

        assertTrue(invokeIsPdfRequest(payload));
    }

    @Test
    void isPdfRequest_rejectsDtdPayload() throws Exception {
        String payload = """
                <!DOCTYPE data [
                  <!ENTITY xxe SYSTEM "file:///etc/passwd">
                ]>
                <data><print_mode>&xxe;</print_mode></data>
                """;

        assertFalse(invokeIsPdfRequest(payload));
    }

    private boolean invokeIsPdfRequest(String payload) throws Exception {
        OrcaReportResource resource = new OrcaReportResource();
        Method method = OrcaReportResource.class.getDeclaredMethod("isPdfRequest", String.class);
        method.setAccessible(true);
        return (boolean) method.invoke(resource, payload);
    }
}
