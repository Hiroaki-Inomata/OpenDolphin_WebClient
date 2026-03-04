package open.dolphin.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.WebApplicationException;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import open.dolphin.infomodel.PatientModel;
import open.dolphin.rest.dto.PatientImageEntryResponse;
import open.dolphin.session.PatientImageServiceBean;
import open.dolphin.session.PatientServiceBean;
import open.dolphin.testsupport.RuntimeDelegateTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PatientImagesResourceFeatureHeaderTest extends RuntimeDelegateTestSupport {

    private static final String FEATURE_ENV = "OPENDOLPHIN_PATIENT_IMAGES_ENABLED";
    private static final String PATIENT_ID = "00001";

    private PatientImagesResource resource;
    private HttpServletRequest request;
    private PatientServiceBean patientServiceBean;
    private PatientImageServiceBean patientImageServiceBean;

    @BeforeEach
    void setUp() throws Exception {
        request = mock(HttpServletRequest.class);
        patientServiceBean = mock(PatientServiceBean.class);
        patientImageServiceBean = mock(PatientImageServiceBean.class);

        resource = new PatientImagesResource() {
            @Override
            String readEnvironmentValue(String key) {
                if (FEATURE_ENV.equals(key)) {
                    return "1";
                }
                return null;
            }
        };
        setField(resource, "httpServletRequest", request);
        setField(resource, "patientServiceBean", patientServiceBean);
        setField(resource, "patientImageServiceBean", patientImageServiceBean);

        when(request.getRemoteUser()).thenReturn("F001:doctor01");
        when(request.getRequestURI()).thenReturn("/openDolphin/resources/patients/" + PATIENT_ID + "/images");
    }

    @Test
    void list_acceptsClientFeatureHeader() {
        when(request.getHeader("X-Client-Feature-Images")).thenReturn("1");
        when(patientServiceBean.getPatientById("F001", PATIENT_ID)).thenReturn(new PatientModel());

        PatientImageEntryResponse entry = new PatientImageEntryResponse();
        entry.setImageId(10L);
        when(patientImageServiceBean.listImages("F001", PATIENT_ID)).thenReturn(List.of(entry));

        List<PatientImageEntryResponse> items = resource.list(PATIENT_ID);

        assertEquals(1, items.size());
        assertEquals("/openDolphin/resources/patients/00001/images/10", items.get(0).getDownloadUrl());
    }

    @Test
    void list_rejectsLegacyHeaderOnly() {
        when(request.getHeader("X-Feature-Images")).thenReturn("1");
        when(request.getHeader("X-Client-Feature-Images")).thenReturn(null);

        WebApplicationException ex = assertThrows(WebApplicationException.class, () -> resource.list(PATIENT_ID));

        assertEquals(404, ex.getResponse().getStatus());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) ex.getResponse().getEntity();
        assertEquals("feature_disabled", body.get("error"));
        assertEquals("X-Client-Feature-Images", body.get("requiredHeader"));
        assertEquals("X-Feature-Images", body.get("unsupportedHeader"));
        verifyNoInteractions(patientServiceBean, patientImageServiceBean);
    }

    @Test
    void list_requiresAuthorizationEvenWhenClientFeatureHeaderIsPresent() {
        when(request.getHeader("X-Client-Feature-Images")).thenReturn("1");
        when(patientServiceBean.getPatientById("F001", PATIENT_ID)).thenReturn(null);

        WebApplicationException ex = assertThrows(WebApplicationException.class, () -> resource.list(PATIENT_ID));

        assertEquals(403, ex.getResponse().getStatus());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) ex.getResponse().getEntity();
        assertEquals("forbidden", body.get("error"));
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Class<?> type = target.getClass();
        Field field = null;
        while (type != null && field == null) {
            try {
                field = type.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        if (field == null) {
            throw new NoSuchFieldException(name);
        }
        field.setAccessible(true);
        field.set(target, value);
    }
}
