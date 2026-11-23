package ac.mju.contractcheck.service;

import ac.mju.contractcheck.domain.EvidenceHit;
import ac.mju.contractcheck.repository.EvidenceRepository;
import java.util.List;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.stereotype.Service;

/**
 * 법령·판례 근거 검색기. Spring AI RAG의 {@link DocumentRetriever}를 우리 테이블(legal_chunks)에 맞춰 구현했다.
 * 쉬운말 설명(FR-EXP-01)에서는 이 검색기를 RetrievalAugmentationAdvisor에 꽂아 Claude에 근거 컨텍스트를 준다.
 *
 * <p>조항을 이미 임베딩해 둔 경우(위험 판정)에는 {@link #search(float[], int)}로 다시 임베딩하지 않고 바로 검색한다.
 * Spring AI VectorStore 테이블은 만들지 않는다. 트랜잭션 밖에서 부른다(임베딩이 외부 호출이다).
 */
@Service
@RequiredArgsConstructor
public class EvidenceRetriever implements DocumentRetriever {

    /** 근거 후보 개수. 스펙: 코사인 Top-k(각 5). */
    public static final int DEFAULT_LIMIT = 5;

    public static final String DOC_TYPE = "docType";
    public static final String TITLE = "title";
    public static final String REF = "ref";
    public static final String DOCUMENT_ID = "documentId";

    @NonNull
    private final EmbeddingModel embeddingModel;
    @NonNull
    private final EvidenceRepository evidence;

    /** 질의 텍스트를 임베딩해 근거 문서를 찾는다. 점수(score)는 코사인 유사도다. */
    @Override
    public List<Document> retrieve(Query query) {
        return search(embeddingModel.embed(query.text()), DEFAULT_LIMIT).stream().map(EvidenceRetriever::toDocument).toList();
    }

    /** 이미 만든 임베딩으로 검색한다. */
    public List<EvidenceHit> search(@NonNull float[] embedding, int limit) {
        return evidence.findSimilar(embedding, limit);
    }

    static Document toDocument(EvidenceHit hit) {
        return Document.builder()
                .id("legal_chunk:" + hit.chunkId())
                .text(hit.text())
                .metadata(DOCUMENT_ID, hit.documentId())
                .metadata(DOC_TYPE, hit.docType())
                .metadata(TITLE, hit.title())
                .metadata(REF, hit.ref())
                .score(hit.similarity())
                .build();
    }
}
