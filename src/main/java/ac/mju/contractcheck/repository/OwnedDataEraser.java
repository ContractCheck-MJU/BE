package ac.mju.contractcheck.repository;

/**
 * 회원이 소유한 데이터를 탈퇴 때 지우는 저장소가 구현한다(FR-AUTH-03). {@code MemberService}는 이 인터페이스 목록만 알고,
 * 새 저장소가 생겨도 탈퇴 코드를 고치지 않는다. 구현체는 스프링 빈이면 자동으로 포함된다.
 *
 * <p>구현하지 않는 것: 결제(payments)와 이용권 원장은 보존 기간 동안 남기고(테이블 정의서), 계약·조항은 원본 파일(S3)을
 * 먼저 지워야 해서 {@code MemberService}가 순서를 정해 직접 지운다.
 * 아직 인메모리인 저장소는 DB로 영속화할 때 같은 인터페이스를 구현한다. 호출은 탈퇴 트랜잭션 안이다.
 */
public interface OwnedDataEraser {

    /** 해당 소유자의 데이터를 모두 지운다. 없으면 아무것도 하지 않는다. 다른 소유자의 데이터는 건드리지 않는다. */
    void deleteAllByOwner(String owner);
}
