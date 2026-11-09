package ac.mju.contractcheck.domain;

public record Payment(long id, String owner, String orderId, int amount, String orderName, String status, int creditsAdded) {}
