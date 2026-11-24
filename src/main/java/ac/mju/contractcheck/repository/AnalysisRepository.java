package ac.mju.contractcheck.repository;

import ac.mju.contractcheck.domain.Analysis;
import ac.mju.contractcheck.domain.enums.AnalysisStatus;
import ac.mju.contractcheck.domain.enums.RiskLevel;
import ac.mju.contractcheck.domain.RiskResult;
import ac.mju.contractcheck.domain.UserId;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * V1__init.sql의 {@code analyses}, {@code risk_results} 행을 읽고 쓴다. 도메인 값을 엔티티로 바꿔 저장하는 일만 하고,
 * 분석을 언제 접수·완료·실패 처리할지와 트랜잭션 경계는 호출하는 service가 정한다.
 * 소유자는 analyses.contract_id → contracts.user_id로 확인한다.
 */
@Repository
@RequiredArgsConstructor
public class AnalysisRepository implements OwnedDataEraser {

    @NonNull
    private final AnalysisJpaRepository analyses;
    @NonNull
    private final RiskResultJpaRepository results;
    @NonNull
    private final ContractJpaRepository contracts;

    /** id가 0이면 새 행을 만들고, 아니면 그 행을 도메인 값으로 갱신한다. */
    public @NonNull Analysis save(@NonNull Analysis analysis) {
        AnalysisEntity saved = analyses.save(new AnalysisEntity(
                analysis.id() == 0 ? null : analysis.id(),
                analysis.contractId(),
                analysis.modelVersion(),
                BigDecimal.valueOf(analysis.overallScore()).setScale(2, RoundingMode.HALF_UP),
                analysis.summary(),
                analysis.status().name(),
                analysis.createdAt()));
        return toAnalysis(saved, analysis.owner());
    }

    public @NonNull Optional<Analysis> findOwned(long id, UserId owner) {
        return analyses.findOwned(id, owner.value()).map(entity -> toAnalysis(entity, owner));
    }

    /** 같은 계약에 판정 중인 분석이 있는지 본다. */
    public boolean existsInProgress(long contractId) {
        return analyses.existsByContractIdAndStatus(contractId, AnalysisStatus.ANALYZING.name());
    }

    /** 접수한 지 {@code before}보다 오래됐는데 아직 판정 중인 분석. 서버가 중간에 죽어 멈춘 분석을 찾는 데 쓴다. */
    public @NonNull List<Analysis> findInProgressBefore(@NonNull Instant before) {
        return analyses.findByStatusAndCreatedAtBefore(AnalysisStatus.ANALYZING.name(), before).stream()
                .flatMap(entity -> contracts.findById(entity.getContractId())
                        .map(contract -> toAnalysis(entity, new UserId(contract.getUserId()))).stream())
                .toList();
    }

    /** 분석 행과 그 조항별 결과를 지운다. 접수 직후 차감에 실패했을 때 분석 행을 남기지 않으려고 쓴다. */
    public void delete(long id) {
        results.deleteByAnalysisId(id);
        analyses.deleteById(id);
    }

    public void saveResults(long analysisId, @NonNull List<RiskResult> items) {
        results.saveAll(items.stream().map(item -> new RiskResultEntity(
                analysisId, item.clauseId(), item.risky(), item.level().name(), item.riskType(),
                BigDecimal.valueOf(item.probability()).setScale(4, RoundingMode.HALF_UP))).toList());
    }

    public @NonNull List<RiskResult> findResults(long analysisId) {
        return results.findByAnalysisIdOrderByClauseId(analysisId).stream()
                .map(entity -> new RiskResult(
                        entity.getClauseId(), entity.isRisky(), RiskLevel.valueOf(entity.getRiskLevel()),
                        entity.getRiskType(), entity.getProbability().doubleValue()))
                .toList();
    }

    /** 탈퇴 시 소유자의 분석과 조항별 결과를 모두 지운다(FR-AUTH-03). */
    @Override
    public void deleteAllByOwner(UserId owner) {
        results.deleteAllByUserId(owner.value());
        analyses.deleteAllByUserId(owner.value());
    }

    private Analysis toAnalysis(AnalysisEntity entity, UserId owner) {
        return new Analysis(
                entity.getId(), entity.getContractId(), owner, AnalysisStatus.valueOf(entity.getStatus()),
                entity.getOverallScore() == null ? 0 : entity.getOverallScore().doubleValue(),
                entity.getSummary() == null ? "" : entity.getSummary(),
                entity.getModelVersion(), entity.getCreatedAt());
    }
}
