package ac.mju.contractcheck.service;

import ac.mju.contractcheck.domain.CreditAccount;
import ac.mju.contractcheck.domain.CreditUseResult;
import ac.mju.contractcheck.domain.LedgerEntry;
import ac.mju.contractcheck.global.error.CreditInsufficientException;
import ac.mju.contractcheck.repository.CreditRepository;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.util.List;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreditService {

    private static final int MAX_ATTEMPTS = 5;

    @NonNull
    private final CreditRepository credits;


    /**
     * FR-CRD-01 무료 횟수
     * 본인 credit_wallets의 free_remaining, paid_remaining을 읽는다. 지갑은 최초 소셜 가입 트랜잭션에서
     * 무료 2회와 GRANT 원장으로 만들어지며(AuthService), 조회 시 만들어 주지 않는다.
     * 스펙 미정: 지갑이 없는 회원(가입 오류)에 돌려줄 전용 오류 코드. 지금은 서버 오류로 드러낸다.
     */
    public CreditAccount me(String owner) {
        return credits.account(owner);
    }

    /**
     * FR-CRD-02 유료 차감
     * 분석 1건에 대해 이용권 1회를 차감한다(무료 우선 → 유료, USE 원장 기록). 이용권이 없으면 402 CREDIT_INSUFFICIENT이다.
     * 같은 지갑을 동시에 바꿔 낙관적 락이 충돌하면 잔액을 다시 읽어 최대 {@value #MAX_ATTEMPTS}번 시도한다.
     * 트랜잭션은 저장소 호출 단위이며 외부 API는 부르지 않는다.
     *
     * TODO(FR-CRD-02): 분석 실패 시 자동 복구(RESTORE, 같은 analysisId 중복 방지 TC-CRD-008)는 구현하지 않았다.
     * 스펙 미정: credit_ledger에는 무료·유료 구분이 없어 복구분을 어느 쪽에 되돌릴지 정해야 한다.
     * 호출은 FR-ANL-01에서 분석을 접수할 때 한다.
     */
    public void use(String owner, long analysisId) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                if (credits.use(owner, analysisId) == CreditUseResult.INSUFFICIENT) {
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
    public List<LedgerEntry> ledger(String owner) {
        return credits.ledger(owner);
    }
}
