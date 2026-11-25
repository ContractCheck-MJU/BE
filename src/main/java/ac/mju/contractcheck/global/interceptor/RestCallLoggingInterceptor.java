package ac.mju.contractcheck.global.interceptor;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/**
 * {@code RestClient}·{@code RestTemplate} 호출을 {@link ExternalCallLog}에 남긴다. 응답 본문을 한 번 읽어 두고
 * 호출한 쪽이 다시 읽을 수 있게 돌려준다. 예외는 그대로 던진다.
 */
@RequiredArgsConstructor
public class RestCallLoggingInterceptor implements ClientHttpRequestInterceptor {

    @NonNull
    private final String target;
    @NonNull
    private final ExternalCallLog log;
    private final boolean bodies;

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        long started = System.nanoTime();
        ExternalCallLog.Payload sent = ExternalCallLog.Payload.of(body, contentType(request.getHeaders()));
        ClientHttpResponse response = null;
        int status = 0;
        try {
            response = execution.execute(request, body);
            status = response.getStatusCode().value();
            byte[] received = response.getBody().readAllBytes();
            record(request, status, started, sent,
                    ExternalCallLog.Payload.of(received, contentType(response.getHeaders())), null);
            return new BufferedResponse(response, received);
        } catch (IOException | RuntimeException exception) {
            record(request, status, started, sent, ExternalCallLog.Payload.UNKNOWN, exception);
            if (response != null) {
                response.close();
            }
            throw exception;
        }
    }

    private void record(HttpRequest request, int status, long started,
                        ExternalCallLog.Payload sent, ExternalCallLog.Payload received, Throwable error) {
        log.record(new ExternalCallLog.Call(
                target, request.getMethod().name(), request.getURI(), status, started, sent, received, error, bodies));
    }

    private static String contentType(HttpHeaders headers) {
        MediaType type = headers.getContentType();
        return type == null ? null : type.toString();
    }

    private record BufferedResponse(ClientHttpResponse delegate, byte[] body) implements ClientHttpResponse {

        @Override
        public HttpStatusCode getStatusCode() throws IOException {
            return delegate.getStatusCode();
        }

        @Override
        public String getStatusText() throws IOException {
            return delegate.getStatusText();
        }

        @Override
        public HttpHeaders getHeaders() {
            return delegate.getHeaders();
        }

        @Override
        public InputStream getBody() {
            return new ByteArrayInputStream(body);
        }

        @Override
        public void close() {
            delegate.close();
        }
    }
}
