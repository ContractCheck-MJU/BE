package ac.mju.contractcheck.service;

import ac.mju.contractcheck.domain.CreditAccount;
import ac.mju.contractcheck.domain.LedgerEntry;
import ac.mju.contractcheck.repository.CreditRepository;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CreditService {
    @NonNull
    private final CreditRepository credits;


    /**
     * FR-CRD-01 무료 횟수
     *
     * TODO(FR-CRD-01): 잔여 이용권을 조회한다.
     * 1. 입력 검증: 로그인 필수.
     * 2. 권한/소유자 확인: 본인 credit_wallets.user_id만 읽는다.
     * 3. DB/외부 호출: credit_wallets의 free_remaining, paid_remaining, version.
     *    지갑이 없는 사용자는 조회 시 만들어 주지 않는다. 최초 OAuth 가입 트랜잭션에서
     *    free_remaining=2, paid_remaining=0, version=0과 credit_ledger type=GRANT를 넣는다(TC-CRD-001).
     * 4. 상태 전이: 없음.
     * 5. 예외/에러코드: 지갑 부재는 가입 오류로 보고, 스펙 미정: 전용 오류 코드.
     * 6. 반환: freeRemaining, paidRemaining.
     */
    public CreditAccount me(String owner) {
        return credits.account(owner);
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
