package ac.mju.contractcheck.client;

import static org.assertj.core.api.Assertions.assertThat;

import ac.mju.contractcheck.client.mac.AppleVisionOcrClient;
import ac.mju.contractcheck.domain.enums.ContractFileType;
import ac.mju.contractcheck.domain.OcrResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.stereotype.Component;

/**
 * OCR 구현체를 설정(contractcheck.ocr.provider)만으로 고르고 바꿀 수 있는지 확인한다. 플랫폼에 상관없이 돈다.
 * Upstage 구현체가 생기면 {@link FakeOcrClient}처럼 @ConditionalOnProperty만 달면 서비스 코드는 그대로다.
 */
class OcrClientSelectionTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(context -> context.getBeanFactory()
                    .setConversionService(ApplicationConversionService.getSharedInstance()))
            .withUserConfiguration(AppleVisionOcrClient.class, FakeOcrClient.class, OcrConsumer.class)
            .withPropertyValues("contractcheck.ocr.timeout=120s");

    @Test
    @DisplayName("provider가 apple-vision이면 Apple Vision 구현체가 OcrClient로 등록된다")
    void appleVisionProviderRegistersAppleVision() {
        runner.withPropertyValues("contractcheck.ocr.provider=apple-vision").run(context -> {
            assertThat(context).hasSingleBean(OcrClient.class);
            assertThat(context.getBean(OcrClient.class)).isInstanceOf(AppleVisionOcrClient.class);
        });
    }

    @Test
    @DisplayName("provider만 바꾸면 OcrClient를 쓰는 코드는 그대로 다른 구현체를 받는다")
    void switchingProviderSwapsImplementationWithoutCodeChange() {
        runner.withPropertyValues("contractcheck.ocr.provider=fake").run(context -> {
            assertThat(context).hasSingleBean(OcrClient.class);
            assertThat(context.getBean(OcrConsumer.class).read()).isEqualTo("가짜 OCR 결과");
        });
    }

    @Test
    @DisplayName("구현체가 없는 provider이거나 설정이 없으면 OcrClient 빈이 없다")
    void unknownOrMissingProviderRegistersNothing() {
        runner.withPropertyValues("contractcheck.ocr.provider=upstage")
                .run(context -> assertThat(context).doesNotHaveBean(OcrClient.class));
        runner.run(context -> assertThat(context).doesNotHaveBean(OcrClient.class));
    }

    /** 다른 구현체를 붙이는 방법을 보여 주는 테스트용 구현체. */
    @Component
    @ConditionalOnProperty(prefix = "contractcheck.ocr", name = "provider", havingValue = "fake")
    static class FakeOcrClient implements OcrClient {

        @Override
        public OcrResult extract(byte[] content, ContractFileType type) {
            return new OcrResult("가짜 OCR 결과", 1);
        }
    }

    /** OcrClient 인터페이스에만 의존하는 코드. 서비스가 이렇게 쓴다. */
    @Component
    @ConditionalOnBean(OcrClient.class)
    static class OcrConsumer {

        private final OcrClient ocr;

        OcrConsumer(OcrClient ocr) {
            this.ocr = ocr;
        }

        String read() {
            return ocr.extract(new byte[0], ContractFileType.PNG).text();
        }
    }
}
