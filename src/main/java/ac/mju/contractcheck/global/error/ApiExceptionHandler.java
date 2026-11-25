package ac.mju.contractcheck.global.error;

import ac.mju.contractcheck.client.PaymentRejectedException;
import ac.mju.contractcheck.global.filter.MdcRequestIdFilter;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> notFound(NotFoundException exception) {
        String traceId = traceId();
        log.warn("리소스 없음 code={} traceId={}", exception.code(), traceId);
        return ResponseEntity.status(404).body(new ApiError(exception.code(), exception.getMessage(), traceId));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiError> conflict(ConflictException exception) {
        String traceId = traceId();
        log.warn("상태 충돌 code={} traceId={}", exception.code(), traceId);
        return ResponseEntity.status(409).body(new ApiError(exception.code(), exception.getMessage(), traceId));
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> rejected(ApiException exception) {
        String traceId = traceId();
        log.warn("요청 거절 status={} code={} traceId={}", exception.status(), exception.code(), traceId);
        return ResponseEntity.status(exception.status())
                .body(new ApiError(exception.code(), exception.getMessage(), traceId));
    }

    /** 멀티파트 한도(20MB)를 넘으면 서비스에 닿기 전에 여기로 온다. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> uploadTooLarge(MaxUploadSizeExceededException exception) {
        String traceId = traceId();
        log.warn("업로드 크기 초과 traceId={}", traceId);
        return ResponseEntity.status(413)
                .body(new ApiError("FILE_TOO_LARGE", "파일은 20MB 이하여야 합니다.", traceId));
    }

    @ExceptionHandler(CreditInsufficientException.class)
    public ResponseEntity<ApiError> creditInsufficient(CreditInsufficientException exception) {
        String traceId = traceId();
        log.warn("이용권 부족 code={} traceId={}", exception.code(), traceId);
        return ResponseEntity.status(402).body(new ApiError(exception.code(), exception.getMessage(), traceId));
    }

    /** 외부 시스템(추론 서버 등)을 쓸 수 없을 때. 상태·코드·문구는 예외가 정하고, 내부 원인은 로그에만 남긴다. */
    @ExceptionHandler(ExternalServiceException.class)
    public ResponseEntity<ApiError> externalServiceUnavailable(ExternalServiceException exception) {
        String traceId = traceId();
        log.error("외부 연동 이용 불가 code={} traceId={} reason={}", exception.code(), traceId, exception.getMessage());
        return ResponseEntity.status(exception.status()).body(new ApiError(exception.code(), exception.publicMessage(), traceId));
    }

    /** 결제 대행사가 승인을 거절했을 때. 스펙(API-PAY-03 422)에 따라 토스 오류 코드를 providerCode로 함께 내린다. */
    @ExceptionHandler(PaymentRejectedException.class)
    public ResponseEntity<PaymentRejectedError> paymentRejected(PaymentRejectedException exception) {
        String traceId = traceId();
        log.warn("결제 승인 거절 providerCode={} traceId={}", exception.providerCode(), traceId);
        return ResponseEntity.status(exception.status())
                .body(new PaymentRejectedError(exception.code(), exception.publicMessage(), traceId, exception.providerCode()));
    }

    /**
     * 요청 본문·파라미터 검증 실패. 본문을 읽지 못한 경우(JSON 형식 오류, 필수 값 누락)도 스펙의 400 VALIDATION_ERROR로 내린다.
     * 어느 값이 틀렸는지는 알려 주되 입력값 자체는 로그에 남기지 않는다.
     */
    @ExceptionHandler({MethodArgumentNotValidException.class, HandlerMethodValidationException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ApiError> invalidRequest(Exception exception) {
        String traceId = traceId();
        log.warn("요청 검증 실패 type={} traceId={}", exception.getClass().getSimpleName(), traceId);
        return ResponseEntity.status(400).body(new ApiError("VALIDATION_ERROR", "요청 값이 올바르지 않습니다.", traceId));
    }

    private static String traceId() {
        String requestId = MDC.get(MdcRequestIdFilter.MDC_KEY);
        return requestId != null ? requestId : UUID.randomUUID().toString();
    }

    public record ApiError(String code, String message, String traceId) {}

    public record PaymentRejectedError(String code, String message, String traceId, String providerCode) {}
}
