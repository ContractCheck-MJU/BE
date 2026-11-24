package ac.mju.contractcheck.domain.enums;

/**
 * 이용권 원장이 가리키는 대상의 종류(스펙시트 컬럼 정의서 credit_ledger.ref_type).
 * 이름이 DB에 저장되는 문자열이므로 바꾸지 않는다. 엔티티에는 {@link #name()}을 그대로 저장한다.
 */
public enum LedgerRefType {
    PAYMENT,
    ANALYSIS
}
