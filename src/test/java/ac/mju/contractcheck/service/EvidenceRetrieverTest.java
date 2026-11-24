package ac.mju.contractcheck.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ac.mju.contractcheck.domain.EvidenceHit;
import ac.mju.contractcheck.repository.EvidenceRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.rag.Query;

@ExtendWith(MockitoExtension.class)
class EvidenceRetrieverTest {

    private static final EvidenceHit HIT =
            new EvidenceHit(
                    7, 70, "LAW", "민법 제640조(차임연체와 해지)", "제640조",
                    "임차인의 차임연체액이 2기의 차임액에 달하는 때", "https://law.go.kr/640", 0.7);

    @Mock
    EmbeddingModel embeddingModel;
    @Mock
    EvidenceRepository evidence;

    @InjectMocks
    EvidenceRetriever retriever;

    @Test
    @DisplayName("Spring AI RAG 검색: 질의를 임베딩해 근거를 Document(본문·출처 메타데이터·점수)로 돌려준다")
    void retrieveReturnsDocuments() {
        float[] queryVector = {0.1f, 0.2f};
        when(embeddingModel.embed("월세 1회 연체 즉시 해지")).thenReturn(queryVector);
        when(evidence.findSimilar(queryVector, EvidenceRetriever.DEFAULT_LIMIT)).thenReturn(List.of(HIT));

        List<Document> documents = retriever.retrieve(new Query("월세 1회 연체 즉시 해지"));

        assertThat(documents).hasSize(1);
        Document document = documents.get(0);
        assertThat(document.getId()).isEqualTo("legal_chunk:70");
        assertThat(document.getText()).contains("2기의 차임액");
        assertThat(document.getScore()).isEqualTo(0.7);
        assertThat(document.getMetadata())
                .containsEntry(EvidenceRetriever.DOC_TYPE, "LAW")
                .containsEntry(EvidenceRetriever.TITLE, "민법 제640조(차임연체와 해지)")
                .containsEntry(EvidenceRetriever.REF, "제640조")
                .containsEntry(EvidenceRetriever.URL, "https://law.go.kr/640")
                .containsEntry(EvidenceRetriever.DOCUMENT_ID, 7L);
    }

    @Test
    @DisplayName("질의어와 문서 유형으로 검색하면 임베딩 후 유형 필터와 건수로 저장소를 조회한다")
    void searchWithQueryAndDocTypeEmbedsAndFilters() {
        float[] queryVector = {0.4f, 0.5f};
        when(embeddingModel.embed("차임 연체 해지")).thenReturn(queryVector);
        when(evidence.findSimilar(queryVector, "LAW", 3)).thenReturn(List.of(HIT));

        assertThat(retriever.search("차임 연체 해지", "LAW", 3)).containsExactly(HIT);
        verify(evidence).findSimilar(queryVector, "LAW", 3);
    }

    @Test
    @DisplayName("이미 만든 임베딩으로 검색하면 다시 임베딩하지 않는다")
    void searchWithEmbeddingSkipsEmbeddingCall() {
        float[] embedding = {0.3f};
        when(evidence.findSimilar(embedding, 3)).thenReturn(List.of(HIT));

        assertThat(retriever.search(embedding, 3)).containsExactly(HIT);
        verify(evidence).findSimilar(embedding, 3);
    }

    @Test
    @DisplayName("근거가 없으면 빈 목록이다")
    void noEvidenceReturnsEmpty() {
        float[] embedding = {0.3f};
        when(embeddingModel.embed("질의")).thenReturn(embedding);
        when(evidence.findSimilar(embedding, EvidenceRetriever.DEFAULT_LIMIT)).thenReturn(List.of());

        assertThat(retriever.retrieve(new Query("질의"))).isEmpty();
    }
}
