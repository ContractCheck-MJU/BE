package ac.mju.contractcheck.dto.payment;

public record ConfirmedPayment(long paymentId, String status, int creditsAdded) {}
