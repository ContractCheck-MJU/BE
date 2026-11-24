package ac.mju.contractcheck.domain;

import ac.mju.contractcheck.domain.enums.LedgerRefType;
import ac.mju.contractcheck.domain.enums.LedgerType;

/**
 * 이용권 원장 한 줄. 가입 지급처럼 가리키는 대상이 없으면 {@code refType}이 null이고 {@code refId}는 빈 문자열이다.
 */
public record LedgerEntry(UserId owner, LedgerType type, int amount, LedgerRefType refType, String refId, String createdAt) {}
