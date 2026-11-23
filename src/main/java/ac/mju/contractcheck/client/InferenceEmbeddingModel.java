package ac.mju.contractcheck.client;

import java.util.ArrayList;
import java.util.List;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Component;

/**
 * Spring AI {@link EmbeddingModel} 어댑터. 실제 임베딩은 추론 서버(FastAPI POST /embed, BGE-M3 1024차원)가 하고,
 * 이 클래스는 {@link InferenceClient}를 통해서만 부른다. 그래서 RAG 구성요소(검색기·어드바이저)가 같은 임베딩을 쓴다.
 * Spring AI의 VectorStore는 쓰지 않는다. 벡터는 우리 테이블(legal_chunks, kftc_cases, clauses)에 두고 native SQL로 검색한다.
 */
@Component
@RequiredArgsConstructor
public class InferenceEmbeddingModel implements EmbeddingModel {

    @NonNull
    private final InferenceClient inference;

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<float[]> vectors = inference.embed(request.getInstructions());
        List<Embedding> embeddings = new ArrayList<>(vectors.size());
        for (int index = 0; index < vectors.size(); index++) {
            embeddings.add(new Embedding(vectors.get(index), index));
        }
        return new EmbeddingResponse(embeddings);
    }

    @Override
    public float[] embed(Document document) {
        return embed(document.getText() == null ? "" : document.getText());
    }

    /** 기본 구현은 차원을 알아내려고 서버를 한 번 부른다. 모델이 고정(BGE-M3)이라 상수로 답한다. */
    @Override
    public int dimensions() {
        return HttpInferenceClient.EMBEDDING_DIMENSION;
    }
}
