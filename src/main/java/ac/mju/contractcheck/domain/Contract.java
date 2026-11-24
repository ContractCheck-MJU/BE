package ac.mju.contractcheck.domain;

import ac.mju.contractcheck.domain.enums.ContractState;
import java.time.Instant;

public record Contract(
        long id,
        UserId owner,
        String title,
        String contractType,
        String s3Key,
        ContractState status,
        String maskedText,
        Instant createdAt,
        String errorMessage) {

    public Contract withStatus(ContractState status, String errorMessage) {
        return new Contract(id, owner, title, contractType, s3Key, status, maskedText, createdAt, errorMessage);
    }
}
