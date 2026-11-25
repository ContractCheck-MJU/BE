package ac.mju.contractcheck.config;

import ac.mju.contractcheck.global.interceptor.ExternalBodySanitizer;
import ac.mju.contractcheck.global.interceptor.ExternalCallLog;
import ac.mju.contractcheck.service.PiiMaskingService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/**
 * 외부 호출 기록 빈. 외부 클라이언트를 만드는 설정이 가져다 붙인다.
 * 본문은 local·dev 프로필에서만 가려서 남긴다. prod가 함께 켜져 있거나 마스킹 서비스가 없으면 본문을 남기지 않는다.
 */
@Configuration
public class ExternalCallLogConfig {

    static final Profiles BODY_PROFILES = Profiles.of("(local | dev) & !prod");

    @Bean
    public ExternalCallLog externalCallLog(Environment environment, ObjectProvider<PiiMaskingService> masking) {
        PiiMaskingService service = environment.acceptsProfiles(BODY_PROFILES) ? masking.getIfAvailable() : null;
        return new ExternalCallLog(service == null ? null : new ExternalBodySanitizer(service));
    }
}
