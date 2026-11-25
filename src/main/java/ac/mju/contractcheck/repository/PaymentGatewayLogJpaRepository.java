package ac.mju.contractcheck.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface PaymentGatewayLogJpaRepository extends JpaRepository<PaymentGatewayLogEntity, Long> {

    List<PaymentGatewayLogEntity> findByPaymentIdOrderByIdAsc(Long paymentId);
}
