package open.dolphin.touch;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.StreamingOutput;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.util.List;
import open.dolphin.touch.converter.IAllergyModel;
import open.dolphin.touch.security.TouchAccessGuard;
import open.dolphin.touch.session.EHTServiceBean;
import open.dolphin.touch.support.TouchJsonConverter;
import open.dolphin.testsupport.RuntimeDelegateTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EHTResourceAccessGuardTest extends RuntimeDelegateTestSupport {

    @Mock
    private EHTServiceBean ehtService;

    @Mock
    private TouchAccessGuard accessGuard;

    @Mock
    private HttpServletRequest request;

    private EHTResource resource;

    @BeforeEach
    void setUp() throws Exception {
        resource = new EHTResource();
        setField(resource, "ehtService", ehtService);
        setField(resource, "accessGuard", accessGuard);
        TouchJsonConverter converter = new TouchJsonConverter();
        setField(converter, "legacyTouchMapper", new ObjectMapper());
        setField(resource, "touchJsonConverter", converter);
        setField(resource, "servletReq", request);
        lenient().when(request.getRemoteUser()).thenReturn("F001:user01");
    }

    @Test
    void getAllergiesRequiresPatientFacilityGuard() throws Exception {
        when(ehtService.getAllergies(77L)).thenReturn(List.of());

        StreamingOutput output = resource.getAllergies("77");
        output.write(new ByteArrayOutputStream());

        verify(accessGuard).requirePatientFacility(request, 77L);
        verify(ehtService).getAllergies(77L);
    }

    @Test
    void postAllergiesRejectsRequestWithoutKarteId() throws Exception {
        IAllergyModel allergy = new IAllergyModel();
        allergy.setFactor("dust");
        String json = new ObjectMapper().writeValueAsString(new IAllergyModel[]{allergy});

        StreamingOutput output = resource.postAllergies(json);
        assertThrows(NotFoundException.class, () -> output.write(new ByteArrayOutputStream()));

        verify(ehtService, never()).addAllergy(any());
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
