package ac.mju.contractcheck.service;

import ac.mju.contractcheck.domain.Member;
import ac.mju.contractcheck.domain.OAuthProfile;
import ac.mju.contractcheck.repository.CreditRepository;
import ac.mju.contractcheck.repository.MemberRepository;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FR-AUTH-01 소셜 로그인, FR-AUTH-02 세션 관리·로그아웃
 *
 * 소셜 로그인의 외부 호출(제공자 토큰·사용자 정보)은 CustomOAuth2UserService가 이 서비스 밖에서 끝낸다.
 * 여기서는 DB 작업만 한 트랜잭션으로 묶는다.
 * 스펙 미정: 같은 email·다른 provider 계정을 한 회원으로 연결할지 여부. 지금은 연결하지 않고,
 * users.email이 UNIQUE라 두 번째 가입은 무결성 예외로 거절한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    @NonNull
    private final MemberService members;
    @NonNull
    private final MemberRepository memberRepository;
    @NonNull
    private final CreditRepository credits;

    /**
     * FR-AUTH-01 소셜 로그인
     * 기존 (provider, provider_user_id)면 그 회원을 돌려주고, 처음이면 users·oauth_accounts와
     * 무료 2회 지갑·GRANT 원장을 같은 트랜잭션으로 만든다(TC-AUTH-001, TC-AUTH-002, TC-CRD-001).
     */
    @Transactional
    public Member signIn(OAuthProfile profile) {
        return memberRepository.findByProviderAccount(profile.provider(), profile.providerUserId())
                .orElseGet(() -> register(profile));
    }

    private Member register(OAuthProfile profile) {
        Member member = memberRepository.register(profile);
        credits.grantSignup(member.id());
        log.info("신규 가입 userId={} provider={}", member.id(), profile.provider());
        return member;
    }

    /**
     * FR-AUTH-02 세션 관리·로그아웃
     * 세션 주체(users.id)로 현재 회원을 조회한다. 세션은 있으나 users 행이 없으면 회원을 만들지 않고 401로 이어진다.
     */
    public Member me(String username) {
        return members.me(username);
    }
}
