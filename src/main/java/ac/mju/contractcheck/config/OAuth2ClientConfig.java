package ac.mju.contractcheck.config;

import ac.mju.contractcheck.global.interceptor.ExternalCallLog;
import ac.mju.contractcheck.global.interceptor.RestCallLoggingInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.security.oauth2.client.endpoint.RestClientAuthorizationCodeTokenResponseClient;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

/**
 * 소셜 로그인의 외부 호출(토큰 교환, 사용자 정보 조회)에 호출 기록을 붙인다. 구성은 Spring Security 기본값과 같고
 * 기록만 더한다. 본문은 토큰과 프로필뿐이라 어느 환경에서도 남기지 않는다.
 * 토큰 클라이언트는 빈으로 등록하면 {@code oauth2Login()}이 가져다 쓴다.
 */
@Configuration
@Import(ExternalCallLogConfig.class)
public class OAuth2ClientConfig {

    @Bean
    public RestClientAuthorizationCodeTokenResponseClient authorizationCodeTokenResponseClient(ExternalCallLog log) {
        RestClientAuthorizationCodeTokenResponseClient client = new RestClientAuthorizationCodeTokenResponseClient();
        client.setRestClient(RestClient.builder()
                .configureMessageConverters(converters -> converters
                        .addCustomConverter(new FormHttpMessageConverter())
                        .addCustomConverter(new OAuth2AccessTokenResponseHttpMessageConverter()))
                .defaultStatusHandler(new OAuth2ErrorResponseErrorHandler())
                .requestInterceptor(new RestCallLoggingInterceptor("oauth-token", log, false))
                .build());
        return client;
    }

    @Bean
    public RestTemplate oauthUserInfoRestTemplate(ExternalCallLog log) {
        RestTemplate template = new RestTemplate();
        template.setErrorHandler(new OAuth2ErrorResponseErrorHandler());
        template.getInterceptors().add(new RestCallLoggingInterceptor("oauth-userinfo", log, false));
        return template;
    }
}
