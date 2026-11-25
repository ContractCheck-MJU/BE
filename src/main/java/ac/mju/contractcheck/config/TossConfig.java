package ac.mju.contractcheck.config;

import java.net.http.HttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** 토스 API용 RestClient. 연결·읽기 제한 시간을 설정하고, 만들 때 접속하지 않는다. 인증 헤더는 호출마다 붙인다. */
@Configuration
@EnableConfigurationProperties(TossProperties.class)
public class TossConfig {

    @Bean
    public RestClient tossRestClient(TossProperties properties) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(properties.timeout()).build());
        factory.setReadTimeout(properties.timeout());
        return RestClient.builder().baseUrl(properties.baseUrl()).requestFactory(factory).build();
    }
}
