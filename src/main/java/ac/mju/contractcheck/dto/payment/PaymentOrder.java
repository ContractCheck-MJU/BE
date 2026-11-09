package ac.mju.contractcheck.dto.payment;

public record PaymentOrder(String orderId, int amount, String orderName, String customerKey) {}
