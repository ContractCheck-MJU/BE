package ac.mju.contractcheck.domain;

/** 이용권 정책 값. 저장 방식(DB·JPA)을 모르는 순수 상수라 저장소가 아니라 domain에 둔다. */
public final class CreditPolicy {

    /** 가입한 회원에게 지급하는 무료 분석 횟수(FR-CRD-01, TC-CRD-001). */
    public static final int SIGNUP_FREE_CREDITS = 2;

    private CreditPolicy() {
    }
}
