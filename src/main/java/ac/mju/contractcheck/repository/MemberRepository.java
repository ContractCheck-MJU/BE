package ac.mju.contractcheck.repository;

import ac.mju.contractcheck.domain.Member;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import lombok.NonNull;
import org.springframework.stereotype.Repository;

/**
 * 인메모리 저장소. 영속화 시 V1__init.sql의 {@code users}, {@code oauth_accounts} 테이블에 매핑한다.
 * 조회 시 ensure()로 회원을 만들지 않고, 가입은 OAuth upsert에서만 수행한다.
 */
@Repository
public class MemberRepository {

    private final AtomicLong sequence = new AtomicLong(1);
    private final ConcurrentHashMap<Long, Member> byId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> byUsername = new ConcurrentHashMap<>();

    public @NonNull Member ensure(String username) {
        return findByUsername(username).orElseGet(() -> {
            long id = sequence.getAndIncrement();
            Member member = new Member(
                    id,
                    username,
                    username + "@local.test",
                    username,
                    "local",
                    "USER",
                    true,
                    Instant.now());
            byId.put(id, member);
            byUsername.put(username, id);
            return member;
        });
    }

    public @NonNull Optional<Member> findByUsername(String username) {
        Long id = byUsername.get(username);
        if (id == null) {
            return Optional.empty();
        }
        return Presence.present(byId.get(id));
    }

    public Member save(Member member) {
        byId.put(member.id(), member);
        byUsername.put(member.username(), member.id());
        return member;
    }

    public void deleteByUsername(String username) {
        Long id = byUsername.remove(username);
        if (id != null) {
            byId.remove(id);
        }
    }
}
