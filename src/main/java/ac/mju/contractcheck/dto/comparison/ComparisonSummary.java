package ac.mju.contractcheck.dto.comparison;

/** 변경 유형별 조항 쌍 개수. */
public record ComparisonSummary(int added, int deleted, int modified, int same) {}
