package ac.mju.contractcheck.global.interceptor;

import static org.assertj.core.api.Assertions.assertThat;

import ac.mju.contractcheck.domain.PiiRule;
import ac.mju.contractcheck.repository.PiiRuleRepository;
import ac.mju.contractcheck.service.PiiMaskingService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

class ExternalBodySanitizerTest {

    private final ExternalBodySanitizer sanitizer = new ExternalBodySanitizer(new PiiMaskingService(
            new PiiRuleRepository(new DefaultResourceLoader(), "classpath:masking/pii-rules.yml")));

    private String sanitize(String body, String contentType) {
        return sanitizer.sanitize(body.getBytes(StandardCharsets.UTF_8), contentType);
    }

    @Test
    @DisplayName("JSON 문자열의 주민번호·전화번호는 마스킹하고 구조와 숫자는 남긴다")
    void masksPersonalDataInsideJsonStrings() {
        String result = sanitize("""
                {"content":{"text":"주민등록번호 900101-1234567\\n전화 010-0000-0000"},"usage":{"pages":2}}""", "application/json");

        assertThat(result).contains("[주민번호]", "[전화번호]", "\"usage\":{\"pages\":2}")
                .doesNotContain("900101-1234567", "010-0000-0000");
    }

    @Test
    @DisplayName("키·토큰·결제 키와 이름·이메일·카드 번호 필드는 값을 지우고 URL은 호스트까지만 남긴다")
    void removesSecretsAndPersonalFields() {
        String result = sanitize("""
                {"paymentKey":"pay_key_1","secret":"ps_secret","access_token":"AT-1","customerName":"홍길동",
                 "customerEmail":"a@example.com","card":{"number":"43301234****123*","amount":4900},
                 "receipt":{"url":"https://dashboard.tosspayments.com/receipt?transactionId=pay_key_1"},
                 "code":"REJECT_CARD_PAYMENT","status":"DONE"}""", "application/json;charset=UTF-8");

        assertThat(result).contains("[제거]", "[개인정보]", "\"amount\":4900", "REJECT_CARD_PAYMENT", "\"status\":\"DONE\"",
                        "https://dashboard.tosspayments.com/…")
                .doesNotContain("pay_key_1", "ps_secret", "AT-1", "홍길동", "a@example.com", "43301234");
    }

    @Test
    @DisplayName("임베딩 벡터와 긴 인코딩 문자열, 파일 본문은 크기만 남긴다")
    void summarizesLargeValues() {
        String vector = IntStream.range(0, 1024).mapToObj(i -> "0.5").collect(Collectors.joining(","));

        assertThat(sanitize("{\"vectors\":[[" + vector + "]],\"encoded\":\"" + "A".repeat(600) + "\"}", "application/json"))
                .isEqualTo("{\"vectors\":[\"(숫자 1024개)\"],\"encoded\":\"(문자열 600자)\"}");
        assertThat(sanitizer.sanitize(new byte[2048], "multipart/form-data;boundary=x")).isEqualTo("(본문 생략 2048 bytes)");
        assertThat(sanitizer.sanitize(new byte[10], "application/octet-stream")).isEqualTo("(본문 생략 10 bytes)");
    }

    @Test
    @DisplayName("JSON이 아닌 텍스트도 마스킹해 한 줄로 남기고, 너무 길면 자른다")
    void masksAndClipsPlainText() {
        assertThat(sanitize("<html>\n전화 010-0000-0000", "text/html")).contains("\\n", "[전화번호]").doesNotContain("\n", "010-0000");
        assertThat(sanitize("가 ".repeat(ExternalBodySanitizer.MAX_LENGTH), "text/plain"))
                .hasSizeLessThan(ExternalBodySanitizer.MAX_LENGTH + 50).contains("총 ");
    }

    @Test
    @DisplayName("마스킹에 실패하면 원문을 남기지 않는다")
    void maskingFailureDoesNotLeakOriginal() {
        PiiRule leaky = new PiiRule("leaky", "치환해도 남는 규칙", java.util.regex.Pattern.compile("비밀값"), "비밀값", true);
        ExternalBodySanitizer failing = new ExternalBodySanitizer(new PiiMaskingService(PiiRuleRepository.of(List.of(leaky))));

        assertThat(failing.sanitize("{\"text\":\"비밀값 원문\"}".getBytes(StandardCharsets.UTF_8), "application/json"))
                .isEqualTo("{\"text\":\"[마스킹 실패]\"}");
    }
}
