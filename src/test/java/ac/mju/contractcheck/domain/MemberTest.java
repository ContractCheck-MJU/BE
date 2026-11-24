package ac.mju.contractcheck.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 회원 규칙과 정합성. Spring·DB 없이 순수 객체로 검증한다. */
class MemberTest {

    private static final Instant JOINED = Instant.parse("2026-11-01T00:00:00Z");

    private final Member member = new Member(
            new UserId(7), "a@test.com", "닉네임", "kakao", MemberRole.USER, MemberStatus.ACTIVE, true, JOINED);

    @Test
    @DisplayName("TC-AUTH-014 탈퇴하면 같은 회원이 WITHDRAWN 상태가 되고 이메일·닉네임이 비며 식별자·권한·가입 시각은 유지된다")
    void withdrawClearsPersonalDataAndKeepsIdentity() {
        Member withdrawn = member.withdraw();

        assertThat(withdrawn.status()).isEqualTo(MemberStatus.WITHDRAWN);
        assertThat(withdrawn.email()).isNull();
        assertThat(withdrawn.nickname()).isNull();
        assertThat(withdrawn.id()).isEqualTo(member.id());
        assertThat(withdrawn.role()).isEqualTo(MemberRole.USER);
        assertThat(withdrawn.createdAt()).isEqualTo(JOINED);
        assertThat(withdrawn.active()).isFalse();
    }

    @Test
    @DisplayName("정합성: 탈퇴 상태인 회원은 이메일·닉네임을 가질 수 없다")
    void withdrawnMemberCannotHoldPersonalData() {
        assertThatThrownBy(() -> new Member(new UserId(7), "a@test.com", null, "", MemberRole.USER, MemberStatus.WITHDRAWN, true, JOINED))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Member(new UserId(7), null, "닉네임", "", MemberRole.USER, MemberStatus.WITHDRAWN, true, JOINED))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new Member(new UserId(7), null, null, "", MemberRole.USER, MemberStatus.WITHDRAWN, true, JOINED).active()).isFalse();
    }

    @Test
    @DisplayName("활성 회원은 이메일이 없어도 된다(소셜 제공자가 이메일을 주지 않을 수 있다)")
    void activeMemberMayHaveNoEmail() {
        Member noEmail = new Member(new UserId(8), null, "닉네임", "kakao", MemberRole.USER, MemberStatus.ACTIVE, true, JOINED);

        assertThat(noEmail.active()).isTrue();
    }

    @Test
    @DisplayName("프로필을 바꿔도 상태·권한은 그대로다")
    void withProfileKeepsStatusAndRole() {
        Member changed = member.withProfile("새닉", false);

        assertThat(changed.nickname()).isEqualTo("새닉");
        assertThat(changed.status()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(changed.role()).isEqualTo(MemberRole.USER);
    }

    @Test
    @DisplayName("탈퇴해도 원래 회원 객체는 바뀌지 않는다")
    void withdrawDoesNotMutateMember() {
        member.withdraw();

        assertThat(member.email()).isEqualTo("a@test.com");
        assertThat(member.status()).isEqualTo(MemberStatus.ACTIVE);
    }
}
