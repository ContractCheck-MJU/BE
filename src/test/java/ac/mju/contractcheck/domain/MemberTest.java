package ac.mju.contractcheck.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 탈퇴 규칙. Spring·DB 없이 순수 객체로 검증한다. */
class MemberTest {

    private static final Instant JOINED = Instant.parse("2026-11-01T00:00:00Z");

    private final Member member = new Member(7L, "7", "a@test.com", "닉네임", "kakao", "USER", true, JOINED);

    @Test
    @DisplayName("TC-AUTH-014 탈퇴하면 같은 회원의 WITHDRAWN 상태가 되고 가입 시각은 유지된다")
    void withdrawKeepsIdentityOnly() {
        WithdrawnMember withdrawn = member.withdraw();

        assertThat(withdrawn.id()).isEqualTo(7L);
        assertThat(withdrawn.createdAt()).isEqualTo(JOINED);
        assertThat(WithdrawnMember.STATUS).isEqualTo("WITHDRAWN");
    }

    @Test
    @DisplayName("정합성: 탈퇴 회원 타입에는 개인정보(이메일·닉네임·제공자) 필드가 없어 개인정보가 남을 수 없다")
    void withdrawnMemberCannotHoldPersonalData() {
        assertThat(Arrays.stream(WithdrawnMember.class.getRecordComponents()).map(RecordComponent::getName))
                .containsExactlyInAnyOrder("id", "createdAt");
    }

    @Test
    @DisplayName("탈퇴해도 원래 회원 객체는 바뀌지 않는다")
    void withdrawDoesNotMutateMember() {
        member.withdraw();

        assertThat(member.email()).isEqualTo("a@test.com");
        assertThat(member.nickname()).isEqualTo("닉네임");
    }
}
