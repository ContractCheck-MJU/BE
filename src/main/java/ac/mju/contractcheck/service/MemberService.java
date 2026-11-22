package ac.mju.contractcheck.service;

import ac.mju.contractcheck.client.StorageClient;
import ac.mju.contractcheck.domain.Member;
import ac.mju.contractcheck.global.error.ConflictException;
import ac.mju.contractcheck.repository.ContractRepository;
import ac.mju.contractcheck.repository.MemberRepository;
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

    private static final String ROLE_ADMIN = "ADMIN";

    @NonNull
    private final MemberRepository members;
    @NonNull
    private final ContractRepository contracts;
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
    public Member me(String username) {
        return members.findByUsername(username)
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
    public Member update(String username, String nickname, Boolean notifyEmail) {
        Member current = me(username);
        Member updated = current.withProfile(
                nickname == null || nickname.isBlank() ? current.nickname() : nickname,
                notifyEmail == null ? current.notifyEmail() : notifyEmail);
        Member saved = members.save(updated);
        log.info("회원 정보 수정 username={}", username);
        return saved;
    }

    /**
     * FR-AUTH-03 내 정보·탈퇴
     * 본인만 탈퇴한다. 마지막 ADMIN이면 409 LAST_ADMIN으로 거부하며, 이때는 아무것도 지우지 않는다.
     * 저장소의 계약서 원본을 먼저 지운 뒤(트랜잭션 밖), 한 트랜잭션에서 계약 행 삭제, users.status를 WITHDRAWN으로 변경,
     * 이메일·닉네임 비우기, oauth_accounts 삭제, 모든 로그인 세션(다른 기기 포함) 삭제를 한다. 이용권 잔여는 소멸한다.
     * 객체 삭제가 실패하면 예외로 끝나고 계정과 계약 행은 그대로라 다시 탈퇴를 시도할 수 있다(객체 삭제는 이미 없어도 성공).
     *
     * TODO(FR-AUTH-03): 조항·분석·비교·설명·일정 같은 종속 데이터 삭제는 각 저장소가 영속화되는 FR에서 이어 붙인다.
     * 이 데이터가 계약을 참조하게 되면 계약 행을 지우기 전에 먼저 지워야 한다.
     * 스펙 미정: 탈퇴 후 같은 소셜 계정 재가입 때 무료 2회를 다시 지급할지.
     */
    public void withdraw(String username) {
        Member member = me(username);
        rejectLastAdmin(member);
        contracts.findS3KeysByOwner(username).forEach(storage::delete);
        transaction.executeWithoutResult(status -> {
            rejectLastAdmin(member);
            contracts.deleteAllByOwner(username);
            members.markWithdrawn(username);
            sessions.findByPrincipalName(username).keySet().forEach(sessions::deleteById);
        });
        log.info("회원 탈퇴 username={}", username);
    }

    private void rejectLastAdmin(Member member) {
        if (ROLE_ADMIN.equals(member.role()) && members.countActiveAdmins() <= 1) {
            throw new ConflictException("LAST_ADMIN", "마지막 관리자는 탈퇴할 수 없습니다.");
        }
    }
}
