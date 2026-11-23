package ac.mju.contractcheck.domain;

import java.util.List;

public record NotificationSettings(UserId owner, boolean emailEnabled, boolean pushEnabled, List<Integer> remindDays) {}
