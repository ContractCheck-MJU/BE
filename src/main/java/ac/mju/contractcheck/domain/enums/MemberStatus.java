package ac.mju.contractcheck.domain.enums;

/**
 * 회원 상태(스펙시트 컬럼 정의서 users.status).
 * 이름이 DB에 저장되는 문자열이므로 바꾸지 않는다. 엔티티에는 {@link #name()}을 그대로 저장한다.
 */
public enum MemberStatus {
    ACTIVE,
    WITHDRAWN
}
