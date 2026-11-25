package ac.mju.contractcheck.repository;

import ac.mju.contractcheck.domain.Payment;
import ac.mju.contractcheck.domain.PaymentGatewayLog;
import ac.mju.contractcheck.domain.Product;
import ac.mju.contractcheck.domain.UserId;
import ac.mju.contractcheck.domain.enums.GatewayAction;
import ac.mju.contractcheck.domain.enums.GatewayOutcome;
import ac.mju.contractcheck.domain.enums.PaymentStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * V1__init.sql의 {@code products}, {@code payments}를 읽고 쓴다. 도메인 값을 엔티티로 바꿔 저장하는 일만 하고,
 * 어떤 상태에서 어떤 상태로 옮길지와 트랜잭션 경계는 호출하는 service가 정한다.
 * 소유자는 payments.user_id로 확인하며 다른 회원의 결제는 없는 것과 같게 {@link Optional#empty()}를 돌려준다.
 * 주문 이름과 지급 횟수는 payments에 없고 상품(products)에서 읽는다.
 */
@Repository
@RequiredArgsConstructor
public class PaymentRepository {

    @NonNull
    private final ProductJpaRepository products;
    @NonNull
    private final PaymentJpaRepository payments;
    @NonNull
    private final PaymentGatewayLogJpaRepository gatewayLogs;

    public @NonNull List<Product> findActiveProducts() {
        return products.findByActiveTrueOrderByIdAsc().stream().map(PaymentRepository::toProduct).toList();
    }

    public @NonNull Optional<Product> findActiveProduct(long id) {
        return products.findByIdAndActiveTrue(id).map(PaymentRepository::toProduct);
    }

    public @NonNull Product saveProduct(@NonNull Product product) {
        return toProduct(products.save(new ProductEntity(
                product.id() == 0 ? null : product.id(), product.name(), product.credits(), product.price(), product.active())));
    }

    /** id가 0이면 새 행을 만들고, 아니면 그 행을 도메인 값으로 갱신한다. 이미 저장된 토스 응답 원문(raw)은 그대로 둔다. */
    public @NonNull Payment save(@NonNull Payment payment) {
        String raw = payment.id() == 0 ? null : payments.findById(payment.id()).map(PaymentEntity::getRaw).orElse(null);
        return saveWithRaw(payment, raw);
    }

    /** 토스 승인 응답 원문(raw)과 함께 저장한다. */
    public @NonNull Payment save(@NonNull Payment payment, String raw) {
        return saveWithRaw(payment, raw);
    }

    /**
     * 결제 대행사 호출 결과를 한 행 쌓는다. 호출하는 service의 트랜잭션 안에서 부르며, 상태 전이와 함께 성공·실패한다.
     *
     * @param httpStatus 응답을 받지 못했으면 null
     * @param response 응답 원문(JSON). 없으면 null
     */
    public void appendGatewayLog(
            long paymentId, @NonNull GatewayAction action, @NonNull GatewayOutcome outcome, Integer httpStatus, String response) {
        gatewayLogs.save(new PaymentGatewayLogEntity(paymentId, action.name(), outcome.name(), httpStatus, response, Instant.now()));
    }

    /** 결제 한 건의 대행사 호출 이력을 오래된 순으로 돌려준다. */
    public @NonNull List<PaymentGatewayLog> findGatewayLogs(long paymentId) {
        return gatewayLogs.findByPaymentIdOrderByIdAsc(paymentId).stream()
                .map(entity -> new PaymentGatewayLog(entity.getId(), entity.getPaymentId(), GatewayAction.valueOf(entity.getAction()),
                        GatewayOutcome.valueOf(entity.getOutcome()), entity.getHttpStatus(), entity.getResponse(), entity.getCreatedAt()))
                .toList();
    }

    public @NonNull Optional<Payment> findByOrderId(String orderId, UserId owner) {
        return payments.findByOrderIdAndUserId(orderId, owner.value()).map(this::toPayment);
    }

    public @NonNull Optional<Payment> findOwned(long id, UserId owner) {
        return payments.findByIdAndUserId(id, owner.value()).map(this::toPayment);
    }

    public @NonNull Optional<Payment> findByPaymentKey(String paymentKey) {
        return payments.findByPaymentKey(paymentKey).map(this::toPayment);
    }

    public @NonNull List<Payment> findByOwner(UserId owner) {
        return payments.findByUserIdOrderByIdDesc(owner.value()).stream().map(this::toPayment).toList();
    }

    /**
     * 결제가 {@code from} 상태일 때만 {@code to}로 바꾼다.
     *
     * @return 바꿨으면 true. 이미 다른 요청이 상태를 바꿔 조건에 맞지 않으면 false
     */
    public boolean transition(long id, @NonNull PaymentStatus from, @NonNull PaymentStatus to) {
        return payments.transition(id, from.name(), to.name()) == 1;
    }

    private Payment saveWithRaw(Payment payment, String raw) {
        PaymentEntity saved = payments.save(new PaymentEntity(
                payment.id() == 0 ? null : payment.id(),
                payment.owner().value(),
                payment.productId(),
                payment.orderId(),
                payment.paymentKey().isEmpty() ? null : payment.paymentKey(),
                payment.amount(),
                payment.status().name(),
                payment.method().isEmpty() ? null : payment.method(),
                payment.approvedAt(),
                raw));
        return toPayment(saved);
    }

    private Payment toPayment(PaymentEntity entity) {
        Product product = products.findById(entity.getProductId()).map(PaymentRepository::toProduct).orElse(null);
        return new Payment(
                entity.getId(),
                new UserId(entity.getUserId()),
                entity.getProductId(),
                entity.getOrderId(),
                product == null ? "" : product.name(),
                entity.getAmount(),
                product == null ? 0 : product.credits(),
                PaymentStatus.valueOf(entity.getStatus()),
                entity.getPaymentKey() == null ? "" : entity.getPaymentKey(),
                entity.getMethod() == null ? "" : entity.getMethod(),
                entity.getApprovedAt());
    }

    private static Product toProduct(ProductEntity entity) {
        return new Product(entity.getId(), entity.getName(), entity.getCredits(), entity.getPrice(), entity.isActive());
    }
}
