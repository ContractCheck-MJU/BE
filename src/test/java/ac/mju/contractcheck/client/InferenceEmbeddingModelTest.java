package ac.mju.contractcheck.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingResponse;

@ExtendWith(MockitoExtension.class)
class InferenceEmbeddingModelTest {

    @Mock
    InferenceClient inference;

    @InjectMocks
    InferenceEmbeddingModel model;

    @Test
    @DisplayName("EmbeddingModel.embed(List)는 InferenceClient 한 번(배치)으로 처리하고 순서를 지킨다")
    void embedsListInOneBatch() {
        float[] first = {1f};
        float[] second = {2f};
        when(inference.embed(List.of("가", "나"))).thenReturn(List.of(first, second));

        List<float[]> vectors = model.embed(List.of("가", "나"));

        assertThat(vectors).containsExactly(first, second);
        verify(inference).embed(List.of("가", "나"));
        verifyNoMoreInteractions(inference);
    }

    @Test
    @DisplayName("embed(String)·embed(Document)도 추론 서버 임베딩을 쓴다")
    void embedsSingleTextAndDocument() {
        when(inference.embed(List.of("질의"))).thenReturn(List.of(new float[] {3f}));

        assertThat(model.embed("질의")).containsExactly(3f);
        assertThat(model.embed(Document.builder().text("질의").build())).containsExactly(3f);
    }

    @Test
    @DisplayName("차원은 서버를 부르지 않고 1024로 답한다")
    void dimensionsDoesNotCallServer() {
        assertThat(model.dimensions()).isEqualTo(1024);
        verifyNoMoreInteractions(inference);
    }

    @Test
    @DisplayName("추론 서버 실패는 InferenceException으로 그대로 올라온다")
    void failurePropagates() {
        when(inference.embed(List.of("가"))).thenThrow(new InferenceException("down"));

        assertThatThrownBy(() -> model.embed(List.of("가"))).isInstanceOf(InferenceException.class);
    }

    @Test
    @DisplayName("call 응답의 index가 입력 순서와 같다")
    void responseKeepsIndexes() {
        when(inference.embed(List.of("가", "나"))).thenReturn(List.of(new float[] {1f}, new float[] {2f}));

        EmbeddingResponse response = model.embedForResponse(List.of("가", "나"));

        assertThat(response.getResults()).extracting(e -> e.getIndex()).containsExactly(0, 1);
    }
}
