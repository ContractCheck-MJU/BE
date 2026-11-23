package ac.mju.contractcheck.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 가입 무료 횟수와 원장 값. enum 이름은 DB에 저장되는 문자열(credit_ledger.type, ref_type)이라 바꾸면 기존 데이터와 어긋난다. */
class CreditLedgerValuesTest {

    @Test
    @DisplayName("TC-CRD-001 가입 무료 지급은 2회다")
    void signupFreeCreditsIsTwo() {
        assertThat(CreditPolicy.SIGNUP_FREE_CREDITS).isEqualTo(2);
    }

    @Test
    @DisplayName("원장 종류의 이름은 스펙의 DB 값(GRANT·USE·REFUND·RESTORE)과 같다")
    void ledgerTypeNamesAreDatabaseValues() {
        assertThat(Arrays.stream(LedgerType.values()).map(Enum::name))
                .containsExactlyInAnyOrder("GRANT", "USE", "REFUND", "RESTORE");
    }

    @Test
    @DisplayName("원장 참조 종류의 이름은 스펙의 DB 값(PAYMENT·ANALYSIS)과 같다")
    void ledgerRefTypeNamesAreDatabaseValues() {
        assertThat(Arrays.stream(LedgerRefType.values()).map(Enum::name))
                .containsExactlyInAnyOrder("PAYMENT", "ANALYSIS");
    }
}
