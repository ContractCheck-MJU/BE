package ac.mju.contractcheck.client;

import ac.mju.contractcheck.domain.ExplanationContext;
import ac.mju.contractcheck.domain.ExplanationDraft;

/**
 * Claude를 부르는 유일한 통로다(FR-EXP-01·03). 서비스는 이 인터페이스만 부르고, 테스트에서는 mock한다.
 * 트랜잭션 밖에서 부르고, 구현체는 조항 본문을 로그에 남기지 않는다.
 */
public interface LlmClient {

    /**
     * 조항의 쉬운말 설명과 수정 문구를 만든다.
     *
     * @throws LlmTimeoutException 제한 시간 안에 답이 오지 않을 때
     * @throws LlmException 거절, 잘못된 응답, 인증·서버 오류 등 그 밖의 실패
     */
    ExplanationDraft explain(ExplanationContext context);
}
