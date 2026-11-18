package ac.mju.contractcheck.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.web.http.DefaultCookieSerializer;

/**
 * FR-AUTH-02 세션 쿠키(SESSION) 속성: HttpOnly, SameSite=Lax, Path=/, Secure.
 * Secure는 http로 개발하는 local 프로필에서만 끈다({@code contractcheck.session.cookie-secure}).
 * 세션 저장소는 Spring Session JDBC(PostgreSQL), 유휴 만료는 {@code server.servlet.session.timeout}이다.
 */
@Configuration
public class SessionConfig {

    @Bean
    DefaultCookieSerializer cookieSerializer(
            @Value("${contractcheck.session.cookie-secure:true}") boolean cookieSecure) {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName("SESSION");
        serializer.setCookiePath("/");
        serializer.setUseHttpOnlyCookie(true);
        serializer.setUseSecureCookie(cookieSecure);
        serializer.setSameSite("Lax");
        return serializer;
    }
}
