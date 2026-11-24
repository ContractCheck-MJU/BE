package ac.mju.contractcheck.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ac.mju.contractcheck.client.StorageClient;
import ac.mju.contractcheck.domain.Clause;
import ac.mju.contractcheck.domain.Contract;
import ac.mju.contractcheck.domain.ContractState;
import ac.mju.contractcheck.domain.Member;
import ac.mju.contractcheck.domain.MemberRole;
import ac.mju.contractcheck.domain.MemberStatus;
import ac.mju.contractcheck.domain.OAuthProfile;
import ac.mju.contractcheck.domain.UserId;
import ac.mju.contractcheck.global.error.ConflictException;
import ac.mju.contractcheck.repository.AnalysisRepository;
import ac.mju.contractcheck.repository.ClauseRepository;
import ac.mju.contractcheck.repository.ContractRepository;
import ac.mju.contractcheck.repository.MemberRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 여러 저장소에 걸친 삭제·교체의 순서는 service가 정한다. 저장소는 한 테이블에 대한 단순 저장·삭제만 하는 가짜(mock)로 두고,
 * Spring·DB 없이 service가 어떤 순서로 저장소와 저장소(S3), 트랜잭션을 부르는지 확인한다.
 */
class ServiceOrchestrationTest {

    private static final UserId OWNER = new UserId(7);

    private final ContractRepository contractRepository = mock(ContractRepository.class);
    private final ClauseRepository clauseRepository = mock(ClauseRepository.class);
    private final AnalysisRepository analysisRepository = mock(AnalysisRepository.class);
    private final StorageClient storage = mock(StorageClient.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private TransactionTemplate transaction;

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        transaction = new TransactionTemplate(transactionManager);
    }

    @Test
    @DisplayName("계약서 삭제: 원본(S3) 삭제 → 트랜잭션 시작 → 조항 삭제 → 계약 삭제 순서다")
    void contractDeleteOrder() {
        Contract contract = new Contract(5L, OWNER, "제목", "MONTHLY", "contracts/7/k", ContractState.UPLOADED, "", Instant.now(), null);
        when(contractRepository.findOwned(5L, OWNER)).thenReturn(Optional.of(contract));
        ContractService service = new ContractService(
                contractRepository, clauseRepository, analysisRepository, mock(CreditService.class), storage, transaction,
                mock(ApplicationEventPublisher.class));

        service.delete(OWNER, 5L);

        InOrder order = inOrder(storage, transactionManager, clauseRepository, contractRepository);
        order.verify(storage).delete("contracts/7/k");
        order.verify(transactionManager).getTransaction(any());
        order.verify(clauseRepository).deleteByContract(5L);
        order.verify(contractRepository).delete(5L);
    }

    @Test
    @DisplayName("API-CON-06 분석 진행 중인 계약서는 삭제할 수 없다(409 ANALYSIS_IN_PROGRESS). 원본도 지우지 않는다")
    void contractDeleteIsRejectedWhileAnalyzing() {
        Contract contract = new Contract(5L, OWNER, "제목", "MONTHLY", "contracts/7/k", ContractState.SPLIT, "", Instant.now(), null);
        when(contractRepository.findOwned(5L, OWNER)).thenReturn(Optional.of(contract));
        when(analysisRepository.existsInProgress(5L)).thenReturn(true);
        ContractService service = new ContractService(
                contractRepository, clauseRepository, analysisRepository, mock(CreditService.class), storage, transaction,
                mock(ApplicationEventPublisher.class));

        assertThatThrownBy(() -> service.delete(OWNER, 5L))
                .isInstanceOfSatisfying(ConflictException.class, e -> assertThat(e.code()).isEqualTo("ANALYSIS_IN_PROGRESS"));

        verify(storage, never()).delete(any());
        verify(contractRepository, never()).delete(5L);
    }

