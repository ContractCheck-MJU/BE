package ac.mju.contractcheck.dto.milestone;

import ac.mju.contractcheck.domain.Milestone;

public record MilestoneItem(long id, String type, String dueDate, String title, int remindDays, boolean done) {
    public static MilestoneItem from(Milestone milestone) {
        return new MilestoneItem(
                milestone.id(),
                milestone.type(),
                milestone.dueDate() == null ? "" : milestone.dueDate(),
                milestone.title(),
                milestone.remindDays(),
                milestone.done());
    }
}
