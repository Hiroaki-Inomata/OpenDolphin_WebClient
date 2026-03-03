package open.dolphin.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

class BlockWrapperTest {

    @Test
    void masksTouchUserPasswordInUri() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/openDolphin/resources/touch/user/u,f,pw");

        BlockWrapper wrapper = new BlockWrapper(request);

        assertEquals("/touch/user/u,f,****", wrapper.getRequestURIForLog());
    }

    @Test
    void masksTouchAspUserPasswordAndAdditionalSegments() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/openDolphin/resources/touchasp/user/u,f,pw,pad");

        BlockWrapper wrapper = new BlockWrapper(request);

        assertEquals("/touchasp/user/u,f,****", wrapper.getRequestURIForLog());
    }

    @Test
    void masksDemoUserPassword() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/openDolphin/resources/demo/user/u,f,secret");

        BlockWrapper wrapper = new BlockWrapper(request);

        assertEquals("/demo/user/u,f,****", wrapper.getRequestURIForLog());
    }

    @Test
    void keepsUriWhenTargetSegmentHasLessThanThreeParts() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/openDolphin/resources/touch/user/u,f");

        BlockWrapper wrapper = new BlockWrapper(request);

        assertEquals("/touch/user/u,f", wrapper.getRequestURIForLog());
    }

    @Test
    void keepsUriWhenPrefixIsNotTargeted() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/openDolphin/resources/user/u,f,pw");

        BlockWrapper wrapper = new BlockWrapper(request);

        assertEquals("/user/u,f,pw", wrapper.getRequestURIForLog());
    }
}
