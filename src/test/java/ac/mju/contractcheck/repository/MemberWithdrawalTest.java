package ac.mju.contractcheck.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ac.mju.contractcheck.domain.Member;
import ac.mju.contractcheck.domain.OAuthProfile;
import ac.mju.contractcheck.global.error.ConflictException;
import ac.mju.contractcheck.service.AuthService;
import ac.mju.contractcheck.service.MemberService;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * FR-AUTH-03 회원 탈퇴. 관리자 행은 ADMIN 지정 경로가 스펙에 없어 패키지 비공개 JPA 저장소로 직접 만든다.
 * 클래스 단위 @Transactional이라 테스트마다 롤백되어 서로 데이터를 공유하지 않는다.
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
    FindByIndexNameSessionRepository<? extends Session> sessions;

    @Test
    @DisplayName("TC-AUTH-014 탈퇴하면 개인정보를 비우고 모든 기기의 세션을 지우며 이후 조회는 401")
    void withdrawalRemovesSessionsAndPersonalData() {
        Member member = signUp();
        String username = String.valueOf(member.id());
        createSession(username);
        createSession(username);
        assertThat(sessions.findByPrincipalName(username)).hasSize(2);

        members.withdraw(username);

        assertThat(sessions.findByPrincipalName(username)).isEmpty();
        MemberEntity row = users.findById(member.id()).orElseThrow();
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
        members.withdraw(String.valueOf(first.id()));

        Member second = auth.signIn(profile);

        assertThat(second.id()).isNotEqualTo(first.id());
        assertThat(second.email()).isEqualTo(profile.email());
    }

    @Test
    @DisplayName("TC-AUTH-015 마지막 ADMIN의 탈퇴는 409 LAST_ADMIN이고 계정이 유지된다")
    void lastAdminCannotWithdraw() {
        long adminId = createAdmin();
        String username = String.valueOf(adminId);

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

        members.withdraw(String.valueOf(first));

        assertThat(users.findById(first).orElseThrow().getStatus()).isEqualTo("WITHDRAWN");
    }

    @Test
    @DisplayName("TC-AUTH-013 닉네임만 바뀌고 role은 그대로")
    void updateChangesOnlyNickname() {
        Member member = signUp();

        Member updated = members.update(String.valueOf(member.id()), "새닉", null);

        assertThat(updated.nickname()).isEqualTo("새닉");
        assertThat(updated.role()).isEqualTo("USER");
        assertThat(members.me(String.valueOf(member.id())).nickname()).isEqualTo("새닉");
    }

    private long createAdmin() {
        return users.saveAndFlush(new MemberEntity(
                UUID.randomUUID() + "@example.com", "관리자", "ADMIN", "ACTIVE", Instant.now())).getId();
    }

    private void createSession(String principalName) {
        Session session = sessions.createSession();
        session.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, principalName);
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