    @Test
    @DisplayName("API-CLA-02 분석 진행 중인 계약의 조항은 수정할 수 없다(409 ANALYSIS_IN_PROGRESS)")
    void clauseUpdateIsRejectedWhileAnalyzing() {
        Clause clause = new Clause(9L, 5L, OWNER, 1, "제1조", "본문", null);
        when(clauseRepository.findOwned(9L, OWNER)).thenReturn(Optional.of(clause));
        when(analysisRepository.existsInProgress(5L)).thenReturn(true);
        ClauseService service = new ClauseService(clauseRepository, mock(ContractService.class), analysisRepository);

        assertThatThrownBy(() -> service.update(OWNER, 9L, null, null, "수정"))
                .isInstanceOfSatisfying(ConflictException.class, e -> assertThat(e.code()).isEqualTo("ANALYSIS_IN_PROGRESS"));

        verify(clauseRepository, never()).save(any());
    }

    @Test
    @DisplayName("조항 재분할: 소유 확인 → 기존 조항 삭제 → 새 조항 저장 순서다")
    void clauseSplitOrder() {
        ContractService contracts = mock(ContractService.class);
        ClauseService service = new ClauseService(clauseRepository, contracts, analysisRepository);
        when(clauseRepository.saveAll(eq(5L), any(), anyList())).thenReturn(List.of());

        service.split(OWNER, 5L, "제1조(목적) 이 계약은 임대차에 관한 것이다.");

        InOrder order = inOrder(contracts, clauseRepository);
        order.verify(contracts).get(OWNER, 5L);
        order.verify(clauseRepository).deleteByContract(5L);
        order.verify(clauseRepository).saveAll(eq(5L), any(), anyList());
    }

    @Test
    @DisplayName("회원 탈퇴: 조항 삭제 → 계약 삭제 → 탈퇴 처리 → 소셜 계정 연결 삭제 순서이고 원본(S3)은 그보다 먼저다")
    void withdrawOrder() {
        MemberRepository members = mock(MemberRepository.class);
        Member member = new Member(OWNER, "a@test.com", "닉네임", "kakao", MemberRole.USER, MemberStatus.ACTIVE, true, Instant.now());
        when(members.findById(OWNER)).thenReturn(Optional.of(member));
        when(contractRepository.findS3KeysByOwner(OWNER)).thenReturn(List.of("contracts/7/a"));
        MemberService service = new MemberService(
                members, contractRepository, clauseRepository, List.of(), storage,
                mock(FindByIndexNameSessionRepository.class), transaction);

        service.withdraw(OWNER);

        InOrder order = inOrder(storage, clauseRepository, contractRepository, members);
        order.verify(storage).delete("contracts/7/a");
        order.verify(clauseRepository).deleteAllByOwner(OWNER);
        order.verify(contractRepository).deleteAllByOwner(OWNER);
        order.verify(members).save(argThat(
                saved -> saved.status() == MemberStatus.WITHDRAWN && saved.email() == null && saved.nickname() == null));
        order.verify(members).deleteSocialAccounts(OWNER);
    }

    @Test
    @DisplayName("신규 가입: 기본 권한·상태(USER·ACTIVE)를 service가 정해 회원 저장 → 소셜 연결 저장 → 무료 지급 순서로 호출한다")
    void signUpOrder() {
        MemberRepository members = mock(MemberRepository.class);
        CreditService credits = mock(CreditService.class);
        OAuthProfile profile = new OAuthProfile("kakao", "k-1", "a@test.com", "닉네임");
        Member created = new Member(OWNER, "a@test.com", "닉네임", "kakao", MemberRole.USER, MemberStatus.ACTIVE, true, Instant.now());
        when(members.findByProviderAccount("kakao", "k-1")).thenReturn(Optional.empty());
        when(members.insert(profile, MemberRole.USER, MemberStatus.ACTIVE)).thenReturn(created);
        AuthService service = new AuthService(mock(MemberService.class), members, credits);

        Member signedIn = service.signIn(profile);

        InOrder order = inOrder(members, credits);
        order.verify(members).insert(eq(profile), eq(MemberRole.USER), eq(MemberStatus.ACTIVE));
        order.verify(members).linkSocialAccount(OWNER, profile);
        order.verify(credits).grantSignup(OWNER);
        assertThat(signedIn).isEqualTo(created);
    }
}
