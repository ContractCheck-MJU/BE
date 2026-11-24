package ac.mju.contractcheck.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import ac.mju.contractcheck.domain.CreditAccount;
import ac.mju.contractcheck.domain.CreditSource;
import ac.mju.contractcheck.domain.LedgerEntry;
import ac.mju.contractcheck.domain.LedgerRefType;
import ac.mju.contractcheck.domain.LedgerType;
import ac.mju.contractcheck.domain.PageSlice;
import ac.mju.contractcheck.domain.UserId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * 저장소는 판단 없이 읽고 쓰기만 한다. 차감 규칙이나 가입 정책은 CreditService와 domain이 정하므로, 여기서는
 * 받은 값이 그대로 저장되고 DB 값(문자열)으로 남는지만 확인한다. 클래스 단위 @Transactional로 테스트마다 롤백한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CreditRepositoryTest {

    private static final UserId OWNER = new UserId(990_001L);

    @Autowired
    CreditRepository credits;

    @Test
    @DisplayName("없는 지갑은 예외가 아니라 빈 값이다(지갑이 없는 회원을 어떻게 볼지는 service가 판단한다)")
    void missingWalletIsEmpty() {
        assertThat(credits.findAccount(new UserId(990_404L))).isEqualTo(Optional.empty());
    }

    @Test
    @DisplayName("받은 값 그대로 지갑을 만들고 읽는다(무료 몇 회를 줄지 저장소는 정하지 않는다)")
    void storesWhateverItIsGiven() {
        credits.createAccount(new CreditAccount(OWNER, 7, 3));

        assertThat(credits.findAccount(OWNER)).hasValueSatisfying(
                account -> assertThat(account).extracting(CreditAccount::freeRemaining, CreditAccount::paidRemaining).containsExactly(7, 3));
    }

    @Test
    @DisplayName("지갑 저장은 받은 잔여를 그대로 반영한다(차감 규칙을 적용하지 않는다)")
    void saveAccountWritesGivenBalances() {
        credits.createAccount(new CreditAccount(OWNER, 2, 0));
        CreditAccount loaded = credits.findAccount(OWNER).orElseThrow();

        credits.saveAccount(new CreditAccount(OWNER, 0, 9, loaded.version()));

        assertThat(credits.findAccount(OWNER)).hasValueSatisfying(
                account -> assertThat(account).extracting(CreditAccount::freeRemaining, CreditAccount::paidRemaining).containsExactly(0, 9));
    }

    @Test
    @DisplayName("읽은 뒤 다른 저장이 먼저 일어났으면(version이 낡았으면) 저장이 낙관적 락 충돌이다")
    void staleVersionIsRejected() {
        credits.createAccount(new CreditAccount(OWNER, 2, 0));
        CreditAccount readEarlier = credits.findAccount(OWNER).orElseThrow();
        credits.saveAccount(new CreditAccount(OWNER, 1, 0, readEarlier.version()));

        assertThatThrownBy(() -> credits.saveAccount(new CreditAccount(OWNER, 0, 0, readEarlier.version())))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    @DisplayName("원장 존재 여부는 종류·참조 종류·참조 ID가 모두 같을 때만 true다")
    void hasLedgerMatchesTypeAndReference() {
        credits.createAccount(new CreditAccount(OWNER, 2, 0));
        credits.appendLedger(OWNER, LedgerType.USE, 1, LedgerRefType.ANALYSIS, 9L, CreditSource.FREE);

        assertThat(credits.hasLedger(OWNER, LedgerType.USE, LedgerRefType.ANALYSIS, 9L)).isTrue();
        assertThat(credits.hasLedger(OWNER, LedgerType.RESTORE, LedgerRefType.ANALYSIS, 9L)).isFalse();
        assertThat(credits.hasLedger(OWNER, LedgerType.USE, LedgerRefType.ANALYSIS, 10L)).isFalse();
        assertThat(credits.hasLedger(OWNER, LedgerType.USE, LedgerRefType.PAYMENT, 9L)).isFalse();
    }

    @Test
    @DisplayName("원장은 종류와 참조의 이름이 DB 문자열로 저장되고, 참조가 없으면 비어 있다")
    void ledgerStoresEnumNamesAsDatabaseValues() {
        credits.appendLedger(OWNER, LedgerType.GRANT, 2, null, null, CreditSource.FREE);
        credits.appendLedger(OWNER, LedgerType.USE, 1, LedgerRefType.ANALYSIS, 55L, CreditSource.PAID);

        List<LedgerEntry> ledger = credits.findLedgerPage(OWNER, 0, 20).items();

        assertThat(ledger).extracting(LedgerEntry::type).containsExactlyInAnyOrder(LedgerType.GRANT, LedgerType.USE);
        assertThat(ledger).filteredOn(entry -> entry.type() == LedgerType.USE)
                .extracting(LedgerEntry::refType, LedgerEntry::refId)
                .containsExactly(tuple(LedgerRefType.ANALYSIS, "55"));
        assertThat(ledger).filteredOn(entry -> entry.type() == LedgerType.GRANT)
                .extracting(LedgerEntry::refType, LedgerEntry::refId)
                .containsExactly(tuple(null, ""));
    }

    @Test
    @DisplayName("원장 페이지는 최신순으로 잘라 전체 건수와 함께 돌려주고 타인 원장은 섞이지 않는다")
    void findLedgerPageSlicesNewestFirstAndIsolatesOwner() {
        UserId other = new UserId(990_002L);
        credits.appendLedger(OWNER, LedgerType.GRANT, 2, null, null, CreditSource.FREE);
        credits.appendLedger(OWNER, LedgerType.USE, 1, LedgerRefType.ANALYSIS, 11L, CreditSource.FREE);
        credits.appendLedger(other, LedgerType.GRANT, 2, null, null, CreditSource.FREE);
        credits.appendLedger(OWNER, LedgerType.USE, 1, LedgerRefType.ANALYSIS, 22L, CreditSource.FREE);

        PageSlice<LedgerEntry> firstPage = credits.findLedgerPage(OWNER, 0, 2);
        PageSlice<LedgerEntry> secondPage = credits.findLedgerPage(OWNER, 1, 2);
        PageSlice<LedgerEntry> emptyPage = credits.findLedgerPage(new UserId(990_003L), 0, 20);

        assertThat(firstPage.total()).isEqualTo(3);
        assertThat(firstPage.items()).extracting(LedgerEntry::refId).containsExactly("22", "11");
        assertThat(secondPage.total()).isEqualTo(3);
        assertThat(secondPage.items()).extracting(LedgerEntry::type).containsExactly(LedgerType.GRANT);
        assertThat(emptyPage.total()).isZero();
        assertThat(emptyPage.items()).isEmpty();
    }

    @Test
    @DisplayName("원장 존재 여부와 출처(FREE·PAID)를 회원·종류·참조 기준으로 조회한다")
    void hasLedgerAndFindLedgerSourceLookupByOwnerAndRef() {
        credits.appendLedger(OWNER, LedgerType.USE, 1, LedgerRefType.ANALYSIS, 71L, CreditSource.FREE);
        credits.appendLedger(OWNER, LedgerType.USE, 1, LedgerRefType.ANALYSIS, 72L, null);

        assertThat(credits.hasLedger(OWNER, LedgerType.USE, LedgerRefType.ANALYSIS, 71L)).isTrue();
        assertThat(credits.hasLedger(OWNER, LedgerType.RESTORE, LedgerRefType.ANALYSIS, 71L)).isFalse();
        assertThat(credits.hasLedger(new UserId(990_002L), LedgerType.USE, LedgerRefType.ANALYSIS, 71L)).isFalse();

        assertThat(credits.findLedgerSource(OWNER, LedgerType.USE, LedgerRefType.ANALYSIS, 71L)).contains(CreditSource.FREE);
        assertThat(credits.findLedgerSource(OWNER, LedgerType.USE, LedgerRefType.ANALYSIS, 72L)).isEmpty();
        assertThat(credits.findLedgerSource(OWNER, LedgerType.USE, LedgerRefType.ANALYSIS, 999L)).isEmpty();
    }
}
