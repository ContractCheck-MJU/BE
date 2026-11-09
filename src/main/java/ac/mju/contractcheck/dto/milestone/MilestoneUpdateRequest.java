package ac.mju.contractcheck.dto.milestone;

public record MilestoneUpdateRequest(String title, String dueDate, Integer remindDays, Boolean done) {}
