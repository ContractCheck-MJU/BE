package ac.mju.contractcheck.domain;

import lombok.NonNull;

/** 이용권 1회 차감 결과: 차감 후 잔여 계정과 어느 풀(FREE·PAID)에서 깎았는지 함께 운반한다. */
public record CreditDeduction(@NonNull CreditAccount account, @NonNull CreditSource source) {}
