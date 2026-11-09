package ac.mju.contractcheck.dto.payment;

import ac.mju.contractcheck.domain.Payment;

public record PaymentItem(long paymentId, String orderId, int amount, String status) {
    public static PaymentItem from(Payment payment) {
        return new PaymentItem(payment.id(), payment.orderId(), payment.amount(), payment.status());
    }
}
