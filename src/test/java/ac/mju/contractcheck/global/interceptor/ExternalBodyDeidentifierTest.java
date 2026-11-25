package ac.mju.contractcheck.global.interceptor;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ExternalBodyDeidentifierTest {

    private final ExternalBodyDeidentifier deidentifier = new ExternalBodyDeidentifier();

    private String describe(String body) {
        return deidentifier.describe(body.getBytes(StandardCharsets.UTF_8), "application/json");
    }

    @Test
    @DisplayName("구조와 정해 둔 값만 남기고 본문 문자열은 길이만 남긴다")
    void keepsStructureAndAllowedValuesOnly() {
        assertThat(describe("""
                {"api":"2.0","content":{"text":"임대인: 홍길동"},"model":"document-parse-260930","usage":{"pages":2},
                 "elements":[{"id":7,"category":"paragraph","ok":true,"none":null}]}"""))
                .isEqualTo("{\"api\":\"2.0\",\"content\":{\"text\":\"(문자열 8자)\"},\"model\":\"document-parse-260930\","
                        + "\"usage\":{\"pages\":2},\"elements\":[{\"id\":\"(숫자)\",\"category\":\"paragraph\",\"ok\":true,\"none\":null}]}");
    }

    @Test
    @DisplayName("정규식 마스킹이 놓치는 이름·띄어 쓴 주민번호·계좌 숫자와 키도 남지 않는다")
    void unlistedValuesNeverAppear() {
        String result = describe("""
                {"text":"성 명 : 홍길동 900101 - 1234567","customerName":"김가상","paymentKey":"pay_key_1",
                 "account":3333011234567,"digits":[9,0,0,1,0,1],"message":"card 4330-1234-5678-9012",
                 "error":{"code":"REJECT_CARD_PAYMENT","message":"홍길동 카드 거절"}}""");

        assertThat(result).contains("REJECT_CARD_PAYMENT", "(숫자 6개)")
                .doesNotContain("홍길동", "900101", "1234567", "김가상", "pay_key_1", "3333011234567", "4330");
    }

    @Test
    @DisplayName("남기는 필드라도 값이 문장이면 가리고, 식별자 모양이 아닌 필드 이름도 가린다")
    void freeTextInAllowedFieldAndOddFieldNamesAreHidden() {
        assertThat(describe("{\"type\":\"홍길동 계약\",\"status\":\"has space\",\"홍길동\":1}"))
                .isEqualTo("{\"type\":\"(문자열 6자)\",\"status\":\"(문자열 9자)\",\"(필드)\":\"(숫자)\"}");
    }

    @ParameterizedTest
    @ValueSource(strings = {"<html>홍길동</html>", "\"홍길동\"", "{\"text\":\"홍길동"})
    @DisplayName("JSON 객체나 배열이 아니면 크기만 남긴다")
    void nonJsonIsSizeOnly(String body) {
        assertThat(describe(body)).startsWith("(본문 생략 ").doesNotContain("홍길동");
    }
}
