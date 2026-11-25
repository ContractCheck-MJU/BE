package ac.mju.contractcheck.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface PaymentJpaRepository extends JpaRepository<PaymentEntity, Long> {

    Optional<PaymentEntity> findByOrderIdAndUserId(String orderId, Long userId);

    Optional<PaymentEntity> findByIdAndUserId(Long id, Long userId);

    Optional<PaymentEntity> findByPaymentKey(String paymentKey);

    List<PaymentEntity> findByUserIdOrderByIdDesc(Long userId);

    /** 현재 상태가 {@code from}일 때만 {@code to}로 바꾼다. 바뀐 행 수(0 또는 1)로 동시 요청 중 누가 먼저인지 가린다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update PaymentEntity p set p.status = :to where p.id = :id and p.status = :from")
    int transition(@Param("id") Long id, @Param("from") String from, @Param("to") String to);
}
