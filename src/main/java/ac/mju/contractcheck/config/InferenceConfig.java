package ac.mju.contractcheck.config;

import java.net.http.HttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 추론 서버용 RestClient. 연결·읽기 제한 시간을 모두 설정하고, 클라이언트는 만들 때 접속하지 않는다.
 * HTTP/1.1로 고정한다. JDK HttpClient의 기본값은 평문 HTTP에서 h2c 업그레이드를 시도하는데 uvicorn(FastAPI)이
 * 이를 지원하지 않아 400(Invalid HTTP request)으로 거절한다.
 */
@Configuration
@EnableConfigurationProperties(InferenceProperties.class)
public class InferenceConfig {

    @Bean
    public RestClient inferenceRestClient(InferenceProperties properties) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(properties.timeout()).build());
        factory.setReadTimeout(properties.timeout());
        return RestClient.builder().baseUrl(properties.baseUrl()).requestFactory(factory).build();
    }
}
