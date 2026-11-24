package ac.mju.contractcheck.domain;

import ac.mju.contractcheck.domain.enums.ChangeType;
import java.util.List;

/** FR-CMP-02 비교 한 건의 조회 결과. 쌍은 저장 순서(이후 계약 조항 번호 순)다. */
public record ComparisonDetail(Comparison comparison, List<Entry> entries) {

    /**
     * 조항 쌍 하나와 양쪽 조항 정보. ADDED는 {@code before}가, DELETED는 {@code after}가 null이다.
     * 쌍이 가리키는 조항이 없어졌을 때도 그쪽이 null이다.
     */
    public record Entry(ComparisonPair pair, ComparedClause before, ComparedClause after) {}

    /** 변경 유형별 쌍 개수. */
    public record Summary(int added, int deleted, int modified, int same) {}

    public Summary summary() {
        return new Summary(count(ChangeType.ADDED), count(ChangeType.DELETED), count(ChangeType.MODIFIED), count(ChangeType.SAME));
    }

    private int count(ChangeType type) {
        return (int) entries.stream().filter(entry -> entry.pair().changeType() == type).count();
    }
}
