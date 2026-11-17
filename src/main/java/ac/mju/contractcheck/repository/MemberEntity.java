package ac.mju.contractcheck.repository;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** V1__init.sql의 {@code users}. 저장소 안에서만 쓰고 밖으로는 domain.Member로 바꿔 내보낸다. */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class MemberEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 255, unique = true)
    private String email;

    @Column(length = 50)
    private String nickname;

    @Column(nullable = false, length = 20)
    private String role;

    @Column(length = 20)
    private String status;

    private Instant createdAt;

    private Instant updatedAt;

    MemberEntity(String email, String nickname, String role, String status, Instant now) {
        this.email = email;
        this.nickname = nickname;
        this.role = role;
        this.status = status;
        this.createdAt = now;
        this.updatedAt = now;
    }

    void changeNickname(String nickname, Instant now) {
        this.nickname = nickname;
        this.updatedAt = now;
    }

    void changeStatus(String status, Instant now) {
        this.status = status;
        this.updatedAt = now;
    }
}
