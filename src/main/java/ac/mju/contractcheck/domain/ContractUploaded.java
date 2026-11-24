package ac.mju.contractcheck.domain;

/** 계약서 원본을 저장하고 contracts 행(UPLOADED)을 만든 직후의 사실. 이어지는 OCR·조항 분할 처리의 신호다(FR-UPL-02). */
public record ContractUploaded(UserId owner, long contractId) {
}
