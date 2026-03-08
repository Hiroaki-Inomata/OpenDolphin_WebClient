package open.dolphin.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

class BlockWrapperTest {

    @Test
    void keepsUriWithoutLegacyMasking() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/openDolphin/resources/user/u,f,pw");

        BlockWrapper wrapper = new BlockWrapper(request);

        assertEquals("/user/u,f,pw", wrapper.getRequestURIForLog());
    }

    @Test
    void keepsUriWhenPrefixIsNotTargeted() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/openDolphin/resources/user/u,f,pw");

        BlockWrapper wrapper = new BlockWrapper(request);

        assertEquals("/user/u,f,pw", wrapper.getRequestURIForLog());
    }
}
