package ac.mju.contractcheck.global.interceptor;

import com.anthropic.core.RequestOptions;
import com.anthropic.core.http.HttpClient;
import com.anthropic.core.http.HttpRequest;
import com.anthropic.core.http.HttpRequestBody;
import com.anthropic.core.http.HttpResponse;
import com.anthropic.core.http.Interceptor;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.concurrent.CompletableFuture;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * Claude(Anthropic SDK) 호출을 {@link ExternalCallLog}에 남긴다. 앱은 동기 호출만 쓰므로 비동기 호출은 그대로 넘긴다.
 */
@RequiredArgsConstructor
public class AnthropicCallLoggingInterceptor implements Interceptor {

    private static final String JSON = "application/json";

    @NonNull
    private final ExternalCallLog log;

    @Override
    public HttpClient intercept(HttpClient next) {
        return new HttpClient() {
            @Override
            public HttpResponse execute(HttpRequest request, RequestOptions options) {
                HttpRequest buffered = request.buffered();
                ExternalCallLog.Payload sent = buffered.body()
                        .map(AnthropicCallLoggingInterceptor::read)
                        .orElse(ExternalCallLog.Payload.UNKNOWN);
                long started = System.nanoTime();
                int status = 0;
                try {
                    HttpResponse response = next.execute(buffered, options);
                    status = response.statusCode();
                    HttpResponse repeatable = response.buffered();
                    byte[] received;
                    try (InputStream stream = repeatable.body()) {
                        received = stream.readAllBytes();
                    }
                    record(buffered, status, started, sent, ExternalCallLog.Payload.of(received, JSON), null);
                    return repeatable;
                } catch (IOException exception) {
                    record(buffered, status, started, sent, ExternalCallLog.Payload.UNKNOWN, exception);
                    throw new UncheckedIOException(exception);
                } catch (RuntimeException exception) {
                    record(buffered, status, started, sent, ExternalCallLog.Payload.UNKNOWN, exception);
                    throw exception;
                }
            }

            @Override
            public CompletableFuture<HttpResponse> executeAsync(HttpRequest request, RequestOptions options) {
                return next.executeAsync(request, options);
            }

            @Override
            public void close() {
                next.close();
            }
        };
    }

    private void record(HttpRequest request, int status, long started,
                        ExternalCallLog.Payload sent, ExternalCallLog.Payload received, Throwable error) {
        log.record(new ExternalCallLog.Call(
                "claude", request.method().name(), URI.create(request.url()), status, started, sent, received, error, true));
    }

    private static ExternalCallLog.Payload read(HttpRequestBody body) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        body.writeTo(out);
        return ExternalCallLog.Payload.of(out.toByteArray(), body.contentType());
    }
}
