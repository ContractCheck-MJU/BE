package ac.mju.contractcheck.service;

import ac.mju.contractcheck.domain.Member;
import ac.mju.contractcheck.domain.OAuthProfile;
import java.util.List;
import java.util.Map;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * FR-AUTH-01 소셜 로그인
 *
 * 제공자 사용자 정보 조회(외부 호출)를 먼저 끝낸 뒤 AuthService의 가입·조회 트랜잭션을 부른다.
 * 세션에 남는 주체는 users.id(이름)와 권한만 가진다. 제공자 토큰과 프로필 원본은 세션에 두지 않는다.
 * 인증 주체 이름이 users.id 문자열이므로 spring_session.principal_name도 같은 값이 된다.
 */
@Slf4j
@Service
public class CustomOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    static final String USER_ID_ATTRIBUTE = "userId";

    private final AuthService auth;
    private final OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate;

    /** 사용자 정보 조회에는 호출 기록이 붙은 {@code oauthUserInfoRestTemplate}을 쓴다. */
    public CustomOAuth2UserService(@NonNull AuthService auth, @NonNull RestTemplate oauthUserInfoRestTemplate) {
        DefaultOAuth2UserService userInfo = new DefaultOAuth2UserService();
        userInfo.setRestOperations(oauthUserInfoRestTemplate);
        this.auth = auth;
        this.delegate = userInfo;
    }

    /** FR-AUTH-01 소셜 로그인 */
    @Override
    public OAuth2User loadUser(OAuth2UserRequest request) throws OAuth2AuthenticationException {
        OAuth2User loaded = delegate.loadUser(request);
        String registrationId = request.getClientRegistration().getRegistrationId();

        OAuthProfile profile;
        try {
            profile = OAuthProfile.from(registrationId, loaded.getAttributes());
        } catch (IllegalArgumentException exception) {
            log.warn("소셜 사용자 정보를 해석하지 못함 provider={}", registrationId);
            throw new OAuth2AuthenticationException(new OAuth2Error("invalid_user_info"), exception);
        }

        Member member;
        try {
            member = auth.signIn(profile);
        } catch (DataIntegrityViolationException exception) {
            log.warn("소셜 가입 충돌 provider={}", registrationId);
            throw new OAuth2AuthenticationException(new OAuth2Error("account_conflict"), exception);
        }

        return new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_" + member.role().name())),
                Map.of(USER_ID_ATTRIBUTE, member.id().value()),
                USER_ID_ATTRIBUTE);
    }
}
