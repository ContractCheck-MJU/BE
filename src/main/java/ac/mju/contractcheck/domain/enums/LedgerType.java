package ac.mju.contractcheck.domain.enums;

/**
 * 이용권 원장 종류(스펙시트 컬럼 정의서 credit_ledger.type).
 * 이름이 DB에 저장되는 문자열이므로 바꾸지 않는다. 엔티티에는 {@link #name()}을 그대로 저장한다.
 */
public enum LedgerType {
    /** 지급(가입 무료 지급 등). */
    GRANT,
    /** 차감(분석 시작). */
    USE,
    /** 환불(결제 취소). */
    REFUND,
    /** 실패 복구. */
    RESTORE
}
