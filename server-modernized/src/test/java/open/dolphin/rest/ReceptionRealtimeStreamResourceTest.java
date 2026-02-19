package open.dolphin.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.sse.OutboundSseEvent;
import jakarta.ws.rs.sse.Sse;
import jakarta.ws.rs.sse.SseEventSink;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class ReceptionRealtimeStreamResourceTest {

    @Test
    void subscribeRegistersWhenSessionIsAvailable() throws Exception {
        ReceptionRealtimeStreamResource resource = new ReceptionRealtimeStreamResource();
        RecordingSupport support = new RecordingSupport();
        setField(resource, "sseSupport", support);
        setField(resource, "servletRequest", createRequest("facility:user"));

        RecordingSink sink = new RecordingSink();
        RecordingSse sse = new RecordingSse();
        resource.subscribe(sink, sse, "42");

        assertEquals("facility", support.registeredFacilityId);
        assertEquals("42", support.registeredLastEventId);
        assertTrue(!sink.closed);
    }

    @Test
    void subscribeReturnsServiceUnavailableWhenRegisterFails() throws Exception {
        ReceptionRealtimeStreamResource resource = new ReceptionRealtimeStreamResource();
        RecordingSupport support = new RecordingSupport();
        support.failOnRegister = true;
        setField(resource, "sseSupport", support);
        setField(resource, "servletRequest", createRequest("facility:user"));

        RecordingSink sink = new RecordingSink();
        RecordingSse sse = new RecordingSse();
        WebApplicationException exception = assertThrows(
                WebApplicationException.class,
                () -> resource.subscribe(sink, sse, null)
        );
        assertEquals(503, exception.getResponse().getStatus());
        assertTrue(sink.closed);
    }

    private static HttpServletRequest createRequest(String remoteUser) {
        return (HttpServletRequest) Proxy.newProxyInstance(
                ReceptionRealtimeStreamResourceTest.class.getClassLoader(),
                new Class[]{HttpServletRequest.class},
                (proxy, method, args) -> {
                    if ("getRemoteUser".equals(method.getName())) {
                        return remoteUser;
                    }
                    return null;
                }
        );
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = ReceptionRealtimeStreamResource.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static final class RecordingSupport extends ReceptionRealtimeSseSupport {
        private String registeredFacilityId;
        private String registeredLastEventId;
        private boolean failOnRegister;

        @Override
        public void register(String facilityId, Sse sse, SseEventSink sink, String lastEventId) {
            if (failOnRegister) {
                throw new IllegalStateException("boom");
            }
            this.registeredFacilityId = facilityId;
            this.registeredLastEventId = lastEventId;
        }
    }

    private static final class RecordingSink implements SseEventSink {
        private boolean closed;

        @Override
        public boolean isClosed() {
            return closed;
        }

        @Override
        public java.util.concurrent.CompletionStage<?> send(OutboundSseEvent event) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void close() {
            this.closed = true;
        }
    }

    private static final class RecordingSse implements Sse {
        @Override
        public OutboundSseEvent.Builder newEventBuilder() {
            return new OutboundSseEvent.Builder() {
                @Override
                public OutboundSseEvent.Builder id(String id) {
                    return this;
                }

                @Override
                public OutboundSseEvent.Builder name(String name) {
                    return this;
                }

                @Override
                public OutboundSseEvent.Builder reconnectDelay(long delay) {
                    return this;
                }

                @Override
                public OutboundSseEvent.Builder mediaType(MediaType mediaType) {
                    return this;
                }

                @Override
                public OutboundSseEvent.Builder comment(String comment) {
                    return this;
                }

                @Override
                public OutboundSseEvent.Builder data(Class type, Object data) {
                    return this;
                }

                @Override
                public OutboundSseEvent.Builder data(jakarta.ws.rs.core.GenericType type, Object data) {
                    return this;
                }

                @Override
                public OutboundSseEvent.Builder data(Object data) {
                    return this;
                }

                @Override
                public OutboundSseEvent build() {
                    return null;
                }
            };
        }

        @Override
        public jakarta.ws.rs.sse.SseBroadcaster newBroadcaster() {
            throw new UnsupportedOperationException("not used in tests");
        }
    }
}
