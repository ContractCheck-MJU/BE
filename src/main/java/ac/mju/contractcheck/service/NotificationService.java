package ac.mju.contractcheck.service;

import ac.mju.contractcheck.global.error.NotFoundException;
import ac.mju.contractcheck.domain.Notification;
import ac.mju.contractcheck.domain.NotificationSettings;
import ac.mju.contractcheck.repository.NotificationRepository;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.util.List;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {
    @NonNull
    private final NotificationRepository notifications;

    /*
     * TODO(FR-NOT-01): 리마인드 발송. 이 클래스에 {@code @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Seoul")} 메서드를 둔다.
     * 1. 입력 검증: 스케줄 시각 기준으로 오늘 보낼 대상을 고른다. FR-NOT-01은 D-30/D-7/D-1 고정이다.
     *    notification_settings.remind_days(INT[])와 충돌한다. 스펙 미정: 고정 D-day를 쓸지, 설정 배열을 쓸지, 병행할지.
     * 2. 권한/소유자 확인: 해당 milestones.user_id의 설정만 적용한다. email_enabled/push_enabled가 false면 그 채널은 건너뛴다.
     * 3. DB/외부 호출: 미완료(done=false) 일정 중 due_date가 리마인드 오프셋에 해당하는 것을 고른다.
     *    notifications(user_id, milestone_id, channel, title, body, scheduled_at, status=대기)를 넣고
     *    SMTP(email)와 VAPID(push, push_subscriptions.endpoint/p256dh/auth)로 발송한 뒤 sent_at과 status=SENT를 기록한다.
     * 4. 상태 전이: 대기 → SENT, 실패 시 FAILED. 같은 milestone+오프셋+channel 중복 발송을 막는다.
     * 5. 예외/에러코드: 발송 실패는 행을 FAILED로 남긴다. 스펙 미정: 재시도 횟수·간격, notifications.status 허용 값.
     * 6. 반환: 없음(배치). TC-NOT 시나리오를 따른다.
     */

    /**
     * FR-NOT-02 알림 설정·목록
     *
     * TODO(FR-NOT-02): 알림 목록을 페이지로 조회한다.
     * 1. 입력 검증: page/size를 서비스로 전달한다.
     * 2. 권한/소유자 확인: notifications.user_id = 현재 사용자.
     * 3. DB/외부 호출: scheduled_at 또는 created 역순, OFFSET/LIMIT. 읽음 여부는 read_at IS NOT NULL.
     * 4. 상태 전이: 없음.
     * 5. 예외/에러코드: 없음(빈 목록 허용).
     * 6. 반환: 알림 페이지. 도메인 boolean read와 read_at 컬럼을 매핑한다.
     */
    public List<Notification> list(String owner) {
        return notifications.findByOwner(owner);
    }

    /**
     * FR-NOT-02 알림 설정·목록
     *
     * TODO(FR-NOT-02): 알림을 읽음 처리한다.
     * 1. 입력 검증: id 필수.
     * 2. 권한/소유자 확인: notifications.user_id. 아니면 NOTIFICATION_NOT_FOUND(404).
     * 3. DB/외부 호출: read_at=now()로 갱신한다. 이미 읽음이면 멱등.
     * 4. 상태 전이: 읽음 시각 기록.
     * 5. 예외/에러코드: NOTIFICATION_NOT_FOUND(404).
     * 6. 반환: 204.
     */
    public void markRead(String owner, long id) {
        Notification notification = notifications.findOwned(id, owner)
                .orElseThrow(() -> new NotFoundException("NOTIFICATION_NOT_FOUND", "알림을 찾을 수 없습니다."));
        notifications.save(notification.markRead());
        log.info("알림 읽음 notificationId={} owner={}", id, owner);
    }

    /**
     * FR-NOT-02 알림 설정·목록
     *
     * TODO(FR-NOT-02): 알림 설정을 저장한다.
     * 1. 입력 검증: remindDays는 양의 정수 배열. 스펙 미정: 허용 일수 집합(FR-NOT-01 고정 D-30/7/1과 정합).
     * 2. 권한/소유자 확인: 본인 user_id.
     * 3. DB/외부 호출: notification_settings(user_id PK)를 upsert한다(email_enabled, push_enabled, remind_days).
     * 4. 상태 전이: 설정 덮어쓰기.
     * 5. 예외/에러코드: 검증 실패 시 시트 코드.
     * 6. 반환: 저장된 설정.
     */
    public NotificationSettings updateSettings(String owner, boolean emailEnabled, boolean pushEnabled, List<Integer> remindDays) {
        return notifications.saveSettings(new NotificationSettings(owner, emailEnabled, pushEnabled, remindDays));
    }

    /**
     * FR-NOT-02 알림 설정·목록
     *
     * TODO(FR-NOT-02): 웹 푸시 구독을 저장한다.
     * 1. 입력 검증: endpoint, p256dh, auth 필수. 현재 시그니처와 컨트롤러가 endpoint만 넘기므로 DTO 필드(p256dh, auth)를 함께 전달해야 한다.
     * 2. 권한/소유자 확인: 본인 user_id.
     * 3. DB/외부 호출: push_subscriptions(user_id, endpoint UNIQUE, p256dh, auth)를 upsert한다.
     * 4. 상태 전이: 구독 행 추가/갱신.
     * 5. 예외/에러코드: 검증 실패, 중복 endpoint.
     * 6. 반환: 201/204.
     */
    public void subscribe(String owner, String endpoint) {
        notifications.savePush(owner, endpoint);
    }
}
