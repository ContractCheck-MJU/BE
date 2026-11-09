package ac.mju.contractcheck.domain;

import java.time.Instant;

public record Contract(
        long id,
        String owner,
        String title,
        String contractType,
        String status,
        String maskedText,
        Instant createdAt,
        String errorMessage) {

    public Contract withStatus(String status, String errorMessage) {
        return new Contract(id, owner, title, contractType, status, maskedText, createdAt, errorMessage);
    }
}
