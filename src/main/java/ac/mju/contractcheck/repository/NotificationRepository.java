package ac.mju.contractcheck.repository;

import ac.mju.contractcheck.domain.Notification;
import ac.mju.contractcheck.domain.NotificationSettings;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.NonNull;
import org.springframework.stereotype.Repository;

/**
 * 인메모리 저장소. 영속화 시 V1__init.sql의 {@code notifications}, {@code notification_settings},
 * {@code push_subscriptions} 테이블에 매핑한다. 읽음은 notifications.read_at으로 표현한다.
 */
@Repository
public class NotificationRepository {
    private final ConcurrentHashMap<Long, Notification> notifications = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, NotificationSettings> settings = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> pushSubscriptions = new ConcurrentHashMap<>();

    public List<Notification> findByOwner(String owner) {
        return notifications.values().stream().filter(item -> item.owner().equals(owner)).toList();
    }

    public @NonNull Optional<Notification> findOwned(long id, String owner) {
        return Presence.owned(notifications.get(id), owner, Notification::owner);
    }

    public Notification save(Notification notification) {
        notifications.put(notification.id(), notification);
        return notification;
    }

    public NotificationSettings settings(String owner) {
        return settings.getOrDefault(owner, new NotificationSettings(owner, true, false, List.of(30, 7, 1)));
    }

    public NotificationSettings saveSettings(NotificationSettings value) {
        settings.put(value.owner(), value);
        return value;
    }

    public void savePush(String owner, String endpoint) {
        pushSubscriptions.put(owner, endpoint);
    }
}
