package open.dolphin.touch;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
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
import open.dolphin.touch.converter.IPhysicalModel;
import open.dolphin.touch.converter.IVitalModel;
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
    void postVitalRequiresFacilityPatIdGuard() throws Exception {
        IVitalModel vital = new IVitalModel();
        vital.setFacilityPatId("F001:patient99");
        String json = new ObjectMapper().writeValueAsString(vital);

        StreamingOutput output = resource.postVital(json);
        output.write(new ByteArrayOutputStream());

        verify(accessGuard).requireFacilityPatId(request, "F001:patient99");
        verify(ehtService).addVital(any());
    }

    @Test
    void postVitalRejectsCrossFacilityFacilityPatId() throws Exception {
        IVitalModel vital = new IVitalModel();
        vital.setFacilityPatId("F999:patient99");
        String json = new ObjectMapper().writeValueAsString(vital);
        doThrow(new NotFoundException()).when(accessGuard).requireFacilityPatId(request, "F999:patient99");

        StreamingOutput output = resource.postVital(json);

        assertThrows(NotFoundException.class, () -> output.write(new ByteArrayOutputStream()));
        verify(accessGuard).requireFacilityPatId(request, "F999:patient99");
        verify(ehtService, never()).addVital(any());
    }

    @Test
    void removeVitalRequiresVitalFacilityGuard() throws Exception {
        when(ehtService.removeVital("55")).thenReturn(1);

        StreamingOutput output = resource.removeVital("\"55\"");
        output.write(new ByteArrayOutputStream());

        verify(accessGuard).requireVitalFacility(request, 55L);
        verify(ehtService).removeVital("55");
    }

    @Test
    void removeVitalRejectsCrossFacilityVitalId() throws Exception {
        doThrow(new NotFoundException()).when(accessGuard).requireVitalFacility(request, 55L);

        StreamingOutput output = resource.removeVital("\"55\"");

        assertThrows(NotFoundException.class, () -> output.write(new ByteArrayOutputStream()));
        verify(accessGuard).requireVitalFacility(request, 55L);
        verify(ehtService, never()).removeVital(any());
    }

    @Test
    void getKartePhysicalRequiresKarteFacilityGuard() throws Exception {
        when(ehtService.getPhysicals(88L)).thenReturn(List.of());

        StreamingOutput output = resource.getKartePhysical(request, "88");
        output.write(new ByteArrayOutputStream());

        verify(accessGuard).requireKarteFacility(request, 88L);
        verify(ehtService).getPhysicals(88L);
    }

    @Test
    void getKartePhysicalRejectsCrossFacilityKarteId() throws Exception {
        doThrow(new NotFoundException()).when(accessGuard).requireKarteFacility(request, 88L);

        StreamingOutput output = resource.getKartePhysical(request, "88");

        assertThrows(NotFoundException.class, () -> output.write(new ByteArrayOutputStream()));
        verify(accessGuard).requireKarteFacility(request, 88L);
        verify(ehtService, never()).getPhysicals(anyLong());
    }

    @Test
    void postPhysicalRequiresKarteFacilityGuard() throws Exception {
        IPhysicalModel model = new IPhysicalModel();
        model.setKartePK(91L);
        String json = new ObjectMapper().writeValueAsString(model);

        StreamingOutput output = resource.postPhysical(json);
        output.write(new ByteArrayOutputStream());

        verify(accessGuard).requireKarteFacility(request, 91L);
        verify(ehtService).addObservations(any());
    }

    @Test
    void postPhysicalRejectsCrossFacilityKartePk() throws Exception {
        IPhysicalModel model = new IPhysicalModel();
        model.setKartePK(91L);
        String json = new ObjectMapper().writeValueAsString(model);
        doThrow(new NotFoundException()).when(accessGuard).requireKarteFacility(request, 91L);

        StreamingOutput output = resource.postPhysical(json);

        assertThrows(NotFoundException.class, () -> output.write(new ByteArrayOutputStream()));
        verify(accessGuard).requireKarteFacility(request, 91L);
        verify(ehtService, never()).addObservations(any());
    }

    @Test
    void removePhysicalValidatesAllObservationIdsBeforeDelete() throws Exception {
        when(ehtService.removeObservations(List.of(11L, 12L))).thenReturn(2);

        StreamingOutput output = resource.removePhysical("11,12");
        output.write(new ByteArrayOutputStream());

        verify(accessGuard).requireObservationFacility(request, 11L);
        verify(accessGuard).requireObservationFacility(request, 12L);
        verify(ehtService).removeObservations(List.of(11L, 12L));
    }

    @Test
    void removePhysicalRejectsCrossFacilityObservationBeforeDelete() throws Exception {
        doAnswer(invocation -> {
            long observationId = invocation.getArgument(1, Long.class);
            if (observationId == 12L) {
                throw new NotFoundException();
            }
            return null;
        }).when(accessGuard).requireObservationFacility(eq(request), anyLong());

        StreamingOutput output = resource.removePhysical("11,12");

        assertThrows(NotFoundException.class, () -> output.write(new ByteArrayOutputStream()));
        verify(accessGuard).requireObservationFacility(request, 11L);
        verify(accessGuard).requireObservationFacility(request, 12L);
        verify(ehtService, never()).removeObservations(any());
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
