package ac.mju.contractcheck.dto.milestone;

public record MilestoneCreateRequest(long contractId, String type, String title, String dueDate, int remindDays) {}
