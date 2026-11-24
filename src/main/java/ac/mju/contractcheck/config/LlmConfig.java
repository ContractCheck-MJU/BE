package ac.mju.contractcheck.config;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Claude(Anthropic) 클라이언트. 만들 때 접속하지 않으므로 키가 없어도(unset) 앱은 뜨고 설명 생성만 실패한다.
 * 환경변수에서 키를 자동으로 찾지 않고 contractcheck.llm.api-key 값만 쓴다.
 */
@Configuration
@EnableConfigurationProperties(LlmProperties.class)
public class LlmConfig {

    @Bean
    public AnthropicClient anthropicClient(LlmProperties properties) {
        return AnthropicOkHttpClient.builder()
                .apiKey(properties.apiKey())
                .timeout(properties.timeout())
                .maxRetries(properties.maxRetries())
                .build();
    }
}
