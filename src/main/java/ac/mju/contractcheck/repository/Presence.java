package ac.mju.contractcheck.repository;

import java.util.Optional;
import java.util.function.Function;
import lombok.NonNull;

/**
 * 맵에서 꺼낸 값을 Optional로 바꾼다.
 * Optional 자체는 null이 아니다. 없으면 empty, 있으면 Optional.of다.
 */
final class Presence {

    private Presence() {
    }

    static <T> @NonNull Optional<T> present(T found) {
        if (found == null) {
            return Optional.empty();
        }
        return Optional.of(found);
    }

    static <T> @NonNull Optional<T> owned(T found, String owner, Function<T, String> ownerOf) {
        if (found == null) {
            return Optional.empty();
        }
        String foundOwner = ownerOf.apply(found);
        if (foundOwner == null || !foundOwner.equals(owner)) {
            return Optional.empty();
        }
        return Optional.of(found);
    }
}
