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

    /** 마스킹을 거친 본문을 담은 새 계약. 원문은 담지 않는다(FR-UPL-04). */
    public Contract withMaskedText(String maskedText) {
        return new Contract(id, owner, title, contractType, s3Key, status, maskedText, createdAt, errorMessage);
    }

    public Contract withStatus(ContractState status, String errorMessage) {
        return new Contract(id, owner, title, contractType, s3Key, status, maskedText, createdAt, errorMessage);
    }
}
