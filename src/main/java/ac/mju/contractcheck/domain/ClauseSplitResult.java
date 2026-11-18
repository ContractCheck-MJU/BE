package ac.mju.contractcheck.domain;

import java.util.List;

/**
 * 조항 분할 결과.
 *
 * @param clauses 분할된 조항. 비어 있는 문서면 빈 목록이다.
 * @param paragraphFallback 조항 패턴이 하나도 없어 문단 단위로 대체 분할했으면 true. 사용자 수정 안내를 띄우는 근거다(TC-UPL-012).
 */
public record ClauseSplitResult(List<ClauseDraft> clauses, boolean paragraphFallback) {}
