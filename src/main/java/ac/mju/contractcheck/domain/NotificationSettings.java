package ac.mju.contractcheck.domain;

import java.util.List;

public record NotificationSettings(String owner, boolean emailEnabled, boolean pushEnabled, List<Integer> remindDays) {}
