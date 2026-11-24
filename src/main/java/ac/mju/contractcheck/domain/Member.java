package ac.mju.contractcheck.domain;

import java.time.Instant;

/**
 * 회원. 저장되는 값(권한·상태 포함)을 모두 가진다. 탈퇴 상태이면 이메일·닉네임을 가질 수 없다(정합성).
 * 어떤 상태의 회원을 "없는 회원"으로 볼지는 service가 {@link #active()}로 정한다.
 */
public record Member(
        UserId id,
        String email,
        String nickname,
        String provider,
        MemberRole role,
        MemberStatus status,
        boolean notifyEmail,
        Instant createdAt) {

    public Member {
        if (status == MemberStatus.WITHDRAWN && (email != null || nickname != null)) {
            throw new IllegalArgumentException("탈퇴한 회원은 이메일·닉네임을 가질 수 없습니다.");
        }
    }

    public boolean active() {
        return status == MemberStatus.ACTIVE;
    }

    public Member withProfile(String nickname, boolean notifyEmail) {
        return new Member(id, email, nickname, provider, role, status, notifyEmail, createdAt);
    }

    /** 탈퇴 규칙: WITHDRAWN 상태가 되고 이메일·닉네임이 빈다. 식별자·권한·가입 시각은 유지하고 원래 객체는 바뀌지 않는다. */
    public Member withdraw() {
        return new Member(id, null, null, provider, role, MemberStatus.WITHDRAWN, notifyEmail, createdAt);
    }
}
