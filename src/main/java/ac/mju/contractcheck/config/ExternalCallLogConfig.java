package ac.mju.contractcheck.config;

import ac.mju.contractcheck.global.interceptor.ExternalBodyDeidentifier;
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
 * local·dev 프로필은 내용을 볼 수 있는 가린 본문을 남긴다. 그 밖의 프로필, prod가 함께 켜진 경우,
 * 마스킹 서비스가 없는 경우에는 비식별 본문을 남긴다.
 */
@Configuration
public class ExternalCallLogConfig {

    static final Profiles READABLE_BODY_PROFILES = Profiles.of("(local | dev) & !prod");

    @Bean
    public ExternalCallLog externalCallLog(Environment environment, ObjectProvider<PiiMaskingService> masking) {
        PiiMaskingService service = environment.acceptsProfiles(READABLE_BODY_PROFILES) ? masking.getIfAvailable() : null;
        return new ExternalCallLog(service == null ? new ExternalBodyDeidentifier() : new ExternalBodySanitizer(service));
    }
}
