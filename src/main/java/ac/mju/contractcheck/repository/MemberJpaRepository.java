package ac.mju.contractcheck.repository;

import org.springframework.data.jpa.repository.JpaRepository;

interface MemberJpaRepository extends JpaRepository<MemberEntity, Long> {
}
