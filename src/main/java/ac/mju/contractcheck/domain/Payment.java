package ac.mju.contractcheck.domain;

import ac.mju.contractcheck.domain.enums.PaymentStatus;
import java.time.Instant;

/**
 * 이용권 결제 한 건. 주문을 만들 때 READY로 생기고 토스 승인이 끝나면 DONE이 된다. 저장되는 값을 모두 가진다.
 * 금액은 주문할 때의 상품 가격(서버 값)이다. {@code orderName}·{@code creditsAdded}는 상품에서 읽는 값이라 payments 테이블에는 없다.
 *
 * @param paymentKey 토스가 승인 때 돌려준 결제 키. 승인 전에는 빈 문자열이다.
 * @param method 결제 수단(토스 응답). 승인 전에는 빈 문자열이다.
 * @param approvedAt 승인 시각. 승인 전에는 null이다.
 */
public record Payment(
        long id,
        UserId owner,
        long productId,
        String orderId,
        String orderName,
        int amount,
        int creditsAdded,
        PaymentStatus status,
        String paymentKey,
        String method,
        Instant approvedAt) {

    /** 상품으로 새 주문(READY)을 만든다. 아직 저장 전이라 id는 0이다. */
    public static Payment ordered(UserId owner, Product product, String orderId) {
        return new Payment(0, owner, product.id(), orderId, product.name(), product.price(), product.credits(),
                PaymentStatus.READY, "", "", null);
    }

    public Payment withStatus(PaymentStatus status) {
        return new Payment(id, owner, productId, orderId, orderName, amount, creditsAdded, status, paymentKey, method, approvedAt);
    }

    /** 토스 승인 결과를 반영한 DONE 결제. */
    public Payment confirmed(String paymentKey, String method, Instant approvedAt) {
        return new Payment(id, owner, productId, orderId, orderName, amount, creditsAdded,
                PaymentStatus.DONE, paymentKey, method, approvedAt);
    }
}
