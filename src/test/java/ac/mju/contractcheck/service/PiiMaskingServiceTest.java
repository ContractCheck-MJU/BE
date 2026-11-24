package ac.mju.contractcheck.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ac.mju.contractcheck.domain.PiiRule;
import ac.mju.contractcheck.repository.PiiRuleRepository;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

/** 기본 규칙 파일(masking/pii-rules.yml)을 그대로 읽어 마스킹한다. Spring 컨텍스트 없이 규칙과 서비스만 연결한다. */
class PiiMaskingServiceTest {

    private final PiiMaskingService service = new PiiMaskingService(
            new PiiRuleRepository(new DefaultResourceLoader(), "classpath:masking/pii-rules.yml"));

    @Test
    @DisplayName("TC-UPL-014 이름→[이름], 주소→[주소], 주민번호·전화번호·계좌번호는 토큰으로 치환되고 원문 숫자가 남지 않는다")
    void masksSpecSentence() {
        String text = "임대인 홍길동(800101-1234567) 010-1234-5678, 국민은행 123-45-678901, 서울시 마포구 연남로 12, 301호";

        String masked = service.mask(text);

        assertThat(masked).isEqualTo("임대인 [이름]([주민번호]) [전화번호], 국민은행 [계좌번호], [주소]");
        assertThat(masked).doesNotContainPattern("\\d{6}").doesNotContain("홍길동", "연남로", "301");
    }

    @Test
    @DisplayName("TC-UPL-014 보증금·월세·날짜·조문 번호 같은 계약 정보는 바뀌지 않는다")
    void keepsContractInformation() {
        String text = "제3조(보증금) 보증금은 금 10,000,000원, 월세는 500,000원으로 한다. "
                + "계약기간은 2026.11.24부터 2028.11.23까지이고 2026-11-24에 체결한다. 제1항 제2호에 따른다. "
                + "임대인은 임차인에게 보증금을 반환한다. 임대인 소유의 건물이다.";

        assertThat(service.mask(text)).isEqualTo(text);
    }

    @Test
    @DisplayName("FR-UPL-04 주민등록번호는 하이픈이 없어도, 외국인등록번호도 가린다")
    void masksResidentNumberVariants() {
        assertThat(service.mask("주민번호 8001011234567 입니다")).isEqualTo("주민번호 [주민번호] 입니다");
        assertThat(service.mask("외국인 900101-5234567")).isEqualTo("외국인 [주민번호]");
        assertThat(service.mask("생년월일 800101")).isEqualTo("생년월일 800101");
    }

    @Test
    @DisplayName("FR-UPL-04 휴대전화·지역번호 전화번호를 표기 방식과 상관없이 가린다")
    void masksPhoneVariants() {
        assertThat(service.mask("01012345678, 010 1234 5678, 010.1234.5678")).isEqualTo("[전화번호], [전화번호], [전화번호]");
        assertThat(service.mask("사무실 02-123-4567, 031-1234-5678")).isEqualTo("사무실 [전화번호], [전화번호]");
    }

    @Test
    @DisplayName("FR-UPL-04 은행·계좌 표시 뒤의 숫자와 하이픈 계좌번호를 가리되 표시 없는 큰 금액은 건드리지 않는다")
    void masksAccountNumbers() {
        assertThat(service.mask("국민은행 계좌번호: 12345678901234")).isEqualTo("국민은행 계좌번호: [계좌번호]");
        assertThat(service.mask("농협 356-1234-5678-93")).isEqualTo("농협 [계좌번호]");
        assertThat(service.mask("보증금 100000000원")).isEqualTo("보증금 100000000원");
    }

    @Test
    @DisplayName("FR-UPL-04 호칭 뒤의 이름을 가린다(조사가 붙어도, 괄호 표기여도, 줄 끝이어도)")
    void masksNamesAfterRoleWords() {
        assertThat(service.mask("성명 : 김철수")).isEqualTo("성명 : [이름]");
        assertThat(service.mask("임차인(을) 이영희 (인)")).isEqualTo("임차인(을) [이름] (인)");
        assertThat(service.mask("임차인 김철수는 보증금을 낸다")).isEqualTo("임차인 [이름]는 보증금을 낸다");
        assertThat(service.mask("임대인\n박지민\n끝")).isEqualTo("임대인\n[이름]\n끝");
    }

    @Test
    @DisplayName("FR-UPL-04 도로명·지번·동호 주소를 가린다")
    void masksAddresses() {
        assertThat(service.mask("소재지 서울특별시 마포구 연남로 12, 301호 입니다")).isEqualTo("소재지 [주소] 입니다");
        assertThat(service.mask("경기도 수원시 팔달구 매산로 45-1 2층")).isEqualTo("[주소]");
        assertThat(service.mask("서울시 마포구 연남동 123-4번지")).isEqualTo("[주소]");
        assertThat(service.mask("101동 202호에 거주")).isEqualTo("[주소]에 거주");
    }

    @Test
    @DisplayName("FR-UPL-04 여러 줄·여러 건이 섞인 본문도 모두 가리고 줄바꿈은 유지한다")
    void masksMultilineText() {
        String masked = service.mask("제1조(목적)\n임대인 홍길동(800101-1234567)\n임차인 김철수(010-9999-8888)\n끝");

        assertThat(masked).isEqualTo("제1조(목적)\n임대인 [이름]([주민번호])\n임차인 [이름]([전화번호])\n끝");
    }

    @Test
    @DisplayName("FR-UPL-04 maskAll은 입력과 같은 순서·개수로 돌려주고 빈 문자열은 그대로 둔다")
    void maskAllKeepsOrderAndBlank() {
        List<String> masked = service.maskAll(List.of("010-1234-5678", "", "  ", "개인정보 없음"));

        assertThat(masked).containsExactly("[전화번호]", "", "  ", "개인정보 없음");
    }

    @Test
    @DisplayName("TC-UPL-014 마스킹 뒤에도 verify 패턴이 남으면 MaskingException이고, 메시지에 원문이 들어가지 않는다")
    void failsClosedWhenPatternRemains() {
        PiiRule leaky = new PiiRule("leaky", "치환 결과에도 패턴이 남는 규칙", Pattern.compile("\\d{4}-\\d{4}"), "1234-5678", true);
        PiiMaskingService strict = new PiiMaskingService(PiiRuleRepository.of(List.of(leaky)));

        assertThatThrownBy(() -> strict.mask("비밀 9999-8888 입니다"))
                .isInstanceOf(MaskingException.class)
                .hasMessageContaining("leaky")
                .hasMessageNotContaining("9999").hasMessageNotContaining("비밀");
        assertThatThrownBy(() -> strict.maskAll(List.of("정상", "9999-8888"))).isInstanceOf(MaskingException.class);
    }

    @Test
    @DisplayName("FR-UPL-04 정규식 엔진이 스택 오버플로로 실패해도 MaskingException으로 끝나 원문이 전송되지 않는다")
    void wrapsRegexEngineFailure() {
        PiiRule deep = new PiiRule("deep-recursion", "아주 긴 입력에서 정규식 엔진이 실패하는 규칙", Pattern.compile("(?:a|b)*c"), "[X]", false);
        PiiMaskingService fragile = new PiiMaskingService(PiiRuleRepository.of(List.of(deep)));
        String hugeText = "ab".repeat(500_000);

        assertThatThrownBy(() -> fragile.mask(hugeText)).isInstanceOf(MaskingException.class)
                .hasMessageContaining("deep-recursion").hasMessageNotContaining("ab");
    }
}
