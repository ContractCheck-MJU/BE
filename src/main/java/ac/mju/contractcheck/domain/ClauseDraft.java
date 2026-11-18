package ac.mju.contractcheck.domain;

/** 분할 직후의 조항. 저장 전이라 id, 계약, 소유자가 없다. no는 1부터 이어지는 순번이다. */
public record ClauseDraft(int no, String title, String text) {}
