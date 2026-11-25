package ac.mju.contractcheck.service;

import ac.mju.contractcheck.domain.CreditAccount;
import ac.mju.contractcheck.domain.CreditDeduction;
import ac.mju.contractcheck.domain.CreditPolicy;
import ac.mju.contractcheck.domain.enums.CreditSource;
import ac.mju.contractcheck.domain.LedgerEntry;
import ac.mju.contractcheck.domain.enums.LedgerRefType;
import ac.mju.contractcheck.domain.enums.LedgerType;
import ac.mju.contractcheck.domain.PageSlice;
import ac.mju.contractcheck.domain.UserId;
import ac.mju.contractcheck.global.error.ApiException;
import ac.mju.contractcheck.global.error.CreditInsufficientException;
import ac.mju.contractcheck.repository.CreditRepository;
import java.util.Optional;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreditService {

    private static final int MAX_ATTEMPTS = 5;

    @NonNull
    private final CreditRepository credits;
    @NonNull
    private final TransactionTemplate transaction;


    /**
     * FR-CRD-01 무료 횟수
     * 본인 credit_wallets의 free_remaining, paid_remaining을 읽는다. 지갑은 최초 소셜 가입 트랜잭션에서
     * 무료 2회와 GRANT 원장으로 만들어지며(AuthService), 조회 시 만들어 주지 않는다.
     * 스펙 미정: 지갑이 없는 회원(가입 오류)에 돌려줄 전용 오류 코드. 지금은 서버 오류로 드러낸다.
     */
    public CreditAccount me(UserId owner) {
        return credits.findAccount(owner).orElseThrow(() -> new IllegalStateException("이용권 지갑이 없는 회원이다"));
    }

    /**
     * FR-CRD-01 무료 횟수
     * 가입한 회원에게 정책(무료 {@value ac.mju.contractcheck.domain.CreditPolicy#SIGNUP_FREE_CREDITS}회)대로 지갑을 만들고 GRANT 원장 1건을 남긴다.
     * 가입 트랜잭션(AuthService.signIn) 안에서 호출하며 여기서 트랜잭션을 열지 않는다(TC-CRD-001).
     */
    public void grantSignup(UserId owner) {
        credits.createAccount(new CreditAccount(owner, CreditPolicy.SIGNUP_FREE_CREDITS, 0));
        credits.appendLedger(owner, LedgerType.GRANT, CreditPolicy.SIGNUP_FREE_CREDITS, null, null, CreditSource.FREE);
        log.info("무료 이용권 지급 userId={} amount={}", owner, CreditPolicy.SIGNUP_FREE_CREDITS);
    }

    /**
     * FR-CRD-02 유료 차감
     * 분석 1건에 대해 이용권 1회를 차감한다(무료 우선 → 유료, USE 원장에 차감 출처 FREE/PAID 기록).
     * 이용권이 없으면 402 CREDIT_INSUFFICIENT이다.
     * 같은 지갑을 동시에 바꿔 낙관적 락이 충돌하면 잔액을 다시 읽어 최대 {@value #MAX_ATTEMPTS}번 시도한다.
     * 시도마다 새 트랜잭션을 열고(지갑 갱신과 USE 원장 기록을 함께 성공·실패시킨다) 재시도 루프는 트랜잭션 밖에 둔다.
     * 충돌 예외가 트랜잭션 밖으로 나가야 다음 시도가 깨끗한 상태로 지갑을 다시 읽는다. 외부 API는 부르지 않는다.
     *
     * 분석 실패 때의 복구는 {@link #restore}가 한다.
     */
    public void use(UserId owner, long analysisId) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                UseResult result = transaction.execute(status -> useOnce(owner, analysisId));
                if (result == UseResult.INSUFFICIENT) {
                    throw new CreditInsufficientException();
                }
                return;
            } catch (ObjectOptimisticLockingFailureException exception) {
                log.debug("이용권 차감 충돌 재시도 attempt={}", attempt);
            }
        }
        throw new IllegalStateException("이용권 차감 동시성 충돌이 계속된다");
    }

    /**
     * FR-PAY-02 결제 승인
     * 결제 한 건({@code paymentId})으로 산 유료 이용권을 지갑에 더하고 GRANT 원장(ref_type=PAYMENT, 출처 PAID)을 남긴다.
     * 같은 결제로 이미 지급했으면 아무것도 하지 않는다(멱등). 호출하는 쪽이 연 트랜잭션 안에서 실행하며, 낙관적 락 충돌은
     * 그대로 던진다. 트랜잭션 전체를 다시 시도하는 것은 호출하는 쪽의 몫이다(결제 저장과 지급을 함께 성공·실패시키려고).
     */
    public void grantPaidOnce(UserId owner, long paymentId, int count) {
        if (credits.hasLedger(owner, LedgerType.GRANT, LedgerRefType.PAYMENT, paymentId)) {
            return;
        }
        credits.saveAccount(me(owner).grantPaid(count));
        credits.appendLedger(owner, LedgerType.GRANT, count, LedgerRefType.PAYMENT, paymentId, CreditSource.PAID);
        log.info("유료 이용권 지급 userId={} paymentId={} count={}", owner, paymentId, count);
    }

    /**
     * FR-CRD-02 유료 차감
     * 분석 실패 시 해당 분석({@code analysisId})에 차감했던 이용권 1회를 스택(LIFO) 역순 정책에 따라 원래 차감 출처(FREE/PAID)로 복구한다.
     * 이미 같은 analysisId로 RESTORE가 기록돼 있거나 차감(USE) 이력이 없으면 아무것도 하지 않는다(멱등, TC-CRD-008).
     * 같은 지갑에 낙관적 락 충돌이 나면 새 트랜잭션에서 다시 확인해 최대 {@value #MAX_ATTEMPTS}번 시도한다.
     */
    public void restore(UserId owner, long analysisId) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                transaction.executeWithoutResult(status -> restoreOnce(owner, analysisId));
                return;
            } catch (ObjectOptimisticLockingFailureException exception) {
                log.debug("이용권 복구 충돌 재시도 attempt={}", attempt);
            }
        }
        throw new IllegalStateException("이용권 복구 동시성 충돌이 계속된다");
    }

    /** 이용권 1회 차감 시도의 결과. 이 서비스 안에서만 쓴다. */
    private enum UseResult {
        USED,
        INSUFFICIENT
    }

    /** 지갑을 읽어 도메인 규칙(무료 우선 → 유료)으로 차감하고, 지갑 저장과 USE 원장 기록을 이어서 한다. 호출하는 쪽이 연 트랜잭션 안에서 실행한다. */
    private UseResult useOnce(UserId owner, long analysisId) {
        Optional<CreditDeduction> deduction = me(owner).deductOne();
        if (deduction.isEmpty()) {
            return UseResult.INSUFFICIENT;
        }
        credits.saveAccount(deduction.get().account());
        credits.appendLedger(owner, LedgerType.USE, 1, LedgerRefType.ANALYSIS, analysisId, deduction.get().source());
        log.info("이용권 차감 userId={} analysisId={} source={}", owner, analysisId, deduction.get().source());
        return UseResult.USED;
    }

    /**
     * 지갑(version 포함)을 먼저 읽은 뒤 중복 복구·차감 이력을 확인한다.
     * 다른 트랜잭션이 먼저 복구해 커밋했다면 지갑을 먼저 읽었을 때 version 충돌로 롤백·재시도되고,
     * 커밋 뒤에 읽었다면 이어지는 hasLedger(RESTORE)에서 즉시 걸러져 동시 중복 복구가 일어나지 않는다.
     */
    private void restoreOnce(UserId owner, long analysisId) {
        CreditAccount current = me(owner);
        if (credits.hasLedger(owner, LedgerType.RESTORE, LedgerRefType.ANALYSIS, analysisId)) {
            return;
        }
        if (!credits.hasLedger(owner, LedgerType.USE, LedgerRefType.ANALYSIS, analysisId)) {
            return;
        }
        CreditSource source = credits.findLedgerSource(owner, LedgerType.USE, LedgerRefType.ANALYSIS, analysisId)
                .orElse(CreditSource.PAID);
        credits.saveAccount(current.restoreOne(source));
        credits.appendLedger(owner, LedgerType.RESTORE, 1, LedgerRefType.ANALYSIS, analysisId, source);
        log.info("이용권 복구 userId={} analysisId={} source={}", owner, analysisId, source);
    }

    /**
     * FR-CRD-02 유료 차감
     * 내 이용권 원장을 최신순으로 한 페이지 돌려준다(OFFSET/LIMIT, 전체 건수 포함). 빈 목록은 허용한다.
     * page가 0 미만이거나 size가 1 미만이면 400 VALIDATION_ERROR다.
     */
    public PageSlice<LedgerEntry> ledger(UserId owner, int page, int size) {
        if (page < 0 || size < 1) {
            throw new ApiException(400, "VALIDATION_ERROR", "page는 0 이상, size는 1 이상이어야 합니다.");
        }
        return credits.findLedgerPage(owner, page, size);
    }
}
