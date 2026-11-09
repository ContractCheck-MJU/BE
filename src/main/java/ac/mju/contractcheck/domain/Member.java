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
}
