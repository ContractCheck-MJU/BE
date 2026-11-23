package ac.mju.contractcheck.repository;

import ac.mju.contractcheck.domain.Member;
import ac.mju.contractcheck.domain.MemberRole;
import ac.mju.contractcheck.domain.MemberStatus;
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

    public long countByRoleAndStatus(@NonNull MemberRole role, @NonNull MemberStatus status) {
        return users.countByRoleAndStatus(role.name(), status.name());
    }

    /** 탈퇴 처리. 도메인이 정한 탈퇴 상태를 반영해 개인정보 컬럼을 비운다. 소셜 계정 연결 삭제는 {@link #deleteSocialAccounts}로 service가 한다. */
    public void markWithdrawn(@NonNull WithdrawnMember withdrawn) {
        users.findById(withdrawn.id().value()).ifPresent(user -> {
            user.applyWithdrawn(WithdrawnMember.STATUS, Instant.now());
            users.save(user);
        });
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
                user.getRole(),
                true,
                user.getCreatedAt());
    }
}
