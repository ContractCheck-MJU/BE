package ac.mju.contractcheck.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InferencePropertiesTest {

    @Test
    @DisplayName("스펙 기본값(제한 시간 10초, 배치 64)으로 만들 수 있다")
    void acceptsValidValues() {
        InferenceProperties properties = new InferenceProperties("http://x", "k", Duration.ofSeconds(10), 3, Duration.ofMillis(200), 64);

        assertThat(properties.batchSize()).isEqualTo(64);
        assertThat(properties.maxAttempts()).isEqualTo(3);
    }

    @Test
    @DisplayName("배치 크기 0 이하는 거절한다(나누는 반복이 끝나지 않는다)")
    void rejectsNonPositiveBatchSize() {
        assertThatThrownBy(() -> new InferenceProperties("http://x", "k", Duration.ofSeconds(10), 3, Duration.ZERO, 0))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("batch-size");
    }

    @Test
    @DisplayName("시도 횟수 0 이하는 거절한다(한 번도 호출하지 않고 실패한다)")
    void rejectsNonPositiveAttempts() {
        assertThatThrownBy(() -> new InferenceProperties("http://x", "k", Duration.ofSeconds(10), 0, Duration.ZERO, 64))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("max-attempts");
    }

    @Test
    @DisplayName("제한 시간은 양수, 재시도 대기는 0 이상이어야 한다")
    void rejectsInvalidDurations() {
        assertThatThrownBy(() -> new InferenceProperties("http://x", "k", Duration.ZERO, 3, Duration.ZERO, 64))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("timeout");
        assertThatThrownBy(() -> new InferenceProperties("http://x", "k", Duration.ofSeconds(1), 3, Duration.ofMillis(-1), 64))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("retry-backoff");
    }
}
