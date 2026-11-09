package ac.mju.contractcheck.domain;

public record IngestJob(long id, String status, int processed, int failed, int inserted) {}
