package ac.mju.contractcheck.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ac.mju.contractcheck.domain.OAuthProfile;
import ac.mju.contractcheck.domain.Payment;
import ac.mju.contractcheck.domain.Product;
import ac.mju.contractcheck.domain.UserId;
import ac.mju.contractcheck.domain.enums.PaymentStatus;
import ac.mju.contractcheck.global.error.NotFoundException;
import ac.mju.contractcheck.repository.PaymentRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** FR-PAY-01 상품 목록과 주문 생성을 실제 서비스·저장소(H2)로 확인한다. 토스는 부르지 않는다. */
@SpringBootTest
@ActiveProfiles("test")
class PaymentOrderTest {

    @Autowired PaymentService payments;
    @Autowired PaymentRepository paymentRepository;
    @Autowired AuthService auth;

    private UserId owner;

    @BeforeEach
    void setUp() {
        String providerUserId = UUID.randomUUID().toString();
        owner = auth.signIn(new OAuthProfile("kakao", providerUserId, providerUserId + "@example.com", "닉네임")).id();
    }

    @Test
    @DisplayName("TC-PAY-017 상품 목록에는 판매 중(active) 상품만 나온다")
    void listsOnlyActiveProducts() {
        Product active = paymentRepository.saveProduct(new Product(0, "분석 5회-" + UUID.randomUUID(), 5, 4900, true));
        Product inactive = paymentRepository.saveProduct(new Product(0, "중단 상품-" + UUID.randomUUID(), 1, 100, false));

        assertThat(payments.products()).extracting(Product::id).contains(active.id()).doesNotContain(inactive.id());
    }

    @Test
    @DisplayName("TC-PAY-001 주문 금액은 서버에 저장된 상품 가격이고 READY로 저장되며 orderName은 상품명이다")
    void orderUsesServerPrice() {
        Product product = paymentRepository.saveProduct(new Product(0, "분석 20회", 20, 14900, true));

        Payment order = payments.order(owner, product.id());

        assertThat(order.id()).isPositive();
        assertThat(order.amount()).isEqualTo(14900);
        assertThat(order.orderName()).isEqualTo("분석 20회");
        assertThat(order.status()).isEqualTo(PaymentStatus.READY);
        assertThat(order.orderId()).hasSizeBetween(6, 64).matches("[A-Za-z0-9_=-]+");
        assertThat(order.paymentKey()).isEmpty();
        assertThat(paymentRepository.findByOrderId(order.orderId(), owner)).contains(order);
    }

    @Test
    @DisplayName("TC-PAY-001 주문마다 orderId가 다르다")
    void orderIdsAreUnique() {
        Product product = paymentRepository.saveProduct(new Product(0, "분석 5회", 5, 4900, true));

        assertThat(payments.order(owner, product.id()).orderId()).isNotEqualTo(payments.order(owner, product.id()).orderId());
    }

    @Test
    @DisplayName("TC-PAY-002 없는 상품이나 비활성 상품은 PRODUCT_NOT_FOUND다")
    void unknownOrInactiveProductIsNotFound() {
        Product inactive = paymentRepository.saveProduct(new Product(0, "중단 상품", 1, 100, false));

        assertThatThrownBy(() -> payments.order(owner, 9_999_999L)).isInstanceOf(NotFoundException.class)
                .extracting("code").isEqualTo("PRODUCT_NOT_FOUND");
        assertThatThrownBy(() -> payments.order(owner, inactive.id())).isInstanceOf(NotFoundException.class)
                .extracting("code").isEqualTo("PRODUCT_NOT_FOUND");
    }

    @Test
    @DisplayName("FR-PAY-01 주문은 소유자만 조회된다(타인은 없는 것과 같다)")
    void orderIsVisibleToOwnerOnly() {
        Product product = paymentRepository.saveProduct(new Product(0, "분석 5회", 5, 4900, true));
        Payment order = payments.order(owner, product.id());
        String otherId = UUID.randomUUID().toString();
        UserId other = auth.signIn(new OAuthProfile("kakao", otherId, otherId + "@example.com", "타인")).id();

        assertThat(paymentRepository.findByOrderId(order.orderId(), other)).isEmpty();
        assertThat(paymentRepository.findOwned(order.id(), other)).isEmpty();
    }
}
