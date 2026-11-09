package ac.mju.contractcheck.domain;

/** Python 추론 서버 /classify 결과 중 등급 결정에 쓰는 값 (is_risky, prob). */
public record Classification(boolean risky, double probability) {

    public Classification {
        if (probability < 0.0 || probability > 1.0) {
            throw new IllegalArgumentException("probability must be in [0, 1]: " + probability);
        }
    }
}
