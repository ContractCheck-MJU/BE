package ac.mju.contractcheck.domain;

public record Milestone(
        long id, String owner, long contractId, String type, String title, String dueDate, int remindDays, boolean done) {
    public Milestone withUpdate(String title, String dueDate, Integer remindDays, Boolean done) {
        return new Milestone(
                id, owner, contractId, type,
                title == null ? this.title : title,
                dueDate == null ? this.dueDate : dueDate,
                remindDays == null ? this.remindDays : remindDays,
                done == null ? this.done : done);
    }
}
