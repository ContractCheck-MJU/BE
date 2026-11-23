package ac.mju.contractcheck.domain;

import java.time.Instant;

/**
 * 탈퇴한 회원. 이메일·닉네임·제공자 같은 개인정보를 담는 필드가 없어서, 탈퇴한 뒤에는 개인정보가 남을 수 없다(정합성).
 * 저장소는 이 객체를 users에 반영하면서 개인정보 컬럼을 비운다.
 */
public record WithdrawnMember(UserId id, Instant createdAt) {

    /** users.status 값. 활성 회원 조회에서 이 상태를 없는 회원으로 본다. */
    public static final String STATUS = MemberStatus.WITHDRAWN.name();
}
