package ac.mju.contractcheck.repository;

import ac.mju.contractcheck.domain.Member;
import ac.mju.contractcheck.domain.OAuthProfile;
import ac.mju.contractcheck.domain.UserId;
import ac.mju.contractcheck.domain.WithdrawnMember;
import java.time.Instant;
import java.util.Optional;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * V1__init.sql의 {@code users}, {@code oauth_accounts}에 저장한다. 가입은 {@link #register}에서만 한다.
 * 회원은 {@code UserId}(users.id)로 찾는다. 인증 주체 이름(users.id 문자열)을 UserId로 바꾸는 곳은 CurrentUser 한 군데다.
 */
@Repository
@RequiredArgsConstructor
public class MemberRepository {

    private static final String ROLE_USER = "USER";
    private static final String ROLE_ADMIN = "ADMIN";
    private static final String STATUS_ACTIVE = "ACTIVE";

    @NonNull
    private final MemberJpaRepository users;
    @NonNull
    private final OAuthAccountJpaRepository accounts;

    public @NonNull Optional<Member> findByProviderAccount(String provider, String providerUserId) {
        return accounts.findByProviderAndProviderUserId(provider, providerUserId)
                .flatMap(account -> users.findById(account.getUserId())
                        .map(user -> toMember(user, account.getProvider())));
    }

    /** 신규 회원과 소셜 계정 연결을 한 번에 만든다. 같은 이메일이 이미 있으면 무결성 예외가 난다. */
    public @NonNull Member register(@NonNull OAuthProfile profile) {
        Instant now = Instant.now();
        MemberEntity user = users.saveAndFlush(
                new MemberEntity(profile.email(), profile.nickname(), ROLE_USER, STATUS_ACTIVE, now));
        accounts.save(new OAuthAccountEntity(user.getId(), profile.provider(), profile.providerUserId(), now));
        return toMember(user, profile.provider());
    }

    /** 탈퇴한 회원은 없는 회원으로 본다. */
    public @NonNull Optional<Member> findActive(UserId id) {
        return users.findById(id.value())
                .filter(user -> !WithdrawnMember.STATUS.equals(user.getStatus()))
                .map(user -> toMember(user, providerOf(user.getId())));
    }

    /** 프로필 중 users에 있는 값(닉네임)만 저장한다. */
    public Member save(Member member) {
        MemberEntity user = users.findById(member.id().value()).orElseThrow();
        user.changeNickname(member.nickname(), Instant.now());
        return toMember(users.save(user), providerOf(user.getId()));
    }

    public long countActiveAdmins() {
        return users.countByRoleAndStatus(ROLE_ADMIN, STATUS_ACTIVE);
    }

    /** 탈퇴 처리. 도메인이 정한 탈퇴 상태를 반영해 개인정보 컬럼을 비우고 소셜 계정 연결을 끊는다. */
    public void markWithdrawn(@NonNull WithdrawnMember withdrawn) {
        users.findById(withdrawn.id().value()).ifPresent(user -> {
            user.applyWithdrawn(WithdrawnMember.STATUS, Instant.now());
            users.save(user);
            accounts.deleteByUserId(user.getId());
        });
    }

    private String providerOf(Long userId) {
        return accounts.findFirstByUserIdOrderByIdAsc(userId)
                .map(OAuthAccountEntity::getProvider)
                .orElse("");
    }

    private Member toMember(MemberEntity user, String provider) {
        return new Member(
                new UserId(user.getId()),
                user.getEmail(),
                user.getNickname(),
                provider,
                user.getRole(),
                true,
                user.getCreatedAt());
    }
}
