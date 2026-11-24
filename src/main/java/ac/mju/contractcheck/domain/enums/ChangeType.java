package ac.mju.contractcheck.domain.enums;

/** 비교한 조항 쌍의 변경 유형(comparison_pairs.change_type). 이름이 DB에 저장되는 문자열이므로 바꾸지 않는다. */
public enum ChangeType {
    /** 이후 계약에만 있는 조항. */
    ADDED,
    /** 이전 계약에만 있는 조항. */
    DELETED,
    /** 짝이 있고 문구가 다른 조항. */
    MODIFIED,
    /** 짝이 있고 공백을 빼면 문구가 같은 조항. */
    SAME
}
