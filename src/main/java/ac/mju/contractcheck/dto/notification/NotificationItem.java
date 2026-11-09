package ac.mju.contractcheck.dto.notification;

import ac.mju.contractcheck.domain.Notification;

public record NotificationItem(long id, String channel, String title, String sentAt, boolean read) {
    public static NotificationItem from(Notification notification) {
        return new NotificationItem(
                notification.id(), notification.channel(), notification.title(), notification.sentAt(), notification.read());
    }
}
