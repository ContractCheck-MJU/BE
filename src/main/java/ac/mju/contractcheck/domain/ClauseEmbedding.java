package ac.mju.contractcheck.domain;

/** 조항 하나의 임베딩(BGE-M3 1024차원). clauses.embedding에 저장해 수정본 비교(FR-CMP)에서 다시 쓴다. */
public record ClauseEmbedding(long clauseId, float[] embedding) {
}
