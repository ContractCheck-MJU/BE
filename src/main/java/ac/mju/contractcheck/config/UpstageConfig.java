package ac.mju.contractcheck.config;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Upstage API용 RestClient. 연결·읽기 제한 시간을 설정하고, 만들 때 접속하지 않는다. 인증 헤더는 호출마다 붙인다. */
@Configuration
@EnableConfigurationProperties(UpstageProperties.class)
public class UpstageConfig {

    @Bean
    public RestClient upstageRestClient(
            UpstageProperties properties, @Value("${contractcheck.ocr.timeout:120s}") Duration timeout) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(timeout).build());
        factory.setReadTimeout(timeout);
        return RestClient.builder().baseUrl(properties.baseUrl()).requestFactory(factory).build();
    }
}
