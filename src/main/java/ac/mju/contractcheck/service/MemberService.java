package ac.mju.contractcheck.service;

import ac.mju.contractcheck.client.StorageClient;
import ac.mju.contractcheck.domain.Member;
import ac.mju.contractcheck.domain.MemberRole;
import ac.mju.contractcheck.domain.MemberStatus;
import ac.mju.contractcheck.domain.UserId;
import ac.mju.contractcheck.global.error.ConflictException;
import ac.mju.contractcheck.repository.ClauseRepository;
import ac.mju.contractcheck.repository.ContractRepository;
import ac.mju.contractcheck.repository.MemberRepository;
import ac.mju.contractcheck.repository.OwnedDataEraser;
import java.util.List;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemberService {

    @NonNull
    private final MemberRepository members;
    @NonNull
    private final ContractRepository contracts;
    @NonNull
    private final ClauseRepository clauses;
    @NonNull
    private final List<OwnedDataEraser> erasers;
    @NonNull
    private final StorageClient storage;
    @NonNull
    private final FindByIndexNameSessionRepository<? extends Session> sessions;
    @NonNull
    private final TransactionTemplate transaction;


    /**
     * FR-AUTH-02 세션 관리·로그아웃, FR-AUTH-03 내 정보·탈퇴
     * 세션 주체(users.id) 본인의 회원 정보를 읽는다. 행이 없거나 탈퇴한 회원이면 만들어 주지 않고 401로 이어진다.
     */
    public Member me(UserId userId) {
        return members.findById(userId).filter(Member::active)
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("회원 행이 없는 세션"));
    }

    /**
     * FR-AUTH-03 내 정보·탈퇴
     * 닉네임(users.nickname, 50자 이내 — 요청 DTO에서 검증)만 바꾼다. 닉네임이 없거나 공백이면 기존 값을 유지한다.
     * role·email·provider 식별자는 바꾸지 않는다.
     * 스펙 미정: notifyEmail을 notification_settings.email_enabled로 매핑할지, 요청 필드 자체를 둘지.
     * 매핑이 정해질 때까지 notifyEmail은 저장하지 않고 응답에도 싣지 않는다.
     */
    @Transactional
    public Member update(UserId userId, String nickname, Boolean notifyEmail) {
        Member current = me(userId);
        Member updated = current.withProfile(
                nickname == null || nickname.isBlank() ? current.nickname() : nickname,
                notifyEmail == null ? current.notifyEmail() : notifyEmail);
        Member saved = members.save(updated);
        log.info("회원 정보 수정 userId={}", userId);
        return saved;
    }

    /**
     * FR-AUTH-03 내 정보·탈퇴
     * 본인만 탈퇴한다. 마지막 ADMIN이면 409 LAST_ADMIN으로 거부하며, 이때는 아무것도 지우지 않는다.
     * 저장소의 계약서 원본을 먼저 지운 뒤(트랜잭션 밖), 한 트랜잭션에서 다음을 한다.
     * 분석·비교·설명·일정·알림 같은 종속 데이터 삭제({@link OwnedDataEraser} 구현체 전부, 계약을 참조하므로 계약 행보다 먼저),
     * 계약 행과 조항 삭제, users.status를 WITHDRAWN으로 변경하고 이메일·닉네임 비우기, oauth_accounts 삭제,
     * 모든 로그인 세션(다른 기기 포함) 삭제.
     * 객체 삭제가 실패하면 예외로 끝나고 계정과 계약 행은 그대로라 다시 탈퇴를 시도할 수 있다(객체 삭제는 이미 없어도 성공).
     * 결제 내역과 이용권 원장은 보존 기간 동안 남긴다(테이블 정의서).
     *
     * 스펙 미정: credit_wallets 처리(테이블 정의서는 "탈퇴 시 삭제, 유료 잔여분 환불 정책 확인 필요"라 유료 잔여분이 있는 지갑을
     * 지울지·환불할지 정해지지 않아 지금은 지갑을 그대로 둔다. 탈퇴한 회원은 로그인할 수 없어 사용되지 않는다),
     * 탈퇴 후 같은 소셜 계정 재가입 때 무료 2회를 다시 지급할지(지금은 새 회원으로 가입해 다시 지급된다),
     * 탈퇴 API의 429(3회/일/사용자) 한도 구현 방식(코드베이스에 한도 장치가 아직 없다).
     */
    public void withdraw(UserId userId) {
        Member member = me(userId);
        rejectLastAdmin(member);
        contracts.findS3KeysByOwner(userId).forEach(storage::delete);
        transaction.executeWithoutResult(status -> {
            rejectLastAdmin(member);
            erasers.forEach(eraser -> eraser.deleteAllByOwner(userId));
            clauses.deleteAllByOwner(userId);
            contracts.deleteAllByOwner(userId);
            members.save(member.withdraw());
            members.deleteSocialAccounts(userId);
            sessions.findByPrincipalName(userId.principalName()).keySet().forEach(sessions::deleteById);
        });
        log.info("회원 탈퇴 userId={}", userId);
    }

    private void rejectLastAdmin(Member member) {
        if (member.role() == MemberRole.ADMIN && members.countByRoleAndStatus(MemberRole.ADMIN, MemberStatus.ACTIVE) <= 1) {
            throw new ConflictException("LAST_ADMIN", "마지막 관리자는 탈퇴할 수 없습니다.");
        }
    }
}
