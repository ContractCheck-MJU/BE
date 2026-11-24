package ac.mju.contractcheck.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ac.mju.contractcheck.domain.enums.ChangeType;
import ac.mju.contractcheck.domain.enums.RiskLevel;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** FR-CMP-01 짝지은 조항의 변경 유형(SAME·MODIFIED·ADDED·DELETED)과 위험 변화(risk_delta). */
class ClauseComparatorTest {

    private static final UserId OWNER = new UserId(1);
    private static final ComparisonPolicy POLICY = new ComparisonPolicy(0.6);

    @Test
    @DisplayName("TC-CMP-006 공백만 다르고 문구가 같으면 SAME, 문구가 다르면 MODIFIED다")
    void classifiesSameAndModifiedByNormalizedText() {
        List<Clause> before = List.of(clause(1, 10, 1, "보증금은  1,000만 원으로\n한다."), clause(2, 10, 2, "월세는 50만 원으로 한다."));
        List<Clause> after = List.of(clause(11, 20, 1, "보증금은 1,000만 원으로 한다. "), clause(12, 20, 2, "월세는 55만 원으로 한다."));
        List<ClauseSimilarity> similarities = List.of(
                new ClauseSimilarity(1, 11, 0.99), new ClauseSimilarity(1, 12, 0.3),
                new ClauseSimilarity(2, 11, 0.2), new ClauseSimilarity(2, 12, 0.97));

        List<ComparisonPair> pairs = ClauseComparator.compare(before, after, similarities, Map.of(), Map.of(), POLICY);

        assertThat(pairs).containsExactly(
                new ComparisonPair(1L, 11L, 0.99, ChangeType.SAME, 0),
                new ComparisonPair(2L, 12L, 0.97, ChangeType.MODIFIED, 0));
    }

    @Test
    @DisplayName("FR-CMP-01 이전에만 있는 조항은 DELETED, 이후에만 있는 조항은 ADDED이고 유사도는 비어 있다")
    void classifiesUnmatchedClausesAsDeletedOrAdded() {
        List<Clause> before = List.of(clause(1, 10, 1, "보증금 조항"), clause(2, 10, 2, "원상복구 조항"));
        List<Clause> after = List.of(clause(11, 20, 1, "보증금 조항"), clause(12, 20, 2, "반려동물 금지 조항"));
        List<ClauseSimilarity> similarities = List.of(
                new ClauseSimilarity(1, 11, 1.0), new ClauseSimilarity(1, 12, 0.1),
                new ClauseSimilarity(2, 11, 0.2), new ClauseSimilarity(2, 12, 0.4));

        List<ComparisonPair> pairs = ClauseComparator.compare(before, after, similarities, Map.of(), Map.of(), POLICY);

        assertThat(pairs).containsExactly(
                new ComparisonPair(1L, 11L, 1.0, ChangeType.SAME, 0),
                new ComparisonPair(null, 12L, null, ChangeType.ADDED, 0),
                new ComparisonPair(2L, null, null, ChangeType.DELETED, 0));
    }

    @Test
    @DisplayName("FR-CMP-01 risk_delta는 등급 점수(SAFE 0·CAUTION 1·HIGH 2)의 이후−이전이고, 없는 쪽은 0으로 본다")
    void computesRiskDeltaFromRiskLevels() {
        List<Clause> before = List.of(clause(1, 10, 1, "위약금 조항"), clause(2, 10, 2, "수선 의무 조항"));
        List<Clause> after = List.of(clause(11, 20, 1, "위약금 조항 수정"), clause(12, 20, 2, "중도 해지 금지 조항"));
        List<ClauseSimilarity> similarities = List.of(
                new ClauseSimilarity(1, 11, 0.9), new ClauseSimilarity(1, 12, 0.1),
                new ClauseSimilarity(2, 11, 0.1), new ClauseSimilarity(2, 12, 0.1));
        Map<Long, RiskLevel> beforeRisks = Map.of(1L, RiskLevel.HIGH, 2L, RiskLevel.CAUTION);
        Map<Long, RiskLevel> afterRisks = Map.of(11L, RiskLevel.SAFE, 12L, RiskLevel.HIGH);

        List<ComparisonPair> pairs = ClauseComparator.compare(before, after, similarities, beforeRisks, afterRisks, POLICY);

        assertThat(pairs).extracting(ComparisonPair::changeType, ComparisonPair::riskDelta).containsExactly(
                org.assertj.core.groups.Tuple.tuple(ChangeType.MODIFIED, -2),
                org.assertj.core.groups.Tuple.tuple(ChangeType.ADDED, 2),
                org.assertj.core.groups.Tuple.tuple(ChangeType.DELETED, -1));
    }

    @Test
    @DisplayName("FR-CMP-01 임베딩이 없어 유사도가 없는 조항은 짝짓지 않는다")
    void treatsMissingSimilarityAsNoMatch() {
        List<Clause> before = List.of(clause(1, 10, 1, "보증금 조항"));
        List<Clause> after = List.of(clause(11, 20, 1, "보증금 조항"));

        List<ComparisonPair> pairs = ClauseComparator.compare(before, after, List.of(), Map.of(), Map.of(), POLICY);

        assertThat(pairs).extracting(ComparisonPair::changeType).containsExactly(ChangeType.ADDED, ChangeType.DELETED);
    }

    @Test
    @DisplayName("FR-CMP-01 매칭 임계값은 0 이상 1 이하여야 한다")
    void policyRejectsThresholdOutOfRange() {
        assertThatThrownBy(() -> new ComparisonPolicy(1.1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ComparisonPolicy(-0.1)).isInstanceOf(IllegalArgumentException.class);
    }

    private static Clause clause(long id, long contractId, int no, String text) {
        return new Clause(id, contractId, OWNER, no, "제" + no + "조", text, "");
    }
}
