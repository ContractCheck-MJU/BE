package ac.mju.contractcheck.global.interceptor;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.core.interceptor.Context;
import software.amazon.awssdk.core.interceptor.ExecutionAttribute;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.interceptor.ExecutionInterceptor;
import software.amazon.awssdk.core.interceptor.SdkExecutionAttribute;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.http.SdkHttpResponse;

/**
 * 저장소(S3 호환) 호출을 {@link ExternalCallLog}에 남긴다. 본문은 계약서 파일이라 크기만 남긴다.
 * 재시도를 포함한 API 호출 한 번에 한 줄이다. 사전서명 URL 발급은 네트워크 호출이 아니라 남지 않는다.
 */
@RequiredArgsConstructor
public class S3CallLoggingInterceptor implements ExecutionInterceptor {

    private static final ExecutionAttribute<Long> STARTED = new ExecutionAttribute<>("ContractCheckCallStarted");

    @NonNull
    private final ExternalCallLog log;

    @Override
    public void beforeExecution(Context.BeforeExecution context, ExecutionAttributes attributes) {
        attributes.putAttribute(STARTED, System.nanoTime());
    }

    @Override
    public void afterExecution(Context.AfterExecution context, ExecutionAttributes attributes) {
        record(attributes, context.httpRequest(), context.httpResponse(), null);
    }

    @Override
    public void onExecutionFailure(Context.FailedExecution context, ExecutionAttributes attributes) {
        record(attributes, context.httpRequest().orElse(null), context.httpResponse().orElse(null), context.exception());
    }

    private void record(ExecutionAttributes attributes, SdkHttpRequest request, SdkHttpResponse response, Throwable error) {
        Long started = attributes.getAttribute(STARTED);
        String operation = attributes.getAttribute(SdkExecutionAttribute.OPERATION_NAME);
        log.record(new ExternalCallLog.Call(
                "storage." + operation,
                request == null ? "-" : request.method().name(),
                request == null ? null : request.getUri(),
                response == null ? 0 : response.statusCode(),
                started == null ? System.nanoTime() : started,
                length(request == null ? null : request.firstMatchingHeader("Content-Length").orElse(null)),
                length(response == null ? null : response.firstMatchingHeader("Content-Length").orElse(null)),
                error,
                false));
    }

    private static ExternalCallLog.Payload length(String header) {
        if (header == null || !header.chars().allMatch(Character::isDigit) || header.isEmpty()) {
            return ExternalCallLog.Payload.UNKNOWN;
        }
        return ExternalCallLog.Payload.sizeOnly(Long.parseLong(header));
    }
}
