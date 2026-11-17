package ac.mju.contractcheck.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface OAuthAccountJpaRepository extends JpaRepository<OAuthAccountEntity, Long> {

    Optional<OAuthAccountEntity> findByProviderAndProviderUserId(String provider, String providerUserId);

    Optional<OAuthAccountEntity> findFirstByUserIdOrderByIdAsc(Long userId);
}
