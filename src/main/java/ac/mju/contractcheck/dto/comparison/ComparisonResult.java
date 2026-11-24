package ac.mju.contractcheck.dto.comparison;

import ac.mju.contractcheck.domain.ComparisonDetail;
import java.util.List;

/** 비교 조회 응답. pairs는 이후 계약 조항 번호 순이고 SAME 쌍도 포함한다. */
public record ComparisonResult(
        long comparisonId,
        long beforeContractId,
        long afterContractId,
        String createdAt,
        ComparisonSummary summary,
        List<ComparisonPairItem> pairs) {

    public static ComparisonResult from(ComparisonDetail detail) {
        ComparisonDetail.Summary summary = detail.summary();
        return new ComparisonResult(
                detail.comparison().id(),
                detail.comparison().beforeContractId(),
                detail.comparison().afterContractId(),
                detail.comparison().createdAt() == null ? null : detail.comparison().createdAt().toString(),
                new ComparisonSummary(summary.added(), summary.deleted(), summary.modified(), summary.same()),
                detail.entries().stream().map(ComparisonPairItem::from).toList());
    }
}
