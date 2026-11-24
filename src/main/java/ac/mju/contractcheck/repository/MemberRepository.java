package ac.mju.contractcheck.repository;

import ac.mju.contractcheck.domain.Member;
import ac.mju.contractcheck.domain.enums.MemberRole;
import ac.mju.contractcheck.domain.enums.MemberStatus;
import ac.mju.contractcheck.domain.OAuthProfile;
import ac.mju.contractcheck.domain.UserId;
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

    @NonNull
    private final MemberJpaRepository users;
    @NonNull
    private final OAuthAccountJpaRepository accounts;

    public @NonNull Optional<Member> findByProviderAccount(String provider, String providerUserId) {
        return accounts.findByProviderAndProviderUserId(provider, providerUserId)
                .flatMap(account -> users.findById(account.getUserId())
                        .map(user -> toMember(user, account.getProvider())));
    }

    /**
     * users 행을 한 줄 만든다. 기본 권한·상태는 호출하는 service가 정해 넘긴다. 같은 이메일이 이미 있으면 무결성 예외가 난다(바로 flush).
     * 소셜 계정 연결은 {@link #linkSocialAccount}로 service가 이어서 한다.
     */
    public @NonNull Member insert(@NonNull OAuthProfile profile, @NonNull MemberRole role, @NonNull MemberStatus status) {
        MemberEntity user = users.saveAndFlush(
                new MemberEntity(profile.email(), profile.nickname(), role.name(), status.name(), Instant.now()));
        return toMember(user, profile.provider());
    }

    /** 회원과 소셜 계정(제공자·제공자 회원 ID)의 연결 행을 한 줄 만든다. */
    public void linkSocialAccount(@NonNull UserId id, @NonNull OAuthProfile profile) {
        accounts.save(new OAuthAccountEntity(id.value(), profile.provider(), profile.providerUserId(), Instant.now()));
    }

    /** 상태와 상관없이 id로 읽는다. 탈퇴 회원을 없는 회원으로 볼지는 호출하는 service가 {@code Member.active()}로 정한다. */
    public @NonNull Optional<Member> findById(UserId id) {
        return users.findById(id.value()).map(user -> toMember(user, providerOf(user.getId())));
    }

    /** 도메인 회원의 값(이메일·닉네임·권한·상태)을 users 행에 반영한다. 이메일·닉네임이 null이면 NULL로 저장된다. */
    public @NonNull Member save(@NonNull Member member) {
        MemberEntity saved = users.save(new MemberEntity(
                member.id().value(), member.email(), member.nickname(), member.role().name(), member.status().name(),
                member.createdAt(), Instant.now()));
        return toMember(saved, providerOf(saved.getId()));
    }

    public long countByRoleAndStatus(@NonNull MemberRole role, @NonNull MemberStatus status) {
        return users.countByRoleAndStatus(role.name(), status.name());
    }

    /** 회원의 소셜 계정 연결(oauth_accounts)을 모두 지운다. 탈퇴 때 연결을 끊을지는 호출하는 service가 정한다. 호출하는 쪽 트랜잭션 안에서 실행한다. */
    public void deleteSocialAccounts(@NonNull UserId id) {
        accounts.deleteByUserId(id.value());
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
                MemberRole.valueOf(user.getRole()),
                MemberStatus.valueOf(user.getStatus()),
                true,
                user.getCreatedAt());
    }
}
