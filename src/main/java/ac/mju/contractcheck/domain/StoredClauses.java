package ac.mju.contractcheck.domain;

import java.util.List;

/**
 * 분할해 저장한 조항.
 *
 * @param clauses 저장된 조항. 번호 순서다.
 * @param paragraphFallback 조항 패턴이 없어 문단 단위로 대체 분할했으면 true. 사용자 수정 안내를 띄우는 근거다(TC-UPL-012).
 */
public record StoredClauses(List<Clause> clauses, boolean paragraphFallback) {}
