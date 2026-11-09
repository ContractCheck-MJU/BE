package ac.mju.contractcheck.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

/**
 * 보안 기본값 (FR-AUTH-02, FR-AUTH-04).
 *
 * <ul>
 *   <li>CSRF: 켜 둔다. SPA 방식(XSRF-TOKEN 쿠키 + X-XSRF-TOKEN 헤더). 토스 웹훅 예외는 결제 구현 시 해당 경로만 추가.</li>
 *   <li>인가: CSRF 토큰 발급만 공개, 관리자 API는 ADMIN, 나머지는 로그인 필요.</li>
 *   <li>API 미인증 요청은 로그인 페이지 리다이렉트 대신 401.</li>
 * </ul>
 * OAuth2 소셜 로그인(oauth2Login)은 클라이언트 등록 정보가 준비되면 추가한다. 가짜 로그인은 두지 않는다.
 *
 * TODO(FR-AUTH-01): 카카오·네이버·구글 Authorization Code 로그인을 {@code oauth2Login()}으로 붙인다.
 * 1. 입력 검증: application 설정의 registration(kakao/naver/google)과 redirect URI를 사용한다. 시크릿은 코드에 넣지 않는다.
 * 2. 권한/소유자 확인: {@code /oauth2/authorization/*}, {@code /login/oauth2/code/*}는 인증 전 접근을 허용한다.
 *    CSRF·세션 정책은 기존 SPA 설정을 유지한다.
 * 3. DB/외부 호출: AuthService/CustomOAuth2UserService가 oauth_accounts·users를 upsert하고 무료 2회를 지급한다(TC-CRD-001).
 *    제공자 토큰은 세션 생성 후 저장하지 않는다.
 * 4. 상태 전이: 성공 시 Spring Session JDBC 쿠키(SESSION) 발급, users는 활성 상태로 둔다.
 * 5. 예외/에러코드: 실패 시 프론트 로그인 페이지로 리다이렉트. API 그 외 미인증은 기존처럼 401.
 *    스펙 미정: 실패 리다이렉트 베이스 URL과 오류 쿼리 형식, 같은 email·다른 provider 연결, users.status 허용 값.
 * 6. 반환: 인증된 세션. 로그아웃은 /api/v1/auth/logout에서 앱 세션만 무효화한다(FR-AUTH-02).
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers("/api/v1/payments/webhooks/toss")
                        .spa()
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/csrf", "/api/v1/products", "/api/v1/payments/webhooks/toss").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));
        return http.build();
    }
}
