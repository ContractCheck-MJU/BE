package ac.mju.contractcheck.service;

import ac.mju.contractcheck.domain.Member;
import ac.mju.contractcheck.global.error.ConflictException;
import ac.mju.contractcheck.repository.MemberRepository;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemberService {

    private static final String ROLE_ADMIN = "ADMIN";

    @NonNull
    private final MemberRepository members;
    @NonNull
    private final FindByIndexNameSessionRepository<? extends Session> sessions;


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
     * 본인만 탈퇴한다. 마지막 ADMIN이면 409 LAST_ADMIN으로 거부한다.
     * users.status를 WITHDRAWN으로 바꾸고 이메일·닉네임을 비우며 oauth_accounts를 지운다.
     * 해당 회원의 모든 로그인 세션(다른 기기 포함)을 지운다. 이용권 잔여는 소멸한다.
     *
     * TODO(FR-AUTH-03): 계약서·조항·분석 결과 삭제와 S3 원본 삭제는 해당 저장소가 영속화되는 FR-UPL-01에서 이어 붙인다.
     * 스펙 미정: 탈퇴 후 같은 소셜 계정 재가입 때 무료 2회를 다시 지급할지.
     */
    @Transactional
    public void withdraw(String username) {
        Member member = me(username);
        if (ROLE_ADMIN.equals(member.role()) && members.countActiveAdmins() <= 1) {
            throw new ConflictException("LAST_ADMIN", "마지막 관리자는 탈퇴할 수 없습니다.");
        }
        members.markWithdrawn(username);
        sessions.findByPrincipalName(username).keySet().forEach(sessions::deleteById);
        log.info("회원 탈퇴 username={}", username);
    }
}
