package ac.mju.contractcheck.controller;

import ac.mju.contractcheck.domain.CreditAccount;
import ac.mju.contractcheck.dto.common.PageResponse;
import ac.mju.contractcheck.dto.credit.CreditBalance;
import ac.mju.contractcheck.dto.credit.LedgerItem;
import ac.mju.contractcheck.global.security.CurrentUser;
import ac.mju.contractcheck.service.CreditService;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * FR-CRD-01 무료 횟수
 * FR-CRD-02 유료 차감
 */
@RestController
@RequiredArgsConstructor
public class CreditRestController {
    @NonNull
    private final CreditService credits;
    @NonNull
    private final CurrentUser currentUser;

    /** FR-CRD-01 무료 횟수 */
    @GetMapping("/api/v1/credits/me")
    public CreditBalance me(Authentication authentication) {
        CreditAccount account = credits.me(currentUser.username(authentication));
        return new CreditBalance(account.freeRemaining(), account.paidRemaining());
    }

    /**
     * FR-CRD-02 유료 차감
     *
     * TODO(FR-CRD-02): page(및 size)를 CreditService.ledger의 OFFSET/LIMIT·총건수에 전달한다. 현재는 전체 목록을 가져와 page만 응답에 넣는다.
     */
    @GetMapping("/api/v1/credits/me/ledger")
    public PageResponse<LedgerItem> ledger(Authentication authentication, @RequestParam(defaultValue = "0") int page) {
        return new PageResponse<>(
                credits.ledger(currentUser.username(authentication)).stream().map(LedgerItem::from).toList(),
                page);
    }
}
