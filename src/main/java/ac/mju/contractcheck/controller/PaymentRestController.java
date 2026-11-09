package ac.mju.contractcheck.controller;

import ac.mju.contractcheck.domain.Payment;
import ac.mju.contractcheck.dto.common.ItemsResponse;
import ac.mju.contractcheck.dto.common.PageResponse;
import ac.mju.contractcheck.dto.payment.CanceledPayment;
import ac.mju.contractcheck.dto.payment.ConfirmedPayment;
import ac.mju.contractcheck.dto.payment.PaymentCancelRequest;
import ac.mju.contractcheck.dto.payment.PaymentConfirmRequest;
import ac.mju.contractcheck.dto.payment.PaymentItem;
import ac.mju.contractcheck.dto.payment.PaymentOrder;
import ac.mju.contractcheck.dto.payment.PaymentOrderRequest;
import ac.mju.contractcheck.dto.payment.PaymentWebhookRequest;
import ac.mju.contractcheck.dto.payment.ProductItem;
import ac.mju.contractcheck.global.security.CurrentUser;
import ac.mju.contractcheck.service.PaymentService;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * FR-PAY-01 결제 요청
 * FR-PAY-02 결제 승인
 * FR-PAY-03 웹훅 검증
 * FR-PAY-04 결제 내역·취소
 */
@RestController
@RequiredArgsConstructor
public class PaymentRestController {
    @NonNull
    private final PaymentService payments;
    @NonNull
    private final CurrentUser currentUser;

    /** FR-PAY-01 결제 요청 */
    @GetMapping("/api/v1/products")
    public ItemsResponse<ProductItem> products() {
        return new ItemsResponse<>(payments.products().stream().map(ProductItem::from).toList());
    }

    /** FR-PAY-01 결제 요청 */
    @PostMapping("/api/v1/payments/orders")
    public ResponseEntity<PaymentOrder> order(Authentication authentication, @RequestBody PaymentOrderRequest request) {
        Payment payment = payments.order(currentUser.username(authentication), request.productId());
        return ResponseEntity.created(java.net.URI.create("/api/v1/payments/" + payment.id()))
                .body(new PaymentOrder(payment.orderId(), payment.amount(), payment.orderName(), currentUser.username(authentication)));
    }

    /** FR-PAY-02 결제 승인 */
    @PostMapping("/api/v1/payments/confirm")
    public ConfirmedPayment confirm(Authentication authentication, @RequestBody PaymentConfirmRequest request) {
        Payment payment = payments.confirm(
                currentUser.username(authentication), request.paymentKey(), request.orderId(), request.amount());
        return new ConfirmedPayment(payment.id(), payment.status(), payment.creditsAdded());
    }

    /** FR-PAY-03 웹훅 검증 */
    @PostMapping("/api/v1/payments/webhooks/toss")
    public ResponseEntity<Void> webhook(@RequestBody PaymentWebhookRequest request) {
        String paymentKey = request.data() == null ? "" : request.data().paymentKey();
        String status = request.data() == null ? "" : request.data().status();
        payments.webhook(paymentKey, status);
        return ResponseEntity.ok().build();
    }

    /**
     * FR-PAY-04 결제 내역·취소
     *
     * TODO(FR-PAY-04): page/size를 PaymentService.list의 OFFSET/LIMIT·총건수에 전달한다. 현재는 전체 목록을 가져와 page만 응답에 넣는다.
     */
    @GetMapping("/api/v1/payments")
    public PageResponse<PaymentItem> list(Authentication authentication, @RequestParam(defaultValue = "0") int page) {
        return new PageResponse<>(payments.list(currentUser.username(authentication)).stream().map(PaymentItem::from).toList(), page);
    }

    /**
     * FR-PAY-04 결제 내역·취소
     *
     * TODO(FR-PAY-04): request.reason을 PaymentService.cancel에 전달한다. 현재는 응답에만 넣고 서비스·토스 취소 요청에는 쓰지 않는다.
     */
    @PostMapping("/api/v1/payments/{id}/cancel")
    public CanceledPayment cancel(Authentication authentication, @PathVariable long id, @RequestBody PaymentCancelRequest request) {
        Payment payment = payments.cancel(currentUser.username(authentication), id);
        return new CanceledPayment(payment.status(), request.reason() == null ? "" : request.reason());
    }
}
