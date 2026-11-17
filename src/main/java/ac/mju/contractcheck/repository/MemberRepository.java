package ac.mju.contractcheck.repository;

import ac.mju.contractcheck.domain.Member;
import ac.mju.contractcheck.domain.OAuthProfile;
import java.time.Instant;
import java.util.Optional;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * V1__init.sql의 {@code users}, {@code oauth_accounts}에 저장한다. 가입은 {@link #register}에서만 한다.
 * 인증 주체 이름({@code username})은 users.id 문자열이다(spring_session.principal_name과 같다).
 */
@Repository
@RequiredArgsConstructor
public class MemberRepository {

    private static final String ROLE_USER = "USER";
    private static final String ROLE_ADMIN = "ADMIN";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_WITHDRAWN = "WITHDRAWN";

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
    public @NonNull Optional<Member> findByUsername(String username) {
        return OwnerId.parse(username)
                .flatMap(users::findById)
                .filter(user -> !STATUS_WITHDRAWN.equals(user.getStatus()))
                .map(user -> toMember(user, providerOf(user.getId())));
    }

    /** 프로필 중 users에 있는 값(닉네임)만 저장한다. */
    public Member save(Member member) {
        MemberEntity user = users.findById(member.id()).orElseThrow();
        user.changeNickname(member.nickname(), Instant.now());
        return toMember(users.save(user), providerOf(user.getId()));
    }

    public long countActiveAdmins() {
        return users.countByRoleAndStatus(ROLE_ADMIN, STATUS_ACTIVE);
    }

    /** 탈퇴 처리. 상태를 WITHDRAWN으로 바꾸고 개인정보를 비우며 소셜 계정 연결을 끊는다. */
    public void markWithdrawn(String username) {
        OwnerId.parse(username).flatMap(users::findById).ifPresent(user -> {
            user.withdraw(Instant.now());
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
                user.getId(),
                String.valueOf(user.getId()),
                user.getEmail(),
                user.getNickname(),
                provider,
                user.getRole(),
                true,
                user.getCreatedAt());
    }
}
