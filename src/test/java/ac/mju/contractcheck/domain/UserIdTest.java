package ac.mju.contractcheck.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UserIdTest {

    @Test
    @DisplayName("정합성: 양수만 회원 ID가 될 수 있다")
    void acceptsOnlyPositiveValues() {
        assertThat(new UserId(1).value()).isEqualTo(1L);
        assertThatThrownBy(() -> new UserId(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new UserId(-5)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("인증 주체 이름(users.id 문자열)에서 만든다. 앞뒤 공백은 무시한다")
    void parsesPrincipalName() {
        assertThat(UserId.parse("7")).contains(new UserId(7));
        assertThat(UserId.parse(" 42 ")).contains(new UserId(42));
    }

    @Test
    @DisplayName("숫자가 아니거나 양수가 아니거나 범위를 넘으면 없는 회원으로 본다(예외 없음)")
    void invalidPrincipalNamesAreAbsent() {
        for (String bad : new String[] {null, "", "  ", "abc", "7.5", "0", "-3", "99999999999999999999", "1 2"}) {
            assertThat(UserId.parse(bad)).as("입력 '%s'", bad).isEqualTo(Optional.empty());
        }
    }

    @Test
    @DisplayName("세션 principal_name 값으로 되돌릴 수 있고 로그에는 숫자만 보인다")
    void principalNameRoundTrip() {
        UserId id = new UserId(7);

        assertThat(id.principalName()).isEqualTo("7");
        assertThat(UserId.parse(id.principalName())).contains(id);
        assertThat(id).hasToString("7");
    }

    @Test
    @DisplayName("값이 같으면 같은 회원이다")
    void equalityByValue() {
        assertThat(new UserId(3)).isEqualTo(new UserId(3)).isNotEqualTo(new UserId(4)).hasSameHashCodeAs(new UserId(3));
    }
}
