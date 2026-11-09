package ac.mju.contractcheck.dto.clause;

import ac.mju.contractcheck.domain.Clause;

public record ClauseItem(long clauseId, int no, String title, String text, String riskLevel, boolean needsReanalysis) {
    public static ClauseItem from(Clause clause) {
        return new ClauseItem(clause.id(), clause.no(), clause.title(), clause.text(), clause.riskLevel(), false);
    }
}
