package ac.mju.contractcheck.domain;

import java.util.Optional;

/**
 * 회원 식별자(users.id). 양수만 허용하는 값 객체라 잘못된 값으로는 만들어지지 않는다(정합성).
 * 소유자 확인에 쓰는 값이 "사용자 이름"인지 "users.id"인지 헷갈리던 문자열 {@code owner}를 대신한다.
 * 인증 경계({@code CurrentUser})에서 한 번 만들고 서비스·저장소는 이 타입만 받는다.
 */
public record UserId(long value) {

    public UserId {
        if (value <= 0) {
            throw new IllegalArgumentException("회원 ID는 양수여야 합니다: " + value);
        }
    }

    /**
     * 인증 주체 이름(users.id 문자열, spring_session.principal_name과 같은 값)에서 만든다.
     * 숫자가 아니거나 양수가 아니거나 범위를 넘으면 없는 회원으로 보고 비어 있는 값을 돌려준다(예외로 500이 나가게 두지 않는다).
     */
    public static Optional<UserId> parse(String principalName) {
        if (principalName == null || principalName.isBlank()) {
            return Optional.empty();
        }
        try {
            long value = Long.parseLong(principalName.trim());
            return value > 0 ? Optional.of(new UserId(value)) : Optional.empty();
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    /** spring_session.principal_name에 저장되는 값(users.id 문자열). */
    public String principalName() {
        return String.valueOf(value);
    }

    @Override
    public String toString() {
        return principalName();
    }
}
