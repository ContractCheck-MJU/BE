package ac.mju.contractcheck.dto.notification;

import java.util.List;

public record NotificationSettingsRequest(boolean emailEnabled, boolean pushEnabled, List<Integer> remindDays) {}
