package open.dolphin.orca.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import open.dolphin.orca.OrcaGatewayException;
import org.junit.jupiter.api.Test;

class OrcaHttpClientRequestTest {

    private static final OrcaTransportSettings SETTINGS =
            OrcaTransportSettings.fromAdminConfig("http://localhost:18080", false, "orca", "orca");

    @Test
    void postJsonPayload_usesJsonContentType() {
        StubHttpClient stubClient = new StubHttpClient(List.of(
                ResponseSpec.response(200, "{\"Api_Result\":\"0000\"}", "application/json")));
        OrcaHttpClient client = new OrcaHttpClient(stubClient);

        client.postXml2(
                SETTINGS,
                "/api01rv2/pusheventgetv2",
                "{\"pusheventgetv2req\":{\"event\":\"patient_accept\"}}",
                null,
                "application/json",
                "req-1",
                "trace-1");

        assertEquals("application/json; charset=UTF-8",
                stubClient.lastRequest().headers().firstValue("Content-Type").orElse(null));
        assertEquals("POST", stubClient.lastRequest().method());
    }

    @Test
    void postXmlPayload_keepsXmlContentType() {
        StubHttpClient stubClient = new StubHttpClient(List.of(
                ResponseSpec.response(200, "<data><Api_Result>00</Api_Result></data>", "application/xml")));
        OrcaHttpClient client = new OrcaHttpClient(stubClient);

        client.postXml2(
                SETTINGS,
                "/api01rv2/systeminfv2",
                "<data><systeminfv2req><Request_Date>2026-01-01</Request_Date></systeminfv2req></data>",
                null,
                "application/xml",
                "req-2",
                "trace-2");

        assertEquals("application/xml; charset=UTF-8",
                stubClient.lastRequest().headers().firstValue("Content-Type").orElse(null));
    }

    @Test
    void postIOException_isNotRetriedByDefault() {
        StubHttpClient stubClient = new StubHttpClient(List.of(ResponseSpec.failure(new IOException("boom"))));
        OrcaHttpClient client = new OrcaHttpClient(stubClient);

        OrcaGatewayException error = assertThrows(
                OrcaGatewayException.class,
                () -> client.postXml2(
                        SETTINGS,
                        "/api21/medicalmodv2",
                        "<data><medicalmodreq/></data>",
                        null,
                        "application/xml",
                        "req-3",
                        "trace-3"));

        assertTrue(error.getMessage().contains("Failed to call ORCA API"));
        assertEquals(1, stubClient.sendCount());
    }

    private record ResponseSpec(int status, String body, String contentType, IOException failure) {
        private static ResponseSpec response(int status, String body, String contentType) {
            return new ResponseSpec(status, body, contentType, null);
        }

        private static ResponseSpec failure(IOException ex) {
            return new ResponseSpec(0, null, null, ex);
        }
    }

    private static final class StubHttpClient extends HttpClient {
        private final ArrayDeque<ResponseSpec> responses;
        private HttpRequest lastRequest;
        private int sendCount;

        private StubHttpClient(List<ResponseSpec> plans) {
            this.responses = new ArrayDeque<>(plans);
        }

        HttpRequest lastRequest() {
            return lastRequest;
        }

        int sendCount() {
            return sendCount;
        }

        @Override
        public Optional<CookieHandler> cookieHandler() {
            return Optional.empty();
        }

        @Override
        public Optional<Duration> connectTimeout() {
            return Optional.of(Duration.ofSeconds(1));
        }

        @Override
        public Redirect followRedirects() {
            return Redirect.NEVER;
        }

        @Override
        public Optional<ProxySelector> proxy() {
            return Optional.empty();
        }

        @Override
        public SSLContext sslContext() {
            return null;
        }

        @Override
        public SSLParameters sslParameters() {
            return new SSLParameters();
        }

        @Override
        public Optional<Authenticator> authenticator() {
            return Optional.empty();
        }

        @Override
        public Version version() {
            return Version.HTTP_1_1;
        }

        @Override
        public Optional<Executor> executor() {
            return Optional.empty();
        }

        @Override
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler)
                throws IOException {
            lastRequest = request;
            sendCount++;
            ResponseSpec plan = responses.pollFirst();
            if (plan == null) {
                throw new IOException("No stub response configured");
            }
            if (plan.failure != null) {
                throw plan.failure;
            }
            Map<String, List<String>> headerMap = new LinkedHashMap<>();
            if (plan.contentType != null) {
                headerMap.put("Content-Type", List.of(plan.contentType));
            }
            HttpHeaders headers = HttpHeaders.of(headerMap, (key, value) -> true);
            @SuppressWarnings("unchecked")
            T body = (T) plan.body;
            return new StubHttpResponse<>(request, plan.status, body, headers);
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException("Not implemented"));
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler,
                HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException("Not implemented"));
        }
    }

    private static final class StubHttpResponse<T> implements HttpResponse<T> {
        private final HttpRequest request;
        private final int status;
        private final T body;
        private final HttpHeaders headers;

        private StubHttpResponse(HttpRequest request, int status, T body, HttpHeaders headers) {
            this.request = request;
            this.status = status;
            this.body = body;
            this.headers = headers;
        }

        @Override
        public int statusCode() {
            return status;
        }

        @Override
        public HttpRequest request() {
            return request;
        }

        @Override
        public Optional<HttpResponse<T>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public HttpHeaders headers() {
            return headers;
        }

        @Override
        public T body() {
            return body;
        }

        @Override
        public Optional<SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override
        public URI uri() {
            return request.uri();
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }
    }
}
