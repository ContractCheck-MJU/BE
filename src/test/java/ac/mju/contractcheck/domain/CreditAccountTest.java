package ac.mju.contractcheck.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ac.mju.contractcheck.domain.enums.CreditSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 이용권 차감 규칙. Spring·DB 없이 순수 객체로 검증한다. */
class CreditAccountTest {

    @Test
    @DisplayName("TC-CRD-002 무료가 있으면 무료부터 차감한다")
    void usesFreeFirst() {
        CreditAccount after = new CreditAccount(new UserId(1), 2, 3).deductOne().orElseThrow().account();

        assertThat(after.freeRemaining()).isEqualTo(1);
        assertThat(after.paidRemaining()).isEqualTo(3);
    }

    @Test
    @DisplayName("TC-CRD-003 무료를 다 쓰면 유료를 차감한다")
    void usesPaidWhenFreeIsEmpty() {
        CreditAccount after = new CreditAccount(new UserId(1), 0, 3).deductOne().orElseThrow().account();

        assertThat(after.freeRemaining()).isZero();
        assertThat(after.paidRemaining()).isEqualTo(2);
    }

    @Test
    @DisplayName("TC-CRD-004 둘 다 없으면 차감할 수 없다")
    void cannotUseWhenEmpty() {
        assertThat(new CreditAccount(new UserId(1), 0, 0).deductOne()).isEmpty();
    }

    @Test
    @DisplayName("차감은 원본을 바꾸지 않고 새 계정을 돌려준다")
    void deductOneDoesNotMutate() {
        CreditAccount before = new CreditAccount(new UserId(1), 1, 0);

        before.deductOne();

        assertThat(before.freeRemaining()).isEqualTo(1);
    }

    @Test
    @DisplayName("마지막 1회를 쓰면 0이 되고 그다음 차감은 불가하다")
    void lastCreditLeavesZeroNotNegative() {
        CreditAccount after = new CreditAccount(new UserId(1), 1, 0).deductOne().orElseThrow().account();

        assertThat(after.freeRemaining()).isZero();
        assertThat(after.deductOne()).isEmpty();
    }

    @Test
    @DisplayName("정합성: 잔여가 음수인 계정은 만들 수 없다")
    void rejectsNegativeBalances() {
        assertThatThrownBy(() -> new CreditAccount(new UserId(1), -1, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CreditAccount(new UserId(1), 0, -1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("낙관적 락에 쓰는 version은 차감해도 그대로 운반하고, 새 지갑은 version이 없다")
    void versionIsCarriedThroughUse() {
        assertThat(new CreditAccount(new UserId(1), 2, 0).version()).isNull();

        CreditAccount after = new CreditAccount(new UserId(1), 2, 0, 5L).deductOne().orElseThrow().account();

        assertThat(after.version()).isEqualTo(5L);
    }

    @Test
    @DisplayName("FR-CRD-02 deductOne은 무료 우선 차감 시 FREE, 유료 차감 시 PAID 출처를 함께 돌려준다")
    void deductOneReportsCreditSource() {
        CreditDeduction freeDeduction = new CreditAccount(new UserId(1), 1, 2).deductOne().orElseThrow();
        CreditDeduction paidDeduction = freeDeduction.account().deductOne().orElseThrow();

        assertThat(freeDeduction.source()).isEqualTo(CreditSource.FREE);
        assertThat(freeDeduction.account().freeRemaining()).isZero();
        assertThat(freeDeduction.account().paidRemaining()).isEqualTo(2);

        assertThat(paidDeduction.source()).isEqualTo(CreditSource.PAID);
        assertThat(paidDeduction.account().freeRemaining()).isZero();
        assertThat(paidDeduction.account().paidRemaining()).isEqualTo(1);
    }

    @Test
    @DisplayName("FR-CRD-02 restoreOne은 지정한 출처(FREE·PAID)의 잔여를 1회 복구하고 version을 유지한다")
    void restoreOneAddsBackToGivenSourceAndCarriesVersion() {
        CreditAccount base = new CreditAccount(new UserId(1), 0, 2, 9L);

        CreditAccount paidRestored = base.restoreOne(CreditSource.PAID);
        CreditAccount freeRestored = base.restoreOne(CreditSource.FREE);

        assertThat(paidRestored).isEqualTo(new CreditAccount(new UserId(1), 0, 3, 9L));
        assertThat(freeRestored).isEqualTo(new CreditAccount(new UserId(1), 1, 2, 9L));
    }
}
