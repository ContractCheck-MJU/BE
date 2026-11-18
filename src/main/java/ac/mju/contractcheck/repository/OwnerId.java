package ac.mju.contractcheck.repository;

import java.util.Optional;
import lombok.NonNull;

/**
 * 인증 주체 이름(users.id 문자열, spring_session.principal_name과 같은 값)을 숫자 식별자로 바꾼다.
 * 숫자가 아니면 없는 소유자로 본다. 예외로 500이 나가게 두지 않는다.
 */
final class OwnerId {

    private OwnerId() {
    }

    static @NonNull Optional<Long> parse(String owner) {
        if (owner == null || owner.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(owner.trim()));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }
}
