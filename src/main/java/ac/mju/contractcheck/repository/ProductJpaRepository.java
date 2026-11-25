package ac.mju.contractcheck.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface ProductJpaRepository extends JpaRepository<ProductEntity, Long> {

    List<ProductEntity> findByActiveTrueOrderByIdAsc();

    Optional<ProductEntity> findByIdAndActiveTrue(Long id);
}
