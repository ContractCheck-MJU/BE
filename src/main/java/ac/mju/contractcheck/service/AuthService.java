package ac.mju.contractcheck.service;

import ac.mju.contractcheck.domain.Member;
import ac.mju.contractcheck.service.MemberService;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * TODO(FR-AUTH-01): 카카오·네이버·구글 소셜 로그인(Authorization Code)을 이 서비스와 SecurityConfig에 구현한다.
 * 1. 입력 검증: OAuth2 콜백의 registrationId(kakao/naver/google)와 제공자 프로필(sub/id, email, nickname)을 확인한다.
 * 2. 권한/소유자 확인: 로그인 전이므로 세션은 아직 없다. 클라이언트 ID/Secret은 설정값이며 코드에 넣지 않는다.
 * 3. DB/외부 호출: CustomOAuth2UserService에서 oauth_accounts(provider, provider_user_id)를 찾는다.
 *    없으면 users(role=USER, nickname, email, status, created_at)와 oauth_accounts를 넣고,
 *    credit_wallets(free_remaining=2, paid_remaining=0, version=0)와 credit_ledger type=GRANT(TC-CRD-001, TC-AUTH)를 같은 트랜잭션으로 기록한다.
 *    있으면 기존 user_id로 세션을 만든다. 액세스 토큰은 로그인 처리 후 저장하지 않는다.
 * 4. 상태 전이: 신규 가입 시 users.status를 활성 값으로 둔다. 스펙 미정: users.status 허용 값, 같은 email·다른 provider 계정 연결 여부.
 * 5. 예외/에러코드: 제공자/사용자 거부·연동 실패 시 프론트 로그인 페이지로 리다이렉트. 스펙 미정: 실패 리다이렉트 베이스 URL과 오류 쿼리 형식.
 * 6. 반환: Spring Session JDBC 세션 쿠키(SESSION)와 CSRF 쿠키. 로그아웃(FR-AUTH-02)은 앱 세션만 무효화한다.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    @NonNull
    private final MemberService members;


    /**
     * FR-AUTH-02 세션 관리·로그아웃
     *
     * TODO(FR-AUTH-02): 세션의 users.id로 현재 사용자를 조회한다.
     * 1. 입력 검증: 인증된 세션이 있어야 한다. 없으면 401.
     * 2. 권한/소유자 확인: 세션 주체 본인만 조회한다.
     * 3. DB/외부 호출: users를 id로 읽고, 필요 시 oauth_accounts.provider를 조인한다. MemberRepository.ensure()로 자동 생성하지 않는다.
     * 4. 상태 전이: 없음.
     * 5. 예외/에러코드: 세션은 있으나 users 행이 없으면 401 또는 시트 회원 오류 코드.
     * 6. 반환: id, nickname, role, provider.
     */
    public Member me(String username) {
        return members.me(username);
    }
}
