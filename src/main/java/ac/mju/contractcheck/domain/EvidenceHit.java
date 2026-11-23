package ac.mju.contractcheck.domain;

/**
 * 근거 검색 결과 한 건(legal_chunks 청크 단위).
 *
 * @param text 청크 본문(법령·판례 원문 일부). 설명 생성의 RAG 컨텍스트와 근거 인용에 쓴다.
 * @param similarity 코사인 유사도(1 - 코사인 거리). 리랭커가 없어 이 값을 근거 점수로 쓴다.
 */
public record EvidenceHit(long documentId, long chunkId, String docType, String title, String ref, String text, double similarity) {
}
