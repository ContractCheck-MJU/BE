package ac.mju.contractcheck.domain;

import java.time.Instant;

public record Contract(
        long id,
        String owner,
        String title,
        String contractType,
        String s3Key,
        String status,
        String maskedText,
        Instant createdAt,
        String errorMessage) {

    public Contract withStatus(String status, String errorMessage) {
        return new Contract(id, owner, title, contractType, s3Key, status, maskedText, createdAt, errorMessage);
    }
}
