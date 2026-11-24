package ac.mju.contractcheck.domain;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 설명에 붙인 근거 하나(explanation_citations 한 행).
 *
 * @param sourceType 근거 종류. legal_documents.doc_type 값(LAW·PRECEDENT)을 그대로 쓴다.
 * @param sourceId 근거 문서 id(legal_documents.id). 같은 문서의 여러 청크는 하나로 합친다.
 * @param score 코사인 유사도(0~1). 같은 문서의 청크가 여럿이면 가장 높은 값이다.
 */
public record ExplanationCitation(String sourceType, long sourceId, double score) {

    /** 근거 청크를 문서 단위 인용으로 합친다. 처음 나온 순서(유사도 높은 순)를 유지하고 점수는 가장 높은 값을 남긴다. */
    public static List<ExplanationCitation> fromEvidence(Collection<EvidenceHit> hits) {
        Map<Long, ExplanationCitation> byDocument = new LinkedHashMap<>();
        for (EvidenceHit hit : hits) {
            byDocument.merge(
                    hit.documentId(),
                    new ExplanationCitation(hit.docType(), hit.documentId(), hit.similarity()),
                    (kept, next) -> kept.score() >= next.score() ? kept : next);
        }
        return List.copyOf(byDocument.values());
    }
}
