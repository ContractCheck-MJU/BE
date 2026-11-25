package ac.mju.contractcheck.config;

import ac.mju.contractcheck.global.interceptor.ExternalCallLog;
import ac.mju.contractcheck.global.interceptor.RestCallLoggingInterceptor;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 추론 서버용 RestClient. 분류(/classify)와 임베딩(/embed)은 주소가 달라 하나씩 만든다.
 * 연결·읽기 제한 시간을 모두 설정하고, 클라이언트는 만들 때 접속하지 않는다.
 * HTTP/1.1로 고정한다. JDK HttpClient의 기본값은 평문 HTTP에서 h2c 업그레이드를 시도하는데 uvicorn(FastAPI)이
 * 이를 지원하지 않아 400(Invalid HTTP request)으로 거절한다.
 */
@Configuration
@EnableConfigurationProperties(InferenceProperties.class)
@Import(ExternalCallLogConfig.class)
public class InferenceConfig {

    public static final String CLASSIFY_REST_CLIENT = "inferenceClassifyRestClient";
    public static final String EMBED_REST_CLIENT = "inferenceEmbedRestClient";

    @Bean(CLASSIFY_REST_CLIENT)
    public RestClient inferenceClassifyRestClient(InferenceProperties properties, ExternalCallLog log) {
        return restClient(properties.classifyBaseUrl(), properties.timeout(),
                new RestCallLoggingInterceptor("inference-classify", log, true));
    }

    @Bean(EMBED_REST_CLIENT)
    public RestClient inferenceEmbedRestClient(InferenceProperties properties, ExternalCallLog log) {
        return restClient(properties.embedBaseUrl(), properties.timeout(),
                new RestCallLoggingInterceptor("inference-embed", log, true));
    }

    static RestClient restClient(String baseUrl, Duration timeout, ClientHttpRequestInterceptor logging) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(timeout).build());
        factory.setReadTimeout(timeout);
        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).requestInterceptor(logging).build();
    }
}
