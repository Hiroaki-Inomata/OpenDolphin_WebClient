package open.dolphin.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import open.dolphin.converter.PlistConverter;
import open.dolphin.infomodel.ExtRefModel;
import open.dolphin.infomodel.KarteBean;
import open.dolphin.infomodel.SchemaModel;
import open.dolphin.infomodel.UserModel;
import open.dolphin.security.audit.AuditTrailService;
import open.dolphin.session.KarteServiceBean;
import open.dolphin.session.PVTServiceBean;
import open.dolphin.session.UserServiceBean;
import open.dolphin.session.framework.SessionTraceManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KarteLegacyImagesXmlContractTest {

    @Mock
    KarteServiceBean karteServiceBean;

    @Mock
    PVTServiceBean pvtServiceBean;

    @Mock
    AuditTrailService auditTrailService;

    @Mock
    SessionTraceManager sessionTraceManager;

    @Mock
    UserServiceBean userServiceBean;

    @Mock
    HttpServletRequest httpServletRequest;

    @InjectMocks
    KarteResource resource;

    @Test
    void getImagesPreservesLegacyPlistXmlContract() {
        List<List> imageRanges = List.of(
                List.of(buildSchema(101L, "胸部XP", "https://example.test/images/101", dateOf(2026, Calendar.MARCH, 1))),
                List.of(buildSchema(202L, "心電図", "https://example.test/images/202", dateOf(2026, Calendar.MARCH, 2)))
        );
        when(httpServletRequest.getRemoteUser()).thenReturn("FAC_A:user01");
        when(karteServiceBean.findFacilityIdByKarteId(77L)).thenReturn("FAC_A");
        when(karteServiceBean.getImages(eq(77L), anyList(), anyList()))
                .thenReturn(imageRanges);

        String actualXml = resource.getImages("77,2026-03-01 00:00:00,2026-03-31 00:00:00");
        String baselineXml = new PlistConverter().convert(imageRanges);

        assertThat(normalizeXml(actualXml)).isEqualTo(normalizeXml(baselineXml));
    }

    private static SchemaModel buildSchema(long id, String title, String href, Date started) {
        SchemaModel schema = new SchemaModel();
        schema.setId(id);
        schema.setStarted(started);
        schema.setConfirmed(started);
        schema.setRecorded(started);
        schema.setStatus("F");
        schema.setImageBytes(new byte[] {1, 2, 3});
        schema.setDigest("digest-" + id);
        schema.setUri("uri-" + id);

        ExtRefModel extRef = new ExtRefModel();
        extRef.setTitle(title);
        extRef.setHref(href);
        extRef.setContentType("image/png");
        schema.setExtRefModel(extRef);

        KarteBean karte = new KarteBean();
        karte.setId(77L);
        schema.setKarteBean(karte);

        UserModel user = new UserModel();
        user.setId(900L + id);
        schema.setUserModel(user);
        return schema;
    }

    private static Date dateOf(int year, int month, int dayOfMonth) {
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(year, month, dayOfMonth, 0, 0, 0);
        return calendar.getTime();
    }

    private static String normalizeXml(String xml) {
        return xml.replaceAll(">\\s+<", "><").trim();
    }
}
