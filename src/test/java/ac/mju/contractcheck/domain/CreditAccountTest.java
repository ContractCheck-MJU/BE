package ac.mju.contractcheck.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 이용권 차감 규칙. Spring·DB 없이 순수 객체로 검증한다. */
class CreditAccountTest {

    @Test
    @DisplayName("TC-CRD-002 무료가 있으면 무료부터 차감한다")
    void usesFreeFirst() {
        CreditAccount after = new CreditAccount("1", 2, 3).useOne().orElseThrow();

        assertThat(after.freeRemaining()).isEqualTo(1);
        assertThat(after.paidRemaining()).isEqualTo(3);
    }

    @Test
    @DisplayName("TC-CRD-003 무료를 다 쓰면 유료를 차감한다")
    void usesPaidWhenFreeIsEmpty() {
        CreditAccount after = new CreditAccount("1", 0, 3).useOne().orElseThrow();

        assertThat(after.freeRemaining()).isZero();
        assertThat(after.paidRemaining()).isEqualTo(2);
    }

    @Test
    @DisplayName("TC-CRD-004 둘 다 없으면 차감할 수 없다")
    void cannotUseWhenEmpty() {
        assertThat(new CreditAccount("1", 0, 0).useOne()).isEmpty();
    }

    @Test
    @DisplayName("차감은 원본을 바꾸지 않고 새 계정을 돌려준다")
    void useOneDoesNotMutate() {
        CreditAccount before = new CreditAccount("1", 1, 0);

        before.useOne();

        assertThat(before.freeRemaining()).isEqualTo(1);
    }

    @Test
    @DisplayName("마지막 1회를 쓰면 0이 되고 그다음 차감은 불가하다")
    void lastCreditLeavesZeroNotNegative() {
        CreditAccount after = new CreditAccount("1", 1, 0).useOne().orElseThrow();

        assertThat(after.freeRemaining()).isZero();
        assertThat(after.useOne()).isEmpty();
    }

    @Test
    @DisplayName("정합성: 잔여가 음수인 계정은 만들 수 없다")
    void rejectsNegativeBalances() {
        assertThatThrownBy(() -> new CreditAccount("1", -1, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CreditAccount("1", 0, -1)).isInstanceOf(IllegalArgumentException.class);
    }
}
