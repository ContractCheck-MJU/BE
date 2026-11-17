package ac.mju.contractcheck.config;

import ac.mju.contractcheck.global.security.ApiAccessDeniedHandler;
import ac.mju.contractcheck.global.security.ApiAuthenticationEntryPoint;
import ac.mju.contractcheck.global.security.OAuth2LoginFailureHandler;
import ac.mju.contractcheck.service.CustomOAuth2UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import tools.jackson.databind.ObjectMapper;

/**
 * 보안 기본값 (FR-AUTH-02, FR-AUTH-04).
 *
 * <ul>
 *   <li>CSRF: 켜 둔다. SPA 방식(XSRF-TOKEN 쿠키 + X-XSRF-TOKEN 헤더). 토스 웹훅 예외는 결제 구현 시 해당 경로만 추가.</li>
 *   <li>인가: CSRF 토큰 발급만 공개, 관리자 API는 ADMIN, 나머지는 로그인 필요.</li>
 *   <li>API 미인증 요청은 로그인 페이지 리다이렉트 대신 401 JSON(UNAUTHORIZED), CSRF 실패는 403 CSRF_INVALID,
 *       권한 부족은 403 FORBIDDEN. 모두 {code, message, traceId} 포맷이다.</li>
 * </ul>
 * FR-AUTH-01 소셜 로그인: 카카오·네이버·구글 Authorization Code를 {@code oauth2Login()}으로 처리한다.
 * 시작·콜백({@code /oauth2/authorization/*}, {@code /login/oauth2/code/*})은 스프링 시큐리티 필터가 맡으므로
 * 컨트롤러도 {@code permitAll}도 추가하지 않았다. 성공은 {frontend}/ 로, 실패는 {frontend}/login?error=... 로 302한다.
 * 가짜 로그인은 두지 않는다.
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            CustomOAuth2UserService oauth2UserService,
            ObjectMapper objectMapper,
            @Value("${contractcheck.frontend.base-url}") String frontendBaseUrl) throws Exception {
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
                .oauth2Login(oauth2 -> oauth2
                        .userInfoEndpoint(userInfo -> userInfo.userService(oauth2UserService))
                        .successHandler(successHandler(frontendBaseUrl))
                        .failureHandler(new OAuth2LoginFailureHandler(frontendBaseUrl)))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new ApiAuthenticationEntryPoint(objectMapper))
                        .accessDeniedHandler(new ApiAccessDeniedHandler(objectMapper)));
        return http.build();
    }

    private AuthenticationSuccessHandler successHandler(String frontendBaseUrl) {
        String base = frontendBaseUrl.endsWith("/")
                ? frontendBaseUrl.substring(0, frontendBaseUrl.length() - 1)
                : frontendBaseUrl;
        SimpleUrlAuthenticationSuccessHandler handler = new SimpleUrlAuthenticationSuccessHandler(base + "/");
        handler.setAlwaysUseDefaultTargetUrl(true);
        return handler;
    }
}
