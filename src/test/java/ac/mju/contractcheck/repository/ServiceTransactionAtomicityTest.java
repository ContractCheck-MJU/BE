package ac.mju.contractcheck.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;

import ac.mju.contractcheck.client.StorageClient;
import ac.mju.contractcheck.domain.Clause;
import ac.mju.contractcheck.domain.Contract;
import ac.mju.contractcheck.domain.LedgerType;
import ac.mju.contractcheck.domain.OAuthProfile;
import ac.mju.contractcheck.domain.UserId;
import ac.mju.contractcheck.service.AuthService;
import ac.mju.contractcheck.service.ClauseService;
import ac.mju.contractcheck.service.ContractService;
import ac.mju.contractcheck.service.CreditService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * 한 유스케이스 안의 여러 쓰기가 service의 트랜잭션으로 함께 성공·실패하는지 확인한다. 저장소는 트랜잭션을 열지 않으므로
 * 이 보장은 service에서만 나온다. 패키지 비공개 JPA 저장소를 spy해서 중간 실패를 만들기 위해 repository 패키지에 둔다.
 * 클래스 단위 @Transactional을 쓰지 않아서(테스트 트랜잭션이 가리지 않도록) service가 실제로 롤백하는지 본다.
 */
@SpringBootTest
@ActiveProfiles("test")
class ServiceTransactionAtomicityTest {

    private static final String THREE_ARTICLES = "제1조(목적) 이 계약은 임대차에 관한 것이다.\n"
            + "제2조(보증금) 보증금은 1,000만원으로 한다.\n"
            + "제3조(계약기간) 계약기간은 1년으로 한다.";

    @Autowired
    AuthService auth;
    @Autowired
    ContractService contracts;
    @Autowired
    ClauseService clauses;
    @Autowired
    CreditService credits;
    @MockitoBean
    StorageClient storage;
    @MockitoSpyBean
    ClauseJpaRepository clauseRows;
    @MockitoSpyBean
    CreditRepository creditRepository;

    @Test
    @DisplayName("조항 재분할의 저장이 실패하면 먼저 지운 기존 조항이 그대로 남는다(삭제와 저장이 한 트랜잭션)")
    void splitIsAllOrNothing() {
        UserId owner = newOwner();
        Contract contract = contracts.upload(owner, pdf(), "제목", "MONTHLY");
        List<Long> originalIds = clauses.split(owner, contract.id(), THREE_ARTICLES).clauses().stream().map(Clause::id).toList();
        doThrow(new DataIntegrityViolationException("강제 실패")).when(clauseRows).saveAll(anyIterable());

        assertThatThrownBy(() -> clauses.split(owner, contract.id(), "제1조(새 조항) 내용이다."))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(clauses.list(owner, contract.id()))
                .extracting(Clause::id)
                .as("삭제가 롤백되어 기존 조항 3개가 그대로 있다")
                .containsExactlyElementsOf(originalIds);
    }

    @Test
    @DisplayName("TC-CRD-005 낙관적 락 충돌 뒤 재시도는 새 트랜잭션에서 깨끗이 다시 읽어 차감이 정확히 1회만 일어난다")
    void creditUseRetriesInFreshTransaction() {
        UserId owner = newOwner();
        // 첫 시도는 지갑 저장을 마치고 원장까지 실제로 기록한 뒤 충돌 예외를 던진다. 시도마다 트랜잭션이 따로여야 이 변경이 롤백되고,
        // 재시도 루프 전체가 한 트랜잭션이면 변경이 남아 두 번째 시도가 같은 지갑을 한 번 더 차감한다.
        doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new ObjectOptimisticLockingFailureException(CreditWalletEntity.class, owner.value());
        }).doCallRealMethod().when(creditRepository).appendLedger(any(), eq(LedgerType.USE), anyInt(), any(), any(), any());

        credits.use(owner, 1L);

        assertThat(credits.me(owner).freeRemaining()).as("가입 무료 2회에서 정확히 1회만 차감").isEqualTo(1);
        assertThat(credits.ledger(owner, 0, 20).items()).filteredOn(entry -> entry.type() == LedgerType.USE).hasSize(1);
    }

    private UserId newOwner() {
        String providerUserId = UUID.randomUUID().toString();
        return auth.signIn(new OAuthProfile("kakao", providerUserId, providerUserId + "@example.com", "닉네임")).id();
    }

    private MockMultipartFile pdf() {
        byte[] content = new byte[1024];
        content[0] = '%';
        content[1] = 'P';
        content[2] = 'D';
        content[3] = 'F';
        return new MockMultipartFile("file", "lease.pdf", "application/pdf", content);
    }
}
