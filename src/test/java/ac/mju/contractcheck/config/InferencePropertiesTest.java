package ac.mju.contractcheck.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InferencePropertiesTest {

    @Test
    @DisplayName("기본값(제한 시간 10초, 분류 배치 16, 임베딩 배치 64)으로 만들 수 있다")
    void acceptsValidValues() {
        InferenceProperties properties = new InferenceProperties("http://x", "http://y", "k", Duration.ofSeconds(10), 3, Duration.ofMillis(200), 16, 64);

        assertThat(properties.classifyBatchSize()).isEqualTo(16);
        assertThat(properties.embedBatchSize()).isEqualTo(64);
        assertThat(properties.maxAttempts()).isEqualTo(3);
    }

    @Test
    @DisplayName("배치 크기는 1~64만 받는다(0이면 나누는 반복이 끝나지 않고, 서버 최대는 64)")
    void rejectsNonPositiveBatchSize() {
        assertThatThrownBy(() -> new InferenceProperties("http://x", "http://y", "k", Duration.ofSeconds(10), 3, Duration.ZERO, 0, 64))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("classify-batch-size");
        assertThatThrownBy(() -> new InferenceProperties("http://x", "http://y", "k", Duration.ofSeconds(10), 3, Duration.ZERO, 16, 65))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("embed-batch-size");
    }

    @Test
    @DisplayName("분류·임베딩 주소는 비어 있을 수 없다")
    void rejectsBlankUrls() {
        assertThatThrownBy(() -> new InferenceProperties(" ", "http://y", "k", Duration.ofSeconds(10), 3, Duration.ZERO, 16, 64))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("classify-base-url");
        assertThatThrownBy(() -> new InferenceProperties("http://x", "", "k", Duration.ofSeconds(10), 3, Duration.ZERO, 16, 64))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("embed-base-url");
    }

    @Test
    @DisplayName("시도 횟수 0 이하는 거절한다(한 번도 호출하지 않고 실패한다)")
    void rejectsNonPositiveAttempts() {
        assertThatThrownBy(() -> new InferenceProperties("http://x", "http://y", "k", Duration.ofSeconds(10), 0, Duration.ZERO, 16, 64))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("max-attempts");
    }

    @Test
    @DisplayName("제한 시간은 양수, 재시도 대기는 0 이상이어야 한다")
    void rejectsInvalidDurations() {
        assertThatThrownBy(() -> new InferenceProperties("http://x", "http://y", "k", Duration.ZERO, 3, Duration.ZERO, 16, 64))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("timeout");
        assertThatThrownBy(() -> new InferenceProperties("http://x", "http://y", "k", Duration.ofSeconds(1), 3, Duration.ofMillis(-1), 16, 64))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("retry-backoff");
    }
}
