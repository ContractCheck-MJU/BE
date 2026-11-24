package ac.mju.contractcheck.domain;

import ac.mju.contractcheck.domain.enums.AnalysisStatus;
import java.time.Instant;

/**
 * 계약 하나에 대한 분석 한 건. 저장되는 값을 모두 가진다.
 * 접수 직후에는 {@code ANALYZING}이고 점수·요약은 비어 있으며, 끝나면 {@link #analyzed}나 {@link #failed}로 바뀐 새 객체를 저장한다.
 */
public record Analysis(
        long id,
        long contractId,
        UserId owner,
        AnalysisStatus status,
        double overallScore,
        String summary,
        String modelVersion,
        Instant createdAt) {

    /** 새로 접수한 분석(아직 저장 전이라 id 0). */
    public static Analysis accepted(long contractId, UserId owner, Instant createdAt) {
        return new Analysis(0, contractId, owner, AnalysisStatus.ANALYZING, 0, "", null, createdAt);
    }

    public Analysis analyzed(AnalysisSummary result, String modelVersion) {
        return new Analysis(id, contractId, owner, AnalysisStatus.ANALYZED, result.overallScore(), result.summary(), modelVersion, createdAt);
    }

    public Analysis failed() {
        return new Analysis(id, contractId, owner, AnalysisStatus.FAILED, 0, "", modelVersion, createdAt);
    }
}
