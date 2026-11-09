package ac.mju.contractcheck.dto.payment;

public record PaymentConfirmRequest(String paymentKey, String orderId, int amount) {}
