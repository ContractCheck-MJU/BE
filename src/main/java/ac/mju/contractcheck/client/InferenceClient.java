package ac.mju.contractcheck.client;

import ac.mju.contractcheck.domain.ClauseClassification;
import ac.mju.contractcheck.domain.ClauseText;
import java.util.List;

/**
 * Python 추론 서버(FastAPI)를 부르는 유일한 통로다(FR-ANL-01). 서비스는 이 인터페이스만 부른다.
 * 허용 경로는 POST /classify, POST /embed뿐이다(/similarity는 쓰지 않는다).
 *
 * <p>입력은 배치로 받고, 한 번에 보낼 수 있는 개수(기본 64)보다 많으면 구현체가 나눠 보낸다.
 * 호출은 트랜잭션 밖에서 하고, 테스트에서는 mock한다. 구현체는 조항 본문을 로그에 남기지 않는다.
 */
public interface InferenceClient {

    /** 임베딩 차원(BGE-M3). 스펙시트 /embed의 dim이다. */
    int EMBEDDING_DIMENSION = 1024;

    /**
     * 조항별 위험 여부·유형·확률을 받는다. 결과는 입력과 같은 순서다. 빈 입력이면 서버를 부르지 않고 빈 목록을 돌려준다.
     *
     * @throws InferenceException 서버가 응답하지 않거나, 거절하거나, 응답 형식이 어긋날 때
     */
    List<ClauseClassification> classify(List<ClauseText> clauses);

    /**
     * 텍스트를 BGE-M3 1024차원 정규화 벡터로 바꾼다. 결과는 입력과 같은 순서다. 빈 입력이면 서버를 부르지 않는다.
     *
     * @throws InferenceException 서버가 응답하지 않거나, 거절하거나, 차원이 어긋날 때
     */
    List<float[]> embed(List<String> texts);
}
