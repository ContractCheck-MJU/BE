package ac.mju.contractcheck.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import ac.mju.contractcheck.client.StorageClient;
import ac.mju.contractcheck.domain.ClauseDraft;
import ac.mju.contractcheck.domain.Contract;
import ac.mju.contractcheck.domain.Analysis;
import ac.mju.contractcheck.domain.AnalysisStatus;
import ac.mju.contractcheck.domain.Comparison;
import ac.mju.contractcheck.domain.Explanation;
import ac.mju.contractcheck.domain.Member;
import ac.mju.contractcheck.domain.Milestone;
import ac.mju.contractcheck.domain.Notification;
import ac.mju.contractcheck.domain.NotificationSettings;
import ac.mju.contractcheck.domain.UserId;
import ac.mju.contractcheck.repository.memory.ComparisonRepository;
import ac.mju.contractcheck.repository.memory.ExplanationRepository;
import ac.mju.contractcheck.repository.memory.MilestoneRepository;
import ac.mju.contractcheck.repository.memory.NotificationRepository;
import ac.mju.contractcheck.domain.OAuthProfile;
import ac.mju.contractcheck.global.error.ConflictException;
import ac.mju.contractcheck.service.AuthService;
import ac.mju.contractcheck.service.MemberService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * FR-AUTH-03 회원 탈퇴. 관리자 행은 ADMIN 지정 경로가 스펙에 없어 패키지 비공개 JPA 저장소로 직접 만든다.
 * 저장소(SeaweedFS)는 mock한다. 클래스 단위 @Transactional이라 테스트마다 롤백되어 서로 데이터를 공유하지 않는다.
 * 트랜잭션 밖 호출을 확인하는 테스트만 트랜잭션 없이 돌고, 고유한 회원을 만들어 서로 겹치지 않는다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MemberWithdrawalTest {

    @Autowired
    MemberService members;

    @Autowired
    AuthService auth;

    @Autowired
    MemberJpaRepository users;

    @Autowired
    ContractRepository contracts;

    @Autowired
    ClauseRepository clauseRepository;

    @Autowired
    ClauseJpaRepository clauseRows;

    @Autowired
    FindByIndexNameSessionRepository<? extends Session> sessions;

    @Autowired
    AnalysisRepository analyses;
    @Autowired
    ComparisonRepository comparisons;
    @Autowired
    ExplanationRepository explanations;
    @Autowired
    MilestoneRepository milestones;
    @Autowired
    NotificationRepository notifications;
    @MockitoBean
    StorageClient storage;

    @Test
    @DisplayName("TC-AUTH-014 탈퇴하면 개인정보를 비우고 모든 기기의 세션을 지우며 이후 조회는 401을 응답한다")
    void withdrawalRemovesSessionsAndPersonalData() {
        Member member = signUp();
        UserId username = member.id();
        createSession(username);
        createSession(username);
        assertThat(sessions.findByPrincipalName(username.principalName())).hasSize(2);

        members.withdraw(username);

        assertThat(sessions.findByPrincipalName(username.principalName())).isEmpty();
        MemberEntity row = users.findById(member.id().value()).orElseThrow();
        assertThat(row.getStatus()).isEqualTo("WITHDRAWN");
        assertThat(row.getEmail()).isNull();
        assertThat(row.getNickname()).isNull();
        assertThatThrownBy(() -> members.me(username)).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    @Test
    @DisplayName("탈퇴 회원이 같은 소셜 계정으로 다시 로그인하면 새 회원으로 가입한다")
    void rejoinAfterWithdrawalCreatesNewMember() {
        OAuthProfile profile = profile();
        Member first = auth.signIn(profile);
        members.withdraw(first.id());

        Member second = auth.signIn(profile);

        assertThat(second.id()).isNotEqualTo(first.id());
        assertThat(second.email()).isEqualTo(profile.email());
    }

    @Test
    @DisplayName("TC-AUTH-015 마지막 ADMIN이 탈퇴하면 409 LAST_ADMIN을 응답하고 계정이 유지된다")
    void lastAdminCannotWithdraw() {
        long adminId = createAdmin();
        UserId username = new UserId(adminId);

        assertThatThrownBy(() -> members.withdraw(username))
                .isInstanceOf(ConflictException.class)
                .extracting(ex -> ((ConflictException) ex).code())
                .isEqualTo("LAST_ADMIN");

        assertThat(users.findById(adminId).orElseThrow().getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("ADMIN이 둘 이상이면 한 명은 탈퇴할 수 있다")
    void adminCanWithdrawWhenAnotherAdminExists() {
        long first = createAdmin();
        createAdmin();

        members.withdraw(new UserId(first));

        assertThat(users.findById(first).orElseThrow().getStatus()).isEqualTo("WITHDRAWN");
    }

    @Test
    @DisplayName("TC-AUTH-014 탈퇴하면 계약 행과 저장소 원본을 지우고 다른 회원의 계약은 그대로 둔다")
    void withdrawalDeletesContractsAndStoredOriginals() {
        Member member = signUp();
        UserId username = member.id();
        Contract first = saveContract(username, "contracts/" + username + "/first");
        Contract second = saveContract(username, "contracts/" + username + "/second");
        Contract withoutObject = saveContract(username, null);
        Member other = signUp();
        UserId otherName = other.id();
        Contract othersContract = saveContract(otherName, "contracts/" + otherName + "/kept");

        members.withdraw(username);

        verify(storage).delete(first.s3Key());
        verify(storage).delete(second.s3Key());
        verify(storage, never()).delete(othersContract.s3Key());
        assertThat(contracts.findOwned(first.id(), username)).isEmpty();
        assertThat(contracts.findOwned(second.id(), username)).isEmpty();
        assertThat(contracts.findOwned(withoutObject.id(), username)).isEmpty();
        assertThat(contracts.findOwned(othersContract.id(), otherName)).isPresent();
    }

    @Test
    @DisplayName("TC-AUTH-014 탈퇴하면 계약의 조항도 지우고 다른 회원의 조항은 그대로 둔다")
    void withdrawalDeletesClausesOfContracts() {
        Member member = signUp();
        UserId username = member.id();
        Contract contract = saveContract(username, "contracts/" + username + "/clauses");
        clauseRepository.saveAll(contract.id(), username, List.of(new ClauseDraft(1, "제1조(목적)", "본문"), new ClauseDraft(2, "제2조(보증금)", "본문")));
        Member other = signUp();
        UserId otherName = other.id();
        Contract othersContract = saveContract(otherName, "contracts/" + otherName + "/clauses");
        clauseRepository.saveAll(othersContract.id(), otherName, List.of(new ClauseDraft(1, "제1조(목적)", "본문")));

        members.withdraw(username);

        assertThat(clauseRows.findAll()).extracting(ClauseEntity::getContractId)
                .doesNotContain(contract.id())
                .contains(othersContract.id());
    }

    @Test
    @DisplayName("TC-AUTH-014 탈퇴하면 분석·비교·설명·일정·알림(설정 포함)을 지우고 다른 회원의 것은 그대로 둔다")
    void withdrawalDeletesDependentData() {
        Member member = signUp();
        Member other = signUp();
        UserId owner = member.id();
        UserId otherOwner = other.id();
        Seeded mine = seed(owner);
        Seeded theirs = seed(otherOwner);

        members.withdraw(owner);

        assertThat(analyses.findOwned(mine.analysis(), owner)).isEmpty();
        assertThat(comparisons.findOwned(mine.comparison(), owner)).isEmpty();
        assertThat(explanations.findOwned(mine.explanation(), owner)).isEmpty();
        assertThat(milestones.findByOwner(owner)).isEmpty();
        assertThat(notifications.findByOwner(owner)).isEmpty();
        assertThat(notifications.settings(owner).emailEnabled()).as("저장된 설정은 지워져 기본값으로 돌아간다").isTrue();

        assertThat(analyses.findOwned(theirs.analysis(), otherOwner)).isPresent();
        assertThat(comparisons.findOwned(theirs.comparison(), otherOwner)).isPresent();
        assertThat(explanations.findOwned(theirs.explanation(), otherOwner)).isPresent();
        assertThat(milestones.findByOwner(otherOwner)).hasSize(1);
        assertThat(notifications.findByOwner(otherOwner)).hasSize(1);
        assertThat(notifications.settings(otherOwner).emailEnabled()).isFalse();
    }

    @Test
    @DisplayName("마지막 ADMIN 탈퇴를 거절하면 종속 데이터도 지우지 않는다")
    void lastAdminRejectionKeepsDependentData() {
        UserId owner = new UserId(createAdmin());
        Seeded mine = seed(owner);

        assertThatThrownBy(() -> members.withdraw(owner)).isInstanceOf(ConflictException.class);

        assertThat(analyses.findOwned(mine.analysis(), owner)).isPresent();
        assertThat(milestones.findByOwner(owner)).hasSize(1);
    }

    private record Seeded(long analysis, long comparison, long explanation) {}

    /** 한 회원 몫의 인메모리 종속 데이터를 한 세트 만든다. 설정은 기본값(emailEnabled=true)과 다르게 저장한다. */
    private Seeded seed(UserId owner) {
        long contractId = contracts.save(new Contract(0, owner, "계약", "MONTHLY", "contracts/" + owner + "/seed", "SPLIT", "", Instant.now(), null)).id();
        long analysis = analyses.save(new Analysis(0, contractId, owner, AnalysisStatus.ANALYZED, 50, "요약", "m1", Instant.now())).id();
        long comparison = comparisons.save(new Comparison(0, owner, 1L, 2L)).id();
        long explanation = explanations.save(new Explanation(0, 1L, owner, "기본", "설명", "제안")).id();
        milestones.save(new Milestone(0, owner, 1L, "END", "만기", "2027-01-01", 30, false));
        notifications.save(new Notification(System.nanoTime(), owner, "EMAIL", "알림", "2026-11-23", false));
        notifications.saveSettings(new NotificationSettings(owner, false, true, List.of(7)));
        notifications.savePush(owner, "https://push.example/" + owner);
        return new Seeded(analysis, comparison, explanation);
    }

    @Test
    @DisplayName("계약서가 없는 회원도 저장소를 건드리지 않고 탈퇴한다")
    void withdrawalWithoutContractsDoesNotTouchStorage() {
        Member member = signUp();

        members.withdraw(member.id());

        verify(storage, never()).delete(anyString());
        assertThat(users.findById(member.id().value()).orElseThrow().getStatus()).isEqualTo("WITHDRAWN");
    }

    @Test
    @DisplayName("저장소 삭제가 실패하면 탈퇴하지 않고 계정·계약 행·세션이 그대로 남는다")
    void storageFailureKeepsAccountContractsAndSessions() {
        Member member = signUp();
        UserId username = member.id();
        Contract contract = saveContract(username, "contracts/" + username + "/stuck");
        createSession(username);
        doThrow(new IllegalStateException("저장소 장애")).when(storage).delete(contract.s3Key());

        assertThatThrownBy(() -> members.withdraw(username)).isInstanceOf(IllegalStateException.class);

        assertThat(users.findById(member.id().value()).orElseThrow().getStatus()).isEqualTo("ACTIVE");
        assertThat(contracts.findOwned(contract.id(), username)).isPresent();
        assertThat(sessions.findByPrincipalName(username.principalName())).hasSize(1);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("저장소 원본 삭제는 트랜잭션 밖에서 호출된다")
    void storageDeletionRunsOutsideTransaction() {
        Member member = signUp();
        UserId username = member.id();
        saveContract(username, "contracts/" + username + "/outside");
        AtomicBoolean transactionActive = new AtomicBoolean(true);
        doAnswer(invocation -> {
            transactionActive.set(TransactionSynchronizationManager.isActualTransactionActive());
            return null;
        }).when(storage).delete(anyString());

        members.withdraw(username);

        assertThat(transactionActive).isFalse();
    }

    @Test
    @DisplayName("TC-AUTH-015 마지막 ADMIN 탈퇴를 거절할 때는 저장소 원본도 지우지 않는다")
    void lastAdminRejectionKeepsStoredOriginals() {
        long adminId = createAdmin();
        UserId username = new UserId(adminId);
        Contract contract = saveContract(username, "contracts/" + username + "/admin");

        assertThatThrownBy(() -> members.withdraw(username)).isInstanceOf(ConflictException.class);

        verify(storage, never()).delete(anyString());
        assertThat(contracts.findOwned(contract.id(), username)).isPresent();
    }

    @Test
    @DisplayName("TC-AUTH-013 닉네임만 바뀌고 role은 그대로 유지된다")
    void updateChangesOnlyNickname() {
        Member member = signUp();

        Member updated = members.update(member.id(), "새닉", null);

        assertThat(updated.nickname()).isEqualTo("새닉");
        assertThat(updated.role()).isEqualTo("USER");
        assertThat(members.me(member.id()).nickname()).isEqualTo("새닉");
    }

    private Contract saveContract(UserId owner, String s3Key) {
        return contracts.save(new Contract(0, owner, "계약서", "MONTHLY", s3Key, "UPLOADED", "", Instant.now(), null));
    }

    private long createAdmin() {
        return users.saveAndFlush(new MemberEntity(
                UUID.randomUUID() + "@example.com", "관리자", "ADMIN", "ACTIVE", Instant.now())).getId();
    }

    private void createSession(UserId userId) {
        Session session = sessions.createSession();
        session.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, userId.principalName());
        save(session);
    }

    @SuppressWarnings("unchecked")
    private void save(Session session) {
        ((FindByIndexNameSessionRepository<Session>) sessions).save(session);
    }

    private Member signUp() {
        return auth.signIn(profile());
    }

    private OAuthProfile profile() {
        String providerUserId = UUID.randomUUID().toString();
        return new OAuthProfile("kakao", providerUserId, providerUserId + "@example.com", "닉네임");
    }
}
