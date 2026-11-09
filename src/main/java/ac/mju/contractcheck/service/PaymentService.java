package ac.mju.contractcheck.service;

import ac.mju.contractcheck.global.error.NotFoundException;
import ac.mju.contractcheck.domain.Payment;
import ac.mju.contractcheck.domain.Product;
import ac.mju.contractcheck.repository.PaymentRepository;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {
    @NonNull
    private final PaymentRepository payments;


    /**
     * FR-PAY-01 결제 요청
     *
     * TODO(FR-PAY-01): 판매 중인 이용권 상품을 조회한다. 인증 불필요.
     * 1. 입력 검증: 없음.
     * 2. 권한/소유자 확인: 공개 API.
     * 3. DB/외부 호출: products에서 active=true인 행을 읽는다(name, credits, price).
     * 4. 상태 전이: 없음.
     * 5. 예외/에러코드: 없음(빈 목록 가능).
     * 6. 반환: 상품 목록.
     */
    public List<Product> products() {
        return payments.products();
    }

    /**
     * FR-PAY-01 결제 요청
     *
     * TODO(FR-PAY-01): 토스 결제 주문을 생성한다.
     * 1. 입력 검증: productId 필수. 비활성이거나 없으면 PRODUCT_NOT_FOUND(404).
     * 2. 권한/소유자 확인: 로그인 사용자.
     * 3. DB/외부 호출: payments에 order_id(고유), user_id, product_id, amount=products.price, status=READY를 insert한다.
     * 4. 상태 전이: READY 생성.
     * 5. 예외/에러코드: PRODUCT_NOT_FOUND(404).
     * 6. 반환: orderId, amount, orderName(상품명). 스펙 미정: payments.status 허용 값과 토스 상태 매핑(READY/DONE/CANCELED 등).
     */
    public Payment order(String owner, long productId) {
        Product product = payments.findProduct(productId)
                .orElseThrow(() -> new NotFoundException("PRODUCT_NOT_FOUND", "이용권 상품을 찾을 수 없습니다."));
        Payment saved = payments.save(new Payment(0, owner, UUID.randomUUID().toString(), product.price(), product.name(), "READY", product.credits()));
        log.info("주문 생성 orderId={} owner={} amount={}", saved.orderId(), owner, saved.amount());
        return saved;
    }

    /**
     * FR-PAY-02 결제 승인
     *
     * TODO(FR-PAY-02): 토스 결제를 승인하고 유료 횟수를 지급한다.
     * 1. 입력 검증: paymentKey, orderId, amount가 payments 행과 일치해야 한다. 아니면 AMOUNT_MISMATCH 등 시트 코드.
     * 2. 권한/소유자 확인: payments.user_id. 아니면 PAYMENT_NOT_FOUND(404). 이미 DONE이면 멱등 성공(재지급 금지).
     * 3. DB/외부 호출: 토스 POST /v1/payments/confirm. 성공 시 payment_key, status=DONE, method, approved_at, raw를 저장한다.
     *    같은 트랜잭션에서 credit_wallets.paid_remaining에 products.credits를 더하고 version 락,
     *    credit_ledger type=GRANT, ref_type=PAYMENT, ref_id=payments.id를 남긴다.
     * 4. 상태 전이: READY → DONE. 승인 실패 시 상태를 유지하거나 실패 값으로 둔다.
     * 5. 예외/에러코드: AMOUNT_MISMATCH, PAYMENT_NOT_FOUND, 토스 거절 시 시트 코드(TC-PAY).
     * 6. 반환: 결제 id, status, 지급 횟수.
     */
    public Payment confirm(String owner, String paymentKey, String orderId, int amount) {
        Payment payment = payments.findByOrderId(orderId)
                .filter(item -> item.owner().equals(owner))
                .orElseThrow(() -> new NotFoundException("PAYMENT_NOT_FOUND", "주문을 찾을 수 없습니다."));
        Payment saved = payments.save(new Payment(payment.id(), owner, orderId, amount, payment.orderName(), "DONE", payment.creditsAdded()));
        log.info("결제 승인 orderId={} owner={} amount={}", orderId, owner, amount);
        return saved;
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
        payments.findByOrderId(paymentKey).ifPresentOrElse(payment -> {
            payments.save(new Payment(payment.id(), payment.owner(), payment.orderId(), payment.amount(), payment.orderName(), status, payment.creditsAdded()));
            log.info("결제 웹훅 반영 paymentKey={} status={}", paymentKey, status);
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
    public List<Payment> list(String owner) {
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
    public Payment cancel(String owner, long id) {
        Payment payment = payments.findOwned(id, owner)
                .orElseThrow(() -> new NotFoundException("PAYMENT_NOT_FOUND", "결제를 찾을 수 없습니다."));
        Payment saved = payments.save(new Payment(payment.id(), owner, payment.orderId(), payment.amount(), payment.orderName(), "CANCELED", payment.creditsAdded()));
        log.info("결제 취소 paymentId={} owner={}", saved.id(), owner);
        return saved;
    }
}
