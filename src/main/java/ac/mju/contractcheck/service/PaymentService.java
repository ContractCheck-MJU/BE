package ac.mju.contractcheck.service;

import ac.mju.contractcheck.client.PaymentGatewayClient;
import ac.mju.contractcheck.client.PaymentGatewayException;
import ac.mju.contractcheck.client.PaymentRejectedException;
import ac.mju.contractcheck.domain.GatewayPayment;
import ac.mju.contractcheck.domain.UserId;
import ac.mju.contractcheck.global.error.ApiException;
import ac.mju.contractcheck.global.error.ConflictException;
import ac.mju.contractcheck.global.error.NotFoundException;
import ac.mju.contractcheck.domain.Payment;
import ac.mju.contractcheck.domain.Product;
import ac.mju.contractcheck.domain.enums.PaymentStatus;
import ac.mju.contractcheck.repository.PaymentRepository;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final int MAX_ATTEMPTS = 5;

    @NonNull
    private final PaymentRepository payments;
    @NonNull
    private final CreditService credits;
    @NonNull
    private final TransactionTemplate transaction;
    /** 시크릿 키가 없으면 빈이 없다. 그때는 승인만 502로 거절하고 앱은 정상 기동한다. */
    @NonNull
    private final ObjectProvider<PaymentGatewayClient> gateways;


    /**
     * FR-PAY-01 결제 요청
     * 판매 중(active)인 이용권 상품만 id 순으로 돌려준다. 공개 API라 인증이 필요 없다(TC-PAY-017).
     * 스펙 미정: 상품 구성(이름·횟수·가격). V5 마이그레이션의 초기 상품은 임시 값이다.
     */
    public List<Product> products() {
        return payments.findActiveProducts();
    }

    /**
     * FR-PAY-01 결제 요청
     * 상품 가격(서버 저장값)으로 주문을 만들어 READY로 저장한다. 클라이언트가 보낸 금액은 받지 않는다(TC-PAY-001).
     * 없는 상품이나 비활성 상품은 404 PRODUCT_NOT_FOUND다(TC-PAY-002). orderId는 토스 규칙(6~64자, 영문·숫자·-·_·=)에 맞는 UUID다.
     * 스펙 미정: payments.status 허용 값과 토스 상태 매핑({@link PaymentStatus}는 임시 값).
     */
    public Payment order(UserId owner, long productId) {
        Product product = payments.findActiveProduct(productId)
                .orElseThrow(() -> new NotFoundException("PRODUCT_NOT_FOUND", "이용권 상품을 찾을 수 없습니다."));
        Payment saved = payments.save(Payment.ordered(owner, product, UUID.randomUUID().toString()));
        log.info("주문 생성 orderId={} owner={} amount={}", saved.orderId(), owner, saved.amount());
        return saved;
    }

    /**
     * FR-PAY-02 결제 승인
     * 토스 승인 API를 부르기 전에 서버가 확인할 것을 모두 확인한다: 입력 값(400 VALIDATION_ERROR), 내 주문인지(404 ORDER_NOT_FOUND),
     * 금액이 주문 금액과 같은지(400 AMOUNT_MISMATCH, TC-PAY-004), 아직 승인 전인지(409 ALREADY_CONFIRMED, TC-PAY-006).
     * 그다음 주문을 READY에서 IN_PROGRESS로 바꾸는 조건부 갱신으로 동시 승인 중 한 요청만 통과시킨다.
     *
     * <p>토스 호출은 트랜잭션 밖이다. 거절(422)이면 FAILED로, 토스 장애·시간 초과(502·504)이면 READY로 돌려 다시 승인할 수 있게 한다.
     * 승인에 성공하면 한 트랜잭션에서 결제를 DONE으로 저장하고 유료 이용권을 지급한다. 지갑 낙관적 락 충돌은 트랜잭션을 통째로 다시 시도한다.
     *
     * <p>스펙 미정: FAILED·CANCELED 주문을 다시 승인하려 할 때의 오류 코드(지금은 같은 ALREADY_CONFIRMED). 지급 확정에 실패해 IN_PROGRESS로
     * 남은 결제는 웹훅·재조회(FR-PAY-03)로 맞추는데 아직 없다.
     */
    public Payment confirm(UserId owner, String paymentKey, String orderId, int amount) {
        if (paymentKey == null || paymentKey.isBlank() || orderId == null || orderId.isBlank() || amount <= 0) {
            throw new ApiException(400, "VALIDATION_ERROR", "paymentKey, orderId, amount가 필요합니다.");
        }
        Payment payment = payments.findByOrderId(orderId, owner)
                .orElseThrow(() -> new NotFoundException("ORDER_NOT_FOUND", "주문을 찾을 수 없습니다."));
        if (payment.status() == PaymentStatus.DONE) {
            throw new ConflictException("ALREADY_CONFIRMED", "이미 승인된 주문입니다.");
        }
        if (payment.amount() != amount) {
            log.warn("결제 금액 불일치 orderId={} expected={} actual={}", orderId, payment.amount(), amount);
            throw new ApiException(400, "AMOUNT_MISMATCH", "결제 금액이 주문 금액과 다릅니다.");
        }
        PaymentGatewayClient gateway = gateways.getIfAvailable();
        if (gateway == null) {
            throw new PaymentGatewayException("토스 시크릿 키가 설정되지 않아 승인을 할 수 없습니다.");
        }
        if (!transition(payment.id(), PaymentStatus.READY, PaymentStatus.IN_PROGRESS)) {
            throw new ConflictException("ALREADY_CONFIRMED", "이미 처리 중이거나 승인된 주문입니다.");
        }
        GatewayPayment confirmed = callGateway(gateway, payment, paymentKey);
        Payment done = finish(owner, payment, confirmed);
        log.info("결제 승인 orderId={} owner={} amount={}", orderId, owner, amount);
        return done;
    }

    /** 토스 승인을 부르고, 결과에 따라 주문을 FAILED 또는 READY로 돌려놓는다. 성공 응답이 기대와 다르면 지급하지 않는다. */
    private GatewayPayment callGateway(PaymentGatewayClient gateway, Payment payment, String paymentKey) {
        GatewayPayment confirmed;
        try {
            confirmed = gateway.confirm(paymentKey, payment.orderId(), payment.amount());
        } catch (PaymentRejectedException exception) {
            transition(payment.id(), PaymentStatus.IN_PROGRESS, PaymentStatus.FAILED);
            throw exception;
        } catch (RuntimeException exception) {
            transition(payment.id(), PaymentStatus.IN_PROGRESS, PaymentStatus.READY);
            throw exception;
        }
        if (confirmed.totalAmount() != payment.amount() || !payment.orderId().equals(confirmed.orderId())) {
            log.error("토스 승인 응답이 주문과 다릅니다 orderId={} expectedAmount={} actualAmount={}",
                    payment.orderId(), payment.amount(), confirmed.totalAmount());
            transition(payment.id(), PaymentStatus.IN_PROGRESS, PaymentStatus.FAILED);
            throw new PaymentGatewayException("토스 승인 응답의 주문 번호·금액이 주문과 다릅니다.");
        }
        if (!"DONE".equals(confirmed.status())) {
            log.warn("토스 승인 응답 상태가 DONE이 아닙니다 orderId={} status={}", payment.orderId(), confirmed.status());
            transition(payment.id(), PaymentStatus.IN_PROGRESS, PaymentStatus.READY);
            throw new PaymentGatewayException("토스 승인 상태가 DONE이 아닙니다. status=" + confirmed.status());
        }
        return confirmed;
    }

    /** 결제를 DONE으로 저장하고 유료 이용권을 지급한다. 둘은 한 트랜잭션이며, 지갑 락 충돌이면 트랜잭션을 통째로 다시 시도한다. */
    private Payment finish(UserId owner, Payment payment, GatewayPayment confirmed) {
        Payment done = payment.confirmed(
                confirmed.paymentKey(), confirmed.method(), confirmed.approvedAt() == null ? Instant.now() : confirmed.approvedAt());
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return transaction.execute(status -> {
                    Payment saved = payments.save(done, confirmed.raw());
                    credits.grantPaidOnce(owner, saved.id(), saved.creditsAdded());
                    return saved;
                });
            } catch (ObjectOptimisticLockingFailureException exception) {
                log.debug("결제 지급 충돌 재시도 orderId={} attempt={}", payment.orderId(), attempt);
            }
        }
        log.error("토스 승인은 성공했으나 이용권 지급을 확정하지 못했습니다 orderId={} (IN_PROGRESS로 남음)", payment.orderId());
        throw new IllegalStateException("결제 지급 동시성 충돌이 계속된다");
    }

    private boolean transition(long id, PaymentStatus from, PaymentStatus to) {
        return Boolean.TRUE.equals(transaction.execute(status -> payments.transition(id, from, to)));
    }

    /**
     * FR-PAY-03 웹훅 검증
     *
     * TODO(FR-PAY-03): 토스 웹훅을 검증하고 결제 상태를 동기화한다. 이 경로는 인증·CSRF 제외.
     * 1. 입력 검증: 본문의 paymentKey/status, 서명 검증(TC-PAY-008), 허용 IP(TC-PAY-011). 아니면 FORBIDDEN.
     * 2. 권한/소유자 확인: 서버 간 호출. 결제 소유자 세션은 없다.
     * 3. DB/외부 호출: payment_webhook_events(event_type, payment_key, payload, processed, received_at)에 기록하고 멱등 처리한다.
     *    payments는 payment_key로 찾는다(현재 구현은 paymentKey를 order_id로 조회하므로 수정이 필요하다).
     *    토스 GET /v1/payments/{paymentKey}로 상태·금액을 재확인한 뒤 payments.status를 갱신한다.
     *    DONE이면 confirm과 같이 횟수를 지급하되 이중 지급을 막는다. 취소·부분취소는 원장 REFUND.
     * 4. 상태 전이: processed=true, payments.status를 토스 확정 값으로.
     * 5. 예외/에러코드: 서명·IP 실패는 FORBIDDEN, 대상 없음은 로그 후 2xx(재전송 폭풍 방지) 여부는 시트/토스 권고를 따른다.
     * 6. 반환: 200. 스펙 미정: 웹훅 서명 헤더명·검증 알고리즘, 멱등 키(이벤트 ID 컬럼 없음), IP 허용 목록 출처.
     */
    public void webhook(String paymentKey, String status) {
        payments.findByPaymentKey(paymentKey).ifPresentOrElse(payment -> {
            try {
                payments.save(payment.withStatus(PaymentStatus.valueOf(status)));
                log.info("결제 웹훅 반영 paymentKey={} status={}", paymentKey, status);
            } catch (IllegalArgumentException exception) {
                log.warn("결제 웹훅 상태를 알 수 없음 paymentKey={} status={}", paymentKey, status);
            }
        }, () -> log.warn("결제 웹훅 대상 없음 paymentKey={} status={}", paymentKey, status));
    }

    /**
     * FR-PAY-04 결제 내역·취소
     *
     * TODO(FR-PAY-04): 내 결제 내역을 페이지로 조회한다.
     * 1. 입력 검증: page/size를 서비스로 전달한다.
     * 2. 권한/소유자 확인: payments.user_id.
     * 3. DB/외부 호출: approved_at/생성 역순, OFFSET/LIMIT. order_id, amount, status, method를 포함한다.
     * 4. 상태 전이: 없음.
     * 5. 예외/에러코드: 없음(빈 목록 허용).
     * 6. 반환: 결제 페이지.
     */
    public List<Payment> list(UserId owner) {
        return payments.findByOwner(owner);
    }

    /**
     * FR-PAY-04 결제 내역·취소
     *
     * TODO(FR-PAY-04): 결제를 취소하고 유료 횟수를 환수한다.
     * 1. 입력 검증: id, (있으면) 취소 사유. 컨트롤러의 reason을 서비스로 넘겨야 한다.
     * 2. 권한/소유자 확인: payments.user_id. 아니면 PAYMENT_NOT_FOUND(404). 이미 CANCELED면 멱등.
     * 3. DB/외부 호출: 토스 POST /v1/payments/{paymentKey}/cancel. 성공 시 payments.status=CANCELED, raw 갱신.
     *    credit_wallets.paid_remaining에서 해당 지급분(products.credits)을 차감하고 credit_ledger type=REFUND를 남긴다. version 락.
     * 4. 상태 전이: DONE → CANCELED.
     * 5. 예외/에러코드: PAYMENT_NOT_FOUND, 토스 거절, 시트 취소 코드(TC-PAY).
     * 6. 반환: status, reason. 스펙 미정: 지급분을 이미 사용해 paid_remaining이 부족한 경우의 부분 환수·취소 거부,
     *    취소 사유 저장 위치(payments에 reason 컬럼 없음, raw에 남길지).
     */
    public Payment cancel(UserId owner, long id) {
        Payment payment = payments.findOwned(id, owner)
                .orElseThrow(() -> new NotFoundException("PAYMENT_NOT_FOUND", "결제를 찾을 수 없습니다."));
        Payment saved = payments.save(payment.withStatus(PaymentStatus.CANCELED));
        log.info("결제 취소 paymentId={} owner={}", saved.id(), owner);
        return saved;
    }
}
