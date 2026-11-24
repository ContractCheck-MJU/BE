package ac.mju.contractcheck.repository;

import ac.mju.contractcheck.domain.Comparison;
import ac.mju.contractcheck.domain.ComparisonPair;
import ac.mju.contractcheck.domain.UserId;
import ac.mju.contractcheck.domain.enums.ChangeType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * V1__init.sql의 {@code comparisons}, {@code comparison_pairs}에 저장한다. 소유자 확인은 comparisons.user_id로 하며,
 * 다른 회원의 비교는 없는 것과 같게 {@link Optional#empty()}를 돌려준다. 유사도는 스키마(NUMERIC(5,4))에 맞춰 소수 넷째 자리로 저장한다.
 * 트랜잭션은 열지 않는다. 비교와 쌍을 함께 저장하는 트랜잭션은 호출하는 service가 연다.
 */
@Repository
@RequiredArgsConstructor
public class ComparisonRepository implements OwnedDataEraser {

    @NonNull
    private final ComparisonJpaRepository comparisons;
    @NonNull
    private final ComparisonPairJpaRepository pairs;

    /** id가 0이면 새 행을 만든다. */
    public @NonNull Comparison save(@NonNull Comparison comparison) {
        ComparisonEntity saved = comparisons.save(new ComparisonEntity(
                comparison.id() == 0 ? null : comparison.id(),
                comparison.owner().value(),
                comparison.beforeContractId(),
                comparison.afterContractId(),
                comparison.createdAt()));
        return toComparison(saved);
    }

    public @NonNull Optional<Comparison> findOwned(long id, UserId owner) {
        return comparisons.findByIdAndUserId(id, owner.value()).map(ComparisonRepository::toComparison);
    }

    /** 조항 쌍을 주어진 순서대로 저장한다. 조회도 같은 순서(id 순)다. */
    public void savePairs(long comparisonId, @NonNull List<ComparisonPair> items) {
        pairs.saveAll(items.stream().map(item -> new ComparisonPairEntity(
                comparisonId,
                item.beforeClauseId(),
                item.afterClauseId(),
                item.similarity() == null
                        ? null
                        : BigDecimal.valueOf(item.similarity()).setScale(4, RoundingMode.HALF_UP),
                item.changeType().name(),
                item.riskDelta())).toList());
    }

    public @NonNull List<ComparisonPair> findPairs(long comparisonId) {
        return pairs.findByComparisonIdOrderById(comparisonId).stream()
                .map(entity -> new ComparisonPair(
                        entity.getBeforeClauseId(),
                        entity.getAfterClauseId(),
                        entity.getSimilarity() == null ? null : entity.getSimilarity().doubleValue(),
                        ChangeType.valueOf(entity.getChangeType()),
                        entity.getRiskDelta() == null ? 0 : entity.getRiskDelta()))
                .toList();
    }

    /** 탈퇴 시 소유자의 비교와 조항 쌍을 모두 지운다(FR-AUTH-03). 쌍을 먼저 지운다. */
    @Override
    public void deleteAllByOwner(UserId owner) {
        pairs.deleteAllByUserId(owner.value());
        comparisons.deleteByUserId(owner.value());
    }

    private static Comparison toComparison(ComparisonEntity entity) {
        return new Comparison(
                entity.getId(),
                new UserId(entity.getUserId()),
                entity.getBeforeContractId(),
                entity.getAfterContractId(),
                entity.getCreatedAt());
    }
}
