package ac.mju.contractcheck.controller;

import ac.mju.contractcheck.domain.NotificationSettings;
import ac.mju.contractcheck.dto.common.PageResponse;
import ac.mju.contractcheck.dto.notification.NotificationItem;
import ac.mju.contractcheck.dto.notification.NotificationPushRequest;
import ac.mju.contractcheck.dto.notification.NotificationSettingsRequest;
import ac.mju.contractcheck.global.security.CurrentUser;
import ac.mju.contractcheck.service.NotificationService;

import java.util.List;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * FR-NOT-02 알림 설정·목록
 */
@RestController
@RequiredArgsConstructor
public class NotificationRestController {
    @NonNull
    private final NotificationService notifications;
    @NonNull
    private final CurrentUser currentUser;

    /**
     * FR-NOT-02 알림 설정·목록
     *
     * TODO(FR-NOT-02): page/size를 NotificationService.list의 쿼리에 전달한다. 리마인드 발송(FR-NOT-01)은 NotificationService의 @Scheduled 메서드에서 구현한다.
     */
    @GetMapping("/api/v1/notifications")
    public PageResponse<NotificationItem> list(Authentication authentication, @RequestParam(defaultValue = "0") int page) {
        return new PageResponse<>(
                notifications.list(currentUser.username(authentication)).stream().map(NotificationItem::from).toList(),
                page);
    }

    /** FR-NOT-02 알림 설정·목록 */
    @PatchMapping("/api/v1/notifications/{id}/read")
    public ResponseEntity<Void> read(Authentication authentication, @PathVariable long id) {
        notifications.markRead(currentUser.username(authentication), id);
        return ResponseEntity.noContent().build();
    }

    /** FR-NOT-02 알림 설정·목록 */
    @PutMapping("/api/v1/notifications/settings")
    public NotificationSettings settings(Authentication authentication, @RequestBody NotificationSettingsRequest request) {
        return notifications.updateSettings(
                currentUser.username(authentication), request.emailEnabled(), request.pushEnabled(), request.remindDays());
    }

    /**
     * FR-NOT-02 알림 설정·목록
     *
     * TODO(FR-NOT-02): request의 endpoint뿐 아니라 p256dh, auth를 서비스에 전달해 push_subscriptions에 저장한다.
     */
    @PostMapping("/api/v1/notifications/push-subscriptions")
    public ResponseEntity<Void> subscribe(Authentication authentication, @RequestBody NotificationPushRequest request) {
        notifications.subscribe(currentUser.username(authentication), request.endpoint());
        return ResponseEntity.noContent().build();
    }
}
