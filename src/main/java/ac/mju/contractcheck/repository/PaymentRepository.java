package ac.mju.contractcheck.repository;

import ac.mju.contractcheck.domain.Payment;
import ac.mju.contractcheck.domain.Product;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import lombok.NonNull;
import org.springframework.stereotype.Repository;

/**
 * 인메모리 저장소. 영속화 시 V1__init.sql의 {@code products}, {@code payments},
 * {@code payment_webhook_events} 테이블에 매핑한다. 웹훅 조회 키는 payments.payment_key이다.
 */
@Repository
public class PaymentRepository {
    private final AtomicLong sequence = new AtomicLong(1);
    private final ConcurrentHashMap<Long, Payment> payments = new ConcurrentHashMap<>();
    private final List<Product> products = List.of(
            new Product(1, "분석 5회", 5, 4900),
            new Product(2, "분석 20회", 20, 14900));

    public List<Product> products() {
        return products;
    }

    public @NonNull Optional<Product> findProduct(long id) {
        return products.stream().filter(product -> product.id() == id).findFirst();
    }

    public Payment save(Payment payment) {
        Payment stored = payment.id() == 0
                ? new Payment(sequence.getAndIncrement(), payment.owner(), payment.orderId(), payment.amount(), payment.orderName(), payment.status(), payment.creditsAdded())
                : payment;
        payments.put(stored.id(), stored);
        return stored;
    }

    public @NonNull Optional<Payment> findByOrderId(String orderId) {
        return payments.values().stream().filter(payment -> payment.orderId().equals(orderId)).findFirst();
    }

    public @NonNull Optional<Payment> findOwned(long id, String owner) {
        return Presence.owned(payments.get(id), owner, Payment::owner);
    }

    public List<Payment> findByOwner(String owner) {
        return payments.values().stream().filter(payment -> payment.owner().equals(owner)).toList();
    }
}
