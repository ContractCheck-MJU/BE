package ac.mju.contractcheck.repository;

import static org.assertj.core.api.Assertions.assertThat;

import ac.mju.contractcheck.domain.Analysis;
import ac.mju.contractcheck.domain.Comparison;
import ac.mju.contractcheck.domain.ComparisonPair;
import ac.mju.contractcheck.domain.Contract;
import ac.mju.contractcheck.domain.UserId;
import ac.mju.contractcheck.domain.enums.AnalysisStatus;
import ac.mju.contractcheck.domain.enums.ChangeType;
import ac.mju.contractcheck.domain.enums.ContractState;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/** FR-CMP-01 비교·조항 쌍 JPA 영속화, 소유자 격리, 탈퇴 삭제, 계약의 최신 분석 완료 건 조회. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ComparisonRepositoryTest {

    private static final UserId OWNER = new UserId(890_001L);
    private static final UserId OTHER = new UserId(890_002L);
    private static final Instant NOW = Instant.parse("2026-11-24T00:00:00Z");

    @Autowired
    ComparisonRepository comparisons;

    @Autowired
    AnalysisRepository analyses;

    @Autowired
    ContractRepository contracts;

    @Test
    @DisplayName("FR-CMP-01 비교를 저장하면 id와 생성 시각이 남고 본인만 조회할 수 있다")
    void savesComparisonAndIsolatesOwner() {
        Comparison saved = comparisons.save(new Comparison(0, OWNER, 1L, 2L, NOW));

        assertThat(saved.id()).isPositive();
        assertThat(comparisons.findOwned(saved.id(), OWNER)).hasValueSatisfying(found -> {
            assertThat(found.beforeContractId()).isEqualTo(1L);
            assertThat(found.afterContractId()).isEqualTo(2L);
            assertThat(found.createdAt()).isEqualTo(NOW);
        });
        assertThat(comparisons.findOwned(saved.id(), OTHER)).isEmpty();
    }

    @Test
    @DisplayName("FR-CMP-01 조항 쌍은 저장한 순서대로 다시 읽히고 ADDED·DELETED의 빈 조항·유사도는 비어 있다")
    void savesPairsInOrderWithNullableSides() {
        long comparisonId = comparisons.save(new Comparison(0, OWNER, 1L, 2L, NOW)).id();
        List<ComparisonPair> pairs = List.of(
                new ComparisonPair(1L, 11L, 0.9312, ChangeType.MODIFIED, -1),
                new ComparisonPair(null, 12L, null, ChangeType.ADDED, 2),
                new ComparisonPair(2L, null, null, ChangeType.DELETED, 0));

        comparisons.savePairs(comparisonId, pairs);

        assertThat(comparisons.findPairs(comparisonId)).containsExactlyElementsOf(pairs);
    }

    @Test
    @DisplayName("FR-AUTH-03 탈퇴하면 본인의 비교와 조항 쌍만 지운다")
    void deleteAllByOwnerRemovesOnlyOwnersComparisons() {
        long mine = comparisons.save(new Comparison(0, OWNER, 1L, 2L, NOW)).id();
        long theirs = comparisons.save(new Comparison(0, OTHER, 3L, 4L, NOW)).id();
        comparisons.savePairs(mine, List.of(new ComparisonPair(1L, 11L, 1.0, ChangeType.SAME, 0)));
        comparisons.savePairs(theirs, List.of(new ComparisonPair(3L, 13L, 1.0, ChangeType.SAME, 0)));

        comparisons.deleteAllByOwner(OWNER);

        assertThat(comparisons.findOwned(mine, OWNER)).isEmpty();
        assertThat(comparisons.findPairs(mine)).isEmpty();
        assertThat(comparisons.findOwned(theirs, OTHER)).isPresent();
        assertThat(comparisons.findPairs(theirs)).hasSize(1);
    }

    @Test
    @DisplayName("TC-CMP-008 계약의 분석 중 가장 최근에 완료된 건만 찾고, 판정 중·실패 건과 남의 계약은 제외한다")
    void findsLatestAnalyzedAnalysisOfOwnedContract() {
        long contractId = contracts.save(new Contract(
                0, OWNER, "계약", "MONTHLY", "contracts/cmp-test", ContractState.SPLIT, "", NOW, null)).id();
        analyses.save(new Analysis(0, contractId, OWNER, AnalysisStatus.ANALYZED, 40, "첫 분석", "m1", NOW));
        long latest = analyses.save(new Analysis(
                0, contractId, OWNER, AnalysisStatus.ANALYZED, 60, "재분석", "m1", NOW.plusSeconds(60))).id();
        analyses.save(new Analysis(0, contractId, OWNER, AnalysisStatus.FAILED, 0, "", "m1", NOW.plusSeconds(120)));
        analyses.save(new Analysis(0, contractId, OWNER, AnalysisStatus.ANALYZING, 0, "", null, NOW.plusSeconds(180)));

        assertThat(analyses.findLatestAnalyzed(contractId, OWNER)).hasValueSatisfying(found -> {
            assertThat(found.id()).isEqualTo(latest);
            assertThat(found.status()).isEqualTo(AnalysisStatus.ANALYZED);
        });
        assertThat(analyses.findLatestAnalyzed(contractId, OTHER)).isEmpty();
    }
}
