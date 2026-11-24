package ac.mju.contractcheck.domain.enums;

/**
 * 결제(주문) 상태. 이름이 DB(payments.status)에 저장되는 문자열이므로 바꾸지 않는다.
 *
 * <p>스펙 미정: payments.status 허용 값과 토스 상태 매핑. 여기서는 토스의 DONE·CANCELED와 이름을 맞추고,
 * 서버가 승인 요청을 처리하는 동안을 {@link #IN_PROGRESS}, 승인 거절·검증 실패를 {@link #FAILED}로 둔 임시 값이다.
 */
public enum PaymentStatus {
    /** 주문만 만들어졌고 아직 승인하지 않았다. */
    READY,
    /** 서버가 토스 승인 API를 부르는 중이다. 같은 주문의 동시 승인을 막는다. */
    IN_PROGRESS,
    /** 토스 승인이 끝나 이용권을 지급했다. */
    DONE,
    /** 취소되어 이용권을 회수했다. */
    CANCELED,
    /** 승인이 거절되었거나 검증에 실패해 더는 승인할 수 없다. */
    FAILED
}
