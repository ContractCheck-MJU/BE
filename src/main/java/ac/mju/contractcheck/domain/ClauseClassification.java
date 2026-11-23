package ac.mju.contractcheck.domain;

/**
 * 추론 서버 /classify의 조항별 결과.
 *
 * @param clauseId 요청의 조항 id
 * @param classification 위험 여부와 확률(등급 결정 입력)
 * @param riskType 불공정 유형. 위험이 아니면 빈 문자열이다.
 * @param modelVersion 추론 서버가 응답한 모델 버전(analyses.model_version)
 */
public record ClauseClassification(String clauseId, Classification classification, String riskType, String modelVersion) {
}
