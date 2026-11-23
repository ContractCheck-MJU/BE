package ac.mju.contractcheck.domain;

import java.time.Instant;

public record Member(
        long id,
        String username,
        String email,
        String nickname,
        String provider,
        String role,
        boolean notifyEmail,
        Instant createdAt) {

    public Member withProfile(String nickname, boolean notifyEmail) {
        return new Member(id, username, email, nickname, provider, role, notifyEmail, createdAt);
    }

    /** 탈퇴 규칙: 탈퇴한 회원은 개인정보 없이 식별자와 가입 시각만 남는다. 원래 객체는 바뀌지 않는다. */
    public WithdrawnMember withdraw() {
        return new WithdrawnMember(id, createdAt);
    }
}
