package ac.mju.contractcheck.global.interceptor;

import java.net.URI;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 외부 클라이언트 호출 기록. 모든 호출을 {@code ac.mju.contractcheck.external} 로거에 한 줄씩 남긴다.
 * 대상·메서드·URL(쿼리 제외)·상태·소요 시간·크기는 모든 환경에서 남긴다.
 * 본문은 {@link ExternalBodyView}가 바꾼 문자열을 붙인다. local·dev는 가린 내용({@link ExternalBodySanitizer}),
 * 그 밖(prod 포함)은 비식별 본문({@link ExternalBodyDeidentifier})이다. 헤더는 남기지 않는다.
 * 기록에 실패해도 호출 결과에는 영향이 없다.
 */
public class ExternalCallLog {

    public static final String LOGGER_NAME = "ac.mju.contractcheck.external";
    private static final Logger LOG = LoggerFactory.getLogger(LOGGER_NAME);
    /** 결제 조회·취소 경로의 결제 키({@code /v1/payments/{paymentKey}...}). 승인 경로(confirm)는 키가 아니다. */
    private static final Pattern PAYMENT_KEY_PATH = Pattern.compile("(/v1/payments/)(?!confirm(?:$|/))[^/]+");

    private final ExternalBodyView bodyView;

    /** @param bodyView null이면 본문을 남기지 않는다. */
    public ExternalCallLog(ExternalBodyView bodyView) {
        this.bodyView = bodyView;
    }

    /** 요청이나 응답의 본문. {@code content}가 null이면 크기만 안다(-1은 크기도 모름). */
    public record Payload(long size, byte[] content, String contentType) {

        public static final Payload UNKNOWN = new Payload(-1, null, null);

        public static Payload of(byte[] content, String contentType) {
            return content == null ? UNKNOWN : new Payload(content.length, content, contentType);
        }

        public static Payload sizeOnly(long size) {
            return new Payload(size, null, null);
        }
    }

    /**
     * 호출 한 번의 결과.
     *
     * @param status HTTP 상태. 응답을 받지 못했으면 0
     * @param error 응답을 받지 못했거나 읽지 못한 원인. 없으면 null. 메시지에는 URL이 섞일 수 있어 예외 이름만 남긴다.
     * @param bodies 이 대상의 본문을 남겨도 되는지. 토큰·프로필만 오가는 소셜 로그인은 false다.
     */
    public record Call(String target, String method, URI url, int status, long startedNanos,
                       Payload request, Payload response, Throwable error, boolean bodies) {}

    public void record(Call call) {
        try {
            StringBuilder line = new StringBuilder("외부 호출")
                    .append(" target=").append(call.target())
                    .append(" method=").append(call.method())
                    .append(" url=").append(safeUrl(call.url()))
                    .append(" status=").append(call.status() == 0 ? "-" : String.valueOf(call.status()))
                    .append(" elapsedMs=").append((System.nanoTime() - call.startedNanos()) / 1_000_000)
                    .append(" requestBytes=").append(size(call.request()))
                    .append(" responseBytes=").append(size(call.response()));
            if (call.error() != null) {
                line.append(" error=").append(call.error().getClass().getSimpleName());
            }
            if (bodyView != null && call.bodies()) {
                appendBody(line, " requestBody=", call.request());
                appendBody(line, " responseBody=", call.response());
            }
            if (call.error() != null || call.status() >= 400) {
                LOG.warn("{}", line);
            } else {
                LOG.info("{}", line);
            }
        } catch (RuntimeException exception) {
            LOG.warn("외부 호출 기록 실패 target={} type={}", call.target(), exception.getClass().getSimpleName());
        }
    }

    private void appendBody(StringBuilder line, String label, Payload payload) {
        if (payload.content() != null && payload.content().length > 0) {
            line.append(label).append(bodyView.describe(payload.content(), payload.contentType()));
        }
    }

    private static String size(Payload payload) {
        return payload.size() < 0 ? "-" : String.valueOf(payload.size());
    }

    private static String safeUrl(URI url) {
        if (url == null) {
            return "-";
        }
        String port = url.getPort() < 0 ? "" : ":" + url.getPort();
        String path = url.getRawPath() == null ? "" : PAYMENT_KEY_PATH.matcher(url.getRawPath()).replaceAll("$1[제거]");
        return url.getScheme() + "://" + url.getHost() + port + path;
    }
}
