package ac.mju.contractcheck.domain;

public record Payment(long id, UserId owner, String orderId, int amount, String orderName, String status, int creditsAdded) {}
