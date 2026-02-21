package open.dolphin.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import open.dolphin.infomodel.HealthInsuranceModel;
import open.dolphin.infomodel.PVTHealthInsuranceModel;
import open.dolphin.touch.converter.IOSHelper;
import org.junit.jupiter.api.Test;

class PVTServiceBeanClinicalTest {

    @Test
    void normalizePvtDateForStorage_acceptsDateOnly() {
        String normalized = PVTServiceBean.normalizePvtDateForStorage("2025-11-03");
        assertEquals("2025-11-03T00:00:00", normalized);
        assertEquals("2025-11-03", PVTServiceBean.extractPvtDatePart("2025-11-03"));
    }

    @Test
    void normalizePvtDateForStorage_keepsDateTime() {
        String normalized = PVTServiceBean.normalizePvtDateForStorage("2025-11-03T12:34:56");
        assertEquals("2025-11-03T12:34:56", normalized);
        assertEquals("2025-11-03", PVTServiceBean.extractPvtDatePart(normalized));
    }

    @Test
    void mergeInsurances_keepsExistingWhenIncomingIsSubset() {
        HealthInsuranceModel existingA = insurance("guid-a", "1001", "2024-01-01");
        HealthInsuranceModel existingB = insurance("guid-b", "1002", "2024-01-01");
        HealthInsuranceModel incomingA = insurance("guid-a", "1001", "2025-01-01");
        HealthInsuranceModel incomingC = insurance("guid-c", "1003", "2025-01-01");

        PVTServiceBean.InsuranceMergeResult result = PVTServiceBean.mergeInsurances(
                List.of(existingA, existingB),
                List.of(incomingA, incomingC));

        assertEquals(1, result.updates().size());
        assertSame(existingA, result.updates().get(0).persisted());
        assertSame(incomingA, result.updates().get(0).incoming());
        assertEquals(1, result.additions().size());
        assertSame(incomingC, result.additions().get(0));
        assertEquals(3, result.merged().size());
        assertTrue(result.merged().contains(existingB), "Unspecified existing insurance must be retained");
    }

    private static HealthInsuranceModel insurance(String guid, String number, String startDate) {
        PVTHealthInsuranceModel pvtInsurance = new PVTHealthInsuranceModel();
        pvtInsurance.setGUID(guid);
        pvtInsurance.setInsuranceNumber(number);
        pvtInsurance.setStartDate(startDate);

        HealthInsuranceModel model = new HealthInsuranceModel();
        model.setBeanBytes(IOSHelper.toXMLBytes(pvtInsurance));
        return model;
    }
}
