package ac.mju.contractcheck.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ac.mju.contractcheck.domain.PiiRule;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;

/** 마스킹 규칙 파일을 읽고 검증하는 저장소. 잘못된 파일은 앱 시작 단계에서 바로 실패해야 한다. */
class PiiRuleRepositoryTest {

    private static final String DEFAULT_LOCATION = "classpath:masking/pii-rules.yml";

    @TempDir
    Path directory;

    private PiiRuleRepository load(String location) {
        return new PiiRuleRepository(new DefaultResourceLoader(), location);
    }

    private String write(String yaml) throws IOException {
        Path file = directory.resolve("rules.yml");
        Files.writeString(file, yaml);
        return file.toUri().toString();
    }

    @Test
    @DisplayName("TC-UPL-014 기본 규칙 파일에는 이름·주민번호·전화번호·계좌번호·주소 규칙이 순서대로 들어 있다")
    void defaultFileHasSpecRules() {
        List<PiiRule> rules = load(DEFAULT_LOCATION).findAll();

        assertThat(rules).extracting(PiiRule::id).contains(
                "resident-registration-number", "phone-number", "bank-account", "person-name", "address");
        assertThat(rules).extracting(PiiRule::id).doesNotHaveDuplicates();
        assertThat(rules).allSatisfy(rule -> assertThat(rule.description()).isNotBlank());
    }

    @Test
    @DisplayName("FR-UPL-04 규칙은 파일에 적힌 순서를 그대로 유지한다")
    void keepsFileOrder() throws IOException {
        String location = write("""
                rules:
                  - id: second-in-name-first-in-file
                    description: 첫째
                    pattern: 'a'
                    replacement: '[A]'
                  - id: b-rule
                    description: 둘째
                    pattern: 'b'
                    replacement: '[B]'
                    verify: true
                """);

        List<PiiRule> rules = load(location).findAll();

        assertThat(rules).extracting(PiiRule::id).containsExactly("second-in-name-first-in-file", "b-rule");
        assertThat(rules.get(0).verify()).isFalse();
        assertThat(rules.get(1).verify()).isTrue();
    }

    @Test
    @DisplayName("FR-UPL-04 정규식이 틀리면 어느 규칙인지 알리며 시작 단계에서 실패한다")
    void failsFastOnInvalidRegex() throws IOException {
        String location = write("""
                rules:
                  - id: broken
                    description: 틀린 정규식
                    pattern: '(unclosed'
                    replacement: '[X]'
                """);

        assertThatThrownBy(() -> load(location)).isInstanceOf(IllegalStateException.class).hasMessageContaining("broken");
    }

    @Test
    @DisplayName("FR-UPL-04 필수 항목이 없거나 id가 겹치거나 규칙이 하나도 없으면 시작 단계에서 실패한다")
    void failsFastOnMissingOrDuplicate() throws IOException {
        assertThatThrownBy(() -> load(write("rules:\n  - id: no-pattern\n    description: d\n    replacement: '[X]'\n")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("no-pattern");
        assertThatThrownBy(() -> load(write(
                "rules:\n  - {id: dup, description: d, pattern: 'a', replacement: '[A]'}\n"
                        + "  - {id: dup, description: d, pattern: 'b', replacement: '[B]'}\n")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("dup");
        assertThatThrownBy(() -> load(write("rules: []\n"))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> load("file:" + directory.resolve("none.yml"))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("FR-UPL-04 규칙 파일 위치를 바꾸면 코드 수정 없이 다른 규칙을 쓸 수 있다")
    void readsAnotherLocation() throws IOException {
        String location = write("rules:\n  - {id: only, description: d, pattern: 'x', replacement: '[X]'}\n");

        assertThat(load(location).findAll()).extracting(PiiRule::id).containsExactly("only");
    }
}
