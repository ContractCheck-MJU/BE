package ac.mju.contractcheck.domain;

/**
 * 등급 결정 임계값. 확률 범위(0~1)를 벗어난 값으로는 만들 수 없다(정합성). 설정 파일 바인딩은 config가 맡는다.
 *
 * @param confidenceThreshold 위험 확률이 이 값 미만이면 HIGH로 확정하지 않는다 (TC-ANL-017: 0.6)
 * @param evidenceScoreThreshold 리랭크 점수가 이 값 이상인 근거만 인정한다 (AI·RAG 파이프라인 7단계: 0.5)
 */
public record RiskPolicy(double confidenceThreshold, double evidenceScoreThreshold) {

    public RiskPolicy {
        requireProbability("confidenceThreshold", confidenceThreshold);
        requireProbability("evidenceScoreThreshold", evidenceScoreThreshold);
    }

    private static void requireProbability(String name, double value) {
        if (Double.isNaN(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + "은 0 이상 1 이하여야 합니다: " + value);
        }
    }
}
