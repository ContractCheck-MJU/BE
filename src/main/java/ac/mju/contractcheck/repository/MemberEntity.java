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

/** V1__init.sql의 {@code users} 매핑. 저장소 안에서만 쓰고 밖으로는 domain.Member로 바꿔 내보낸다. 업무 규칙은 두지 않는다. */
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

    /** 탈퇴한 회원(개인정보 필드가 없는 도메인 객체)을 반영한다. 도메인에 없는 값인 이메일·닉네임 컬럼은 NULL이 된다. */
    void applyWithdrawn(String status, Instant now) {
        this.status = status;
        this.email = null;
        this.nickname = null;
        this.updatedAt = now;
    }
}
