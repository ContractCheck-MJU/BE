package ac.mju.contractcheck.domain;

/** 추론 서버에 보낼 조항 한 건. id는 응답과 맞춰 보는 키(조항 ID 문자열)다. */
public record ClauseText(String id, String text) {
}
