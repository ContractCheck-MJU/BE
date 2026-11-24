package ac.mju.contractcheck.domain;

import static org.assertj.core.api.Assertions.assertThat;

import ac.mju.contractcheck.domain.ComparisonDetail.Entry;
import ac.mju.contractcheck.domain.enums.ChangeType;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** FR-CMP-02 비교 조회 결과의 변경 유형별 개수 요약. */
class ComparisonDetailTest {

    @Test
    @DisplayName("FR-CMP-02 요약은 변경 유형별 쌍 개수이고 없는 유형은 0이다")
    void summarizesCountsByChangeType() {
        Comparison comparison = new Comparison(1, new UserId(1), 10, 20, Instant.parse("2026-11-24T00:00:00Z"));
        ComparisonDetail detail = new ComparisonDetail(comparison, List.of(
                entry(ChangeType.MODIFIED), entry(ChangeType.MODIFIED), entry(ChangeType.ADDED), entry(ChangeType.SAME)));

        assertThat(detail.summary()).isEqualTo(new ComparisonDetail.Summary(1, 0, 2, 1));
        assertThat(new ComparisonDetail(comparison, List.of()).summary()).isEqualTo(new ComparisonDetail.Summary(0, 0, 0, 0));
    }

    private static Entry entry(ChangeType type) {
        return new Entry(new ComparisonPair(null, null, null, type, 0), null, null);
    }
}
