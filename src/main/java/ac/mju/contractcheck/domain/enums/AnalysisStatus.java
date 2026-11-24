package ac.mju.contractcheck.domain.enums;

/**
 * 분석 진행 상태(스펙시트 analyses.status). 이름이 DB에 저장되는 문자열이므로 바꾸지 않는다.
 */
public enum AnalysisStatus {
    /** 접수되어 판정 중. 같은 계약의 새 분석을 막는다. */
    ANALYZING,
    ANALYZED,
    FAILED
}
