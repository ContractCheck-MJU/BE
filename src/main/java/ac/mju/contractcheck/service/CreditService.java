package ac.mju.contractcheck.service;

import ac.mju.contractcheck.domain.CreditAccount;
import ac.mju.contractcheck.domain.CreditDeduction;
import ac.mju.contractcheck.domain.CreditPolicy;
import ac.mju.contractcheck.domain.CreditSource;
import ac.mju.contractcheck.domain.CreditUseResult;
import ac.mju.contractcheck.domain.LedgerEntry;
import ac.mju.contractcheck.domain.LedgerRefType;
import ac.mju.contractcheck.domain.LedgerType;
import ac.mju.contractcheck.domain.UserId;
import ac.mju.contractcheck.global.error.CreditInsufficientException;
import ac.mju.contractcheck.repository.CreditRepository;
import java.util.Optional;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.util.List;
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
     * 호출은 FR-ANL-01에서 분석을 접수할 때 한다.
     */
    public void use(UserId owner, long analysisId) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                CreditUseResult result = transaction.execute(status -> useOnce(owner, analysisId));
                if (result == CreditUseResult.INSUFFICIENT) {
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

    /** 지갑을 읽어 도메인 규칙(무료 우선 → 유료)으로 차감하고, 지갑 저장과 USE 원장 기록을 이어서 한다. 호출하는 쪽이 연 트랜잭션 안에서 실행한다. */
    private CreditUseResult useOnce(UserId owner, long analysisId) {
        Optional<CreditDeduction> deduction = me(owner).deductOne();
        if (deduction.isEmpty()) {
            return CreditUseResult.INSUFFICIENT;
        }
        credits.saveAccount(deduction.get().account());
        credits.appendLedger(owner, LedgerType.USE, 1, LedgerRefType.ANALYSIS, analysisId, deduction.get().source());
        log.info("이용권 차감 userId={} analysisId={} source={}", owner, analysisId, deduction.get().source());
        return CreditUseResult.USED;
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
     *
     * TODO(FR-CRD-02): 이용권 원장 목록을 페이지로 조회한다. 실제 차감(USE)·복구(RESTORE)·지급(GRANT)·환불(REFUND)은
     * 분석 시작·실패, 가입, 결제 승인/취소에서 수행하고, 이 API는 조회만 한다.
     * 1. 입력 검증: page/size를 서비스로 전달한다.
     * 2. 권한/소유자 확인: credit_ledger.user_id = 현재 사용자.
     * 3. DB/외부 호출: created_at 내림차순, OFFSET/LIMIT. type, amount, ref_type, ref_id를 포함한다.
     * 4. 상태 전이: 없음.
     * 5. 예외/에러코드: 없음(빈 목록 허용).
     * 6. 반환: 원장 페이지.
     */
    public List<LedgerEntry> ledger(UserId owner) {
        return credits.ledger(owner);
    }
}
