package ac.mju.contractcheck.config;

import ac.mju.contractcheck.global.filter.MdcRequestIdFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * 로깅 횡단 관심 등록. 필터 구현은 {@link MdcRequestIdFilter}에 둔다.
 */
@Configuration
public class LoggingConfig {

    @Bean
    FilterRegistrationBean<MdcRequestIdFilter> mdcRequestIdFilter() {
        FilterRegistrationBean<MdcRequestIdFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new MdcRequestIdFilter());
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.setName("mdcRequestIdFilter");
        return registration;
    }
}
