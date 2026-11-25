package ac.mju.contractcheck.global.interceptor;

import com.anthropic.core.RequestOptions;
import com.anthropic.core.http.Headers;
import com.anthropic.core.http.HttpClient;
import com.anthropic.core.http.HttpRequest;
import com.anthropic.core.http.HttpRequestBody;
import com.anthropic.core.http.HttpResponse;
import com.anthropic.core.http.Interceptor;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
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
                HttpResponse response;
                try {
                    response = next.execute(buffered, options);
                } catch (RuntimeException exception) {
                    record(buffered, 0, started, sent, ExternalCallLog.Payload.UNKNOWN, exception);
                    throw exception;
                }
                ReadResponse read = ReadResponse.of(response);
                record(buffered, response.statusCode(), started, sent,
                        read.failure() == null ? ExternalCallLog.Payload.of(read.content(), JSON) : ExternalCallLog.Payload.UNKNOWN,
                        read.failure());
                return read;
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

    /**
     * 본문을 한 번 읽어 둔 응답. 읽다가 실패하면 읽은 데까지 돌려준 뒤 같은 {@link IOException}을 다시 낸다.
     * 그래서 SDK는 인터셉터가 없을 때와 같은 예외(시간 초과 판별 포함)를 만든다.
     */
    private record ReadResponse(HttpResponse delegate, byte[] content, IOException failure) implements HttpResponse {

        static ReadResponse of(HttpResponse response) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (InputStream stream = response.body()) {
                stream.transferTo(out);
                return new ReadResponse(response, out.toByteArray(), null);
            } catch (IOException exception) {
                return new ReadResponse(response, out.toByteArray(), exception);
            }
        }

        @Override
        public int statusCode() {
            return delegate.statusCode();
        }

        @Override
        public Headers headers() {
            return delegate.headers();
        }

        @Override
        public InputStream body() {
            InputStream read = new ByteArrayInputStream(content);
            if (failure == null) {
                return read;
            }
            return new SequenceInputStream(read, new InputStream() {
                @Override
                public int read() throws IOException {
                    throw failure;
                }
            });
        }

        @Override
        public void close() {
            delegate.close();
        }
    }

    private static ExternalCallLog.Payload read(HttpRequestBody body) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        body.writeTo(out);
        return ExternalCallLog.Payload.of(out.toByteArray(), body.contentType());
    }
}
