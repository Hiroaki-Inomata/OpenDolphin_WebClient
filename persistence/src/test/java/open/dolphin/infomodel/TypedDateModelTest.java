package open.dolphin.infomodel;

import static org.junit.Assert.assertEquals;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.Test;

public class TypedDateModelTest {

    @Test
    public void patientAsLiteModel_keepsTypedBirthday() {
        PatientModel patient = new PatientModel();
        patient.setBirthday(LocalDate.of(1990, 4, 12));

        PatientLiteModel lite = patient.patientAsLiteModel();

        assertEquals(LocalDate.of(1990, 4, 12), lite.getBirthday());
    }

    @Test
    public void patientVisitModel_keepsTypedPvtDate() {
        PatientVisitModel visit = new PatientVisitModel();
        visit.setPvtDate(LocalDateTime.of(2026, 3, 9, 13, 41, 20));

        assertEquals(LocalDateTime.of(2026, 3, 9, 13, 41, 20), visit.getPvtDate());
    }
}
