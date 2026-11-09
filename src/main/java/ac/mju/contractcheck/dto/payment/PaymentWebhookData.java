package ac.mju.contractcheck.dto.payment;

public record PaymentWebhookData(String paymentKey, String status) {}
