package ac.mju.contractcheck.domain;

/**
 * 조항 비교 임계값. 확률 범위(0~1)를 벗어난 값으로는 만들 수 없다. 설정 파일 바인딩은 config가 맡는다.
 *
 * @param matchThreshold 임베딩 유사도가 이 값 이상이어야 같은 조항의 짝으로 본다(FR-CMP-01: 0.6)
 */
public record ComparisonPolicy(double matchThreshold) {

    public ComparisonPolicy {
        if (Double.isNaN(matchThreshold) || matchThreshold < 0.0 || matchThreshold > 1.0) {
            throw new IllegalArgumentException("matchThreshold는 0 이상 1 이하여야 합니다: " + matchThreshold);
        }
    }
}
