package ac.mju.contractcheck.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ac.mju.contractcheck.client.PaymentGatewayException;
import ac.mju.contractcheck.domain.OAuthProfile;
import ac.mju.contractcheck.domain.Payment;
import ac.mju.contractcheck.domain.Product;
import ac.mju.contractcheck.domain.UserId;
import ac.mju.contractcheck.domain.enums.PaymentStatus;
import ac.mju.contractcheck.repository.PaymentRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** 시크릿 키가 없어 토스 클라이언트 빈이 없는 환경(테스트 기본값, 키를 주지 않은 운영)에서도 앱은 뜨고 승인만 502로 거절된다. */
@SpringBootTest
@ActiveProfiles("test")
class PaymentConfirmWithoutGatewayTest {

    @Autowired PaymentService payments;
    @Autowired PaymentRepository paymentRepository;
    @Autowired AuthService auth;

    @Test
    @DisplayName("FR-PAY-02 토스 시크릿 키가 없으면 승인은 502 PAYMENT_GATEWAY_ERROR이고 주문은 READY로 남는다")
    void confirmWithoutGatewayFailsWithoutChangingOrder() {
        String providerUserId = UUID.randomUUID().toString();
        UserId owner = auth.signIn(new OAuthProfile("kakao", providerUserId, providerUserId + "@example.com", "닉네임")).id();
        Payment order = payments.order(owner, paymentRepository.saveProduct(new Product(0, "분석 5회", 5, 4900, true)).id());

        assertThatThrownBy(() -> payments.confirm(owner, "pay_key", order.orderId(), 4900))
                .isInstanceOfSatisfying(PaymentGatewayException.class,
                        exception -> assertThat(exception.code()).isEqualTo("PAYMENT_GATEWAY_ERROR"));

        assertThat(paymentRepository.findOwned(order.id(), owner).orElseThrow().status()).isEqualTo(PaymentStatus.READY);
    }
}
