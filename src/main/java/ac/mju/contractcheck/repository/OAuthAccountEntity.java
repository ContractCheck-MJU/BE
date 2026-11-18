package ac.mju.contractcheck.repository;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** V1__init.sql의 {@code oauth_accounts}. 회원과는 user_id 값으로만 잇고 연관 매핑은 두지 않는다. */
@Entity
@Table(name = "oauth_accounts", uniqueConstraints = @UniqueConstraint(columnNames = {"provider", "provider_user_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class OAuthAccountEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(length = 20)
    private String provider;

    @Column(length = 100)
    private String providerUserId;

    private Instant createdAt;

    OAuthAccountEntity(Long userId, String provider, String providerUserId, Instant now) {
        this.userId = userId;
        this.provider = provider;
        this.providerUserId = providerUserId;
        this.createdAt = now;
    }
}
