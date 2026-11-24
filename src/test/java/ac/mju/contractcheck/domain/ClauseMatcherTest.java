package ac.mju.contractcheck.domain;

import static org.assertj.core.api.Assertions.assertThat;

import ac.mju.contractcheck.domain.ClauseMatcher.Match;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** FR-CMP-01 조항 짝 맞추기. 유사도 합이 최대가 되게 1:1로 짝짓고 임계값 미만은 짝짓지 않는다. */
class ClauseMatcherTest {

    private static final double THRESHOLD = 0.6;

    @Test
    @DisplayName("FR-CMP-01 순서가 바뀐 조항도 유사도 합이 최대가 되게 짝짓는다(높은 것부터 고르면 놓치는 경우)")
    void matchesToMaximizeTotalSimilarity() {
        // 높은 것부터 고르면 (0,0)=0.9를 먼저 잡고 1번은 0.1만 남아 짝을 잃는다. 최적은 (0,1)+(1,0)=1.65다.
        double[][] similarity = {
                {0.9, 0.8},
                {0.85, 0.1}};

        assertThat(ClauseMatcher.match(similarity, THRESHOLD))
                .containsExactlyInAnyOrder(new Match(0, 1, 0.8), new Match(1, 0, 0.85));
    }

    @Test
    @DisplayName("FR-CMP-01 임계값 미만인 쌍은 짝짓지 않고, 임계값과 같으면 짝짓는다")
    void dropsPairsBelowThreshold() {
        double[][] similarity = {
                {0.6, 0.1},
                {0.2, 0.59}};

        assertThat(ClauseMatcher.match(similarity, THRESHOLD)).containsExactly(new Match(0, 0, 0.6));
    }

    @Test
    @DisplayName("FR-CMP-01 이전·이후 조항 수가 달라도 짝을 만들고 남는 조항은 짝이 없다")
    void handlesDifferentClauseCounts() {
        double[][] moreAfter = {{0.1, 0.95, 0.3}};
        double[][] moreBefore = {{0.2}, {0.7}, {0.9}};

        assertThat(ClauseMatcher.match(moreAfter, THRESHOLD)).containsExactly(new Match(0, 1, 0.95));
        assertThat(ClauseMatcher.match(moreBefore, THRESHOLD)).containsExactly(new Match(2, 0, 0.9));
    }

    @Test
    @DisplayName("FR-CMP-01 한쪽에 조항이 없으면 짝이 없다")
    void returnsNoMatchForEmptySide() {
        assertThat(ClauseMatcher.match(new double[0][0], THRESHOLD)).isEmpty();
        assertThat(ClauseMatcher.match(new double[][] {{}, {}}, THRESHOLD)).isEmpty();
    }
}
