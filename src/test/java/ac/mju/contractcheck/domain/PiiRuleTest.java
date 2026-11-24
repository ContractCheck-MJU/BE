package ac.mju.contractcheck.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 마스킹 규칙 한 건의 정합성과 치환. Spring 없이 순수 객체로 검증한다. */
class PiiRuleTest {

    private static PiiRule rule(String id, String regex, String replacement) {
        return new PiiRule(id, "설명", Pattern.compile(regex), replacement, false);
    }

    @Test
    @DisplayName("FR-UPL-04 규칙은 패턴에 맞는 모든 부분을 치환 문자로 바꾼다")
    void replacesEveryMatch() {
        PiiRule rule = rule("phone", "\\d{3}-\\d{4}", "[번호]");

        assertThat(rule.apply("010-1234 그리고 011-5678")).isEqualTo("[번호] 그리고 [번호]");
        assertThat(rule.count("010-1234 그리고 011-5678")).isEqualTo(2);
        assertThat(rule.matches("없음")).isFalse();
    }

    @Test
    @DisplayName("FR-UPL-04 캡처 그룹 참조($1)로 앞 문맥은 남기고 값만 치환할 수 있다")
    void keepsContextWithGroupReference() {
        PiiRule rule = rule("account", "(계좌\\s*:\\s*)\\d+", "$1[계좌번호]");

        assertThat(rule.apply("계좌 : 12345")).isEqualTo("계좌 : [계좌번호]");
    }

    @Test
    @DisplayName("FR-UPL-04 정합성: id·치환 문자가 비어 있거나 없는 그룹을 참조하면 만들 수 없다")
    void rejectsInvalidRules() {
        assertThatThrownBy(() -> rule(" ", "a", "[x]")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> rule("id", "a", "")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> rule("id", "a", "$1")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("id");
    }
}
