package ac.mju.contractcheck.dto.payment;

public record PaymentWebhookRequest(String eventType, PaymentWebhookData data) {}
