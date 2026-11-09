package ac.mju.contractcheck.global.error;

import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> notFound(NotFoundException exception) {
        String traceId = UUID.randomUUID().toString();
        log.warn("리소스 없음 code={} traceId={}", exception.code(), traceId);
        return ResponseEntity.status(404).body(new ApiError(exception.code(), exception.getMessage(), traceId));
    }

    public record ApiError(String code, String message, String traceId) {}
}
