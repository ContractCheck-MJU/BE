package ac.mju.contractcheck.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ac.mju.contractcheck.client.PaymentGatewayClient;
import ac.mju.contractcheck.client.PaymentGatewayException;
import ac.mju.contractcheck.client.PaymentGatewayTimeoutException;
import ac.mju.contractcheck.client.PaymentRejectedException;
import ac.mju.contractcheck.domain.GatewayPayment;
import ac.mju.contractcheck.domain.Member;
import ac.mju.contractcheck.domain.OAuthProfile;
import ac.mju.contractcheck.domain.Payment;
import ac.mju.contractcheck.domain.Product;
import ac.mju.contractcheck.repository.PaymentRepository;
import ac.mju.contractcheck.service.AuthService;
import ac.mju.contractcheck.service.PaymentService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** FR-PAY-01·02 결제 API의 상태 코드와 응답 JSON. 토스 호출만 가짜이고 회원·상품·주문은 실제 서비스(H2)로 만든다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentRestControllerTest {

    @Autowired MockMvc mvc;
    @Autowired AuthService auth;
    @Autowired PaymentService payments;
    @Autowired PaymentRepository paymentRepository;
    @MockitoBean PaymentGatewayClient gateway;

    private Member member;
    private Payment order;
    private final String paymentKey = "pay_key_" + UUID.randomUUID();

    @BeforeEach
    void setUp() {
        String providerUserId = UUID.randomUUID().toString();
        member = auth.signIn(new OAuthProfile("kakao", providerUserId, providerUserId + "@example.com", "닉네임"));
        Product product = paymentRepository.saveProduct(new Product(0, "분석 5회", 5, 4900, true));
        order = payments.order(member.id(), product.id());
    }

    private String username() {
        return member.id().principalName();
    }

    private String confirmBody(int amount) {
        return "{\"paymentKey\":\"" + paymentKey + "\",\"orderId\":\"" + order.orderId() + "\",\"amount\":" + amount + "}";
    }

    @Test
    @DisplayName("TC-PAY-017 비회원도 상품 목록을 조회하고 id·name·credits·price만 받는다")
    void productsArePublic() throws Exception {
        mvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id == " + order.productId() + ")].name").value("분석 5회"))
                .andExpect(jsonPath("$.items[?(@.id == " + order.productId() + ")].credits").value(5))
                .andExpect(jsonPath("$.items[?(@.id == " + order.productId() + ")].price").value(4900));
    }

    @Test
    @DisplayName("TC-PAY-001 주문 생성은 201과 서버 금액·orderName·customerKey를 주고 Location은 /payments/orders/{orderId}다")
    void orderReturnsServerAmount() throws Exception {
        mvc.perform(post("/api/v1/payments/orders").with(user(username())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + order.productId() + ",\"amount\":100}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(4900))
                .andExpect(jsonPath("$.orderName").value("분석 5회"))
                .andExpect(jsonPath("$.customerKey").value(username()))
                .andExpect(jsonPath("$.orderId").isNotEmpty())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/v1/payments/orders/")));
    }

    @Test
    @DisplayName("TC-PAY-002 없는 상품은 404 PRODUCT_NOT_FOUND다")
    void unknownProductIsNotFound() throws Exception {
        mvc.perform(post("/api/v1/payments/orders").with(user(username())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"productId\":9999999}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    @DisplayName("FR-PAY-01 로그인하지 않은 주문·승인은 401이다")
    void orderAndConfirmRequireLogin() throws Exception {
        mvc.perform(post("/api/v1/payments/orders").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"productId\":1}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/payments/confirm").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(confirmBody(4900)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("TC-PAY-003 승인 성공은 200과 paymentId·status=DONE·creditsAdded를 준다")
    void confirmSucceeds() throws Exception {
        when(gateway.confirm(paymentKey, order.orderId(), 4900)).thenReturn(new GatewayPayment(
                paymentKey, order.orderId(), "DONE", "카드", 4900, Instant.parse("2026-11-25T01:00:05Z"), "{\"status\":\"DONE\"}"));

        mvc.perform(post("/api/v1/payments/confirm").with(user(username())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(confirmBody(4900)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value(order.id()))
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.creditsAdded").value(5));
    }

    @Test
    @DisplayName("TC-PAY-004 금액 불일치는 400 AMOUNT_MISMATCH이고 토스를 부르지 않는다")
    void amountMismatchIs400() throws Exception {
        mvc.perform(post("/api/v1/payments/confirm").with(user(username())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(confirmBody(100)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AMOUNT_MISMATCH"));

        verify(gateway, never()).confirm(anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("TC-PAY-005 타인 주문 승인은 404 ORDER_NOT_FOUND다")
    void otherUsersOrderIs404() throws Exception {
        String otherId = UUID.randomUUID().toString();
        String other = auth.signIn(new OAuthProfile("kakao", otherId, otherId + "@example.com", "타인")).id().principalName();

        mvc.perform(post("/api/v1/payments/confirm").with(user(other)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(confirmBody(4900)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }

    @Test
    @DisplayName("TC-PAY-006 같은 주문을 다시 승인하면 409 ALREADY_CONFIRMED다")
    void secondConfirmIs409() throws Exception {
        when(gateway.confirm(any(), any(), anyInt())).thenReturn(new GatewayPayment(
                paymentKey, order.orderId(), "DONE", "카드", 4900, Instant.now(), "{}"));
        mvc.perform(post("/api/v1/payments/confirm").with(user(username())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(confirmBody(4900))).andExpect(status().isOk());

        mvc.perform(post("/api/v1/payments/confirm").with(user(username())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(confirmBody(4900)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_CONFIRMED"));
    }

    @Test
    @DisplayName("FR-PAY-02 토스 거절은 422 PAYMENT_REJECTED이고 토스 오류 코드를 providerCode로 내린다")
    void rejectionIs422WithProviderCode() throws Exception {
        when(gateway.confirm(any(), any(), anyInt())).thenThrow(new PaymentRejectedException("REJECT_CARD_COMPANY", "거절"));

        mvc.perform(post("/api/v1/payments/confirm").with(user(username())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(confirmBody(4900)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("PAYMENT_REJECTED"))
                .andExpect(jsonPath("$.providerCode").value("REJECT_CARD_COMPANY"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    @DisplayName("FR-PAY-02 토스 장애는 502 PAYMENT_GATEWAY_ERROR, 시간 초과는 504 PAYMENT_GATEWAY_TIMEOUT이다")
    void gatewayFailuresAre502And504() throws Exception {
        when(gateway.confirm(any(), any(), anyInt()))
                .thenThrow(new PaymentGatewayException("연결 실패"))
                .thenThrow(new PaymentGatewayTimeoutException("시간 초과", null));

        mvc.perform(post("/api/v1/payments/confirm").with(user(username())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(confirmBody(4900)))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("PAYMENT_GATEWAY_ERROR"));
        mvc.perform(post("/api/v1/payments/confirm").with(user(username())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(confirmBody(4900)))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.code").value("PAYMENT_GATEWAY_TIMEOUT"));
    }

    @Test
    @DisplayName("FR-PAY-02 승인 필수 값이 빠지면 400 VALIDATION_ERROR다")
    void missingFieldsIs400() throws Exception {
        mvc.perform(post("/api/v1/payments/confirm").with(user(username())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"orderId\":\"" + order.orderId() + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
