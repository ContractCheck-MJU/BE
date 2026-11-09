package ac.mju.contractcheck.dto.ingest;

public record IngestJobStatus(String status, int processed, int failed) {}
