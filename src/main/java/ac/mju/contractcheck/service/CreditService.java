package ac.mju.contractcheck.service;

import ac.mju.contractcheck.domain.CreditAccount;
import ac.mju.contractcheck.domain.CreditPolicy;
import ac.mju.contractcheck.domain.CreditUseResult;
import ac.mju.contractcheck.domain.LedgerEntry;
import ac.mju.contractcheck.domain.LedgerRefType;
import ac.mju.contractcheck.domain.LedgerType;
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
        credits.appendLedger(owner, LedgerType.GRANT, CreditPolicy.SIGNUP_FREE_CREDITS, null, null);
        log.info("무료 이용권 지급 userId={} amount={}", owner, CreditPolicy.SIGNUP_FREE_CREDITS);
    }

    /**
     * FR-CRD-02 유료 차감
     * 분석 1건에 대해 이용권 1회를 차감한다(무료 우선 → 유료, USE 원장 기록). 이용권이 없으면 402 CREDIT_INSUFFICIENT이다.
     * 같은 지갑을 동시에 바꿔 낙관적 락이 충돌하면 잔액을 다시 읽어 최대 {@value #MAX_ATTEMPTS}번 시도한다.
     * 시도마다 새 트랜잭션을 열고(지갑 갱신과 USE 원장 기록을 함께 성공·실패시킨다) 재시도 루프는 트랜잭션 밖에 둔다.
     * 충돌 예외가 트랜잭션 밖으로 나가야 다음 시도가 깨끗한 상태로 지갑을 다시 읽는다. 외부 API는 부르지 않는다.
     *
     * 분석 실패 때의 복구는 {@link #restore}가 한다.
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
     * 실패한 분석의 차감을 되돌린다. 같은 분석 ID로 여러 번 불러도 RESTORE는 한 번만 기록하고(TC-CRD-008),
     * 차감(USE) 기록이 없으면 아무것도 하지 않는다. 낙관적 락 충돌은 {@link #use}처럼 새 트랜잭션에서 다시 시도한다.
     * 스펙 미정: 원장에 무료·유료 구분이 없어 복구분은 항상 무료 잔여로 되돌린다.
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

    private void restoreOnce(UserId owner, long analysisId) {
        boolean used = credits.hasLedger(owner, LedgerType.USE, LedgerRefType.ANALYSIS, analysisId);
        boolean restored = credits.hasLedger(owner, LedgerType.RESTORE, LedgerRefType.ANALYSIS, analysisId);
        if (!used || restored) {
            return;
        }
        credits.saveAccount(me(owner).restoreOne());
        credits.appendLedger(owner, LedgerType.RESTORE, 1, LedgerRefType.ANALYSIS, analysisId);
        log.info("이용권 복구 userId={} analysisId={}", owner, analysisId);
    }

    /** 지갑을 읽어 도메인 규칙(무료 우선 → 유료)으로 차감하고, 지갑 저장과 USE 원장 기록을 이어서 한다. 호출하는 쪽이 연 트랜잭션 안에서 실행한다. */
    private CreditUseResult useOnce(UserId owner, long analysisId) {
        Optional<CreditAccount> after = me(owner).useOne();
        if (after.isEmpty()) {
            return CreditUseResult.INSUFFICIENT;
        }
        credits.saveAccount(after.get());
        credits.appendLedger(owner, LedgerType.USE, 1, LedgerRefType.ANALYSIS, analysisId);
        log.info("이용권 차감 userId={} analysisId={}", owner, analysisId);
        return CreditUseResult.USED;
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
