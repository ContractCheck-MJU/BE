package ac.mju.contractcheck.domain;

/**
 * 이용권 잔여 출처(무료·유료). {@code credit_ledger.credit_source}에 같은 이름으로 저장한다.
 * 차감은 FREE → PAID 순이고, 분석 실패 복구는 차감했던 출처(스택 역순)로 되돌린다.
 */
public enum CreditSource {
    FREE,
    PAID
}
