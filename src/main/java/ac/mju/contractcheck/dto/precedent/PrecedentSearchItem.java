package ac.mju.contractcheck.dto.precedent;

import ac.mju.contractcheck.domain.EvidenceHit;

public record PrecedentSearchItem(
        long documentId,
        long chunkId,
        String type,
        String title,
        String ref,
        String text,
        double score,
        String url) {
    public static PrecedentSearchItem from(EvidenceHit hit) {
        return new PrecedentSearchItem(
                hit.documentId(),
                hit.chunkId(),
                hit.docType(),
                hit.title(),
                hit.ref(),
                hit.text(),
                hit.similarity(),
                hit.url());
    }
}
