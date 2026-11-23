package ac.mju.contractcheck.domain;

/**
 * 회원 권한(스펙시트 컬럼 정의서 users.role).
 * 이름이 DB에 저장되는 문자열이므로 바꾸지 않는다. 엔티티에는 {@link #name()}을 그대로 저장한다.
 */
public enum MemberRole {
    USER,
    ADMIN
}
