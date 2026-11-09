package ac.mju.contractcheck.dto.notification;

public record NotificationPushRequest(String endpoint, String p256dh, String auth) {}
