package ac.mju.contractcheck.domain;

public record Notification(long id, String owner, String channel, String title, String sentAt, boolean read) {
    public Notification markRead() {
        return new Notification(id, owner, channel, title, sentAt, true);
    }
}
