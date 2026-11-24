package ac.mju.contractcheck.dto.comparison;

import ac.mju.contractcheck.domain.ComparedClause;
import ac.mju.contractcheck.domain.ComparisonDetail;

/**
 * 조항 쌍 하나. ADDED는 before가, DELETED는 after가 null이고 둘 다 similarity가 null이다.
 * riskDelta는 비교 당시 위험 등급 점수(SAFE 0·CAUTION 1·HIGH 2)의 이후 − 이전이다.
 */
public record ComparisonPairItem(
        String changeType, Double similarity, int riskDelta, ComparedClauseItem before, ComparedClauseItem after) {

    public static ComparisonPairItem from(ComparisonDetail.Entry entry) {
        return new ComparisonPairItem(
                entry.pair().changeType().name(),
                entry.pair().similarity(),
                entry.pair().riskDelta(),
                ComparedClauseItem.from(entry.before()),
                ComparedClauseItem.from(entry.after()));
    }

    /** 조항 한쪽. riskLevel은 조회 시점의 최신 분석 기준이며 판정이 없으면 null이다. */
    public record ComparedClauseItem(long clauseId, int no, String title, String text, String riskLevel) {

        static ComparedClauseItem from(ComparedClause clause) {
            if (clause == null) {
                return null;
            }
            return new ComparedClauseItem(
                    clause.clauseId(), clause.no(), clause.title(), clause.text(),
                    clause.riskLevel() == null ? null : clause.riskLevel().name());
        }
    }
}
