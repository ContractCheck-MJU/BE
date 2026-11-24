package ac.mju.contractcheck.controller;

import ac.mju.contractcheck.domain.CreditAccount;
import ac.mju.contractcheck.domain.LedgerEntry;
import ac.mju.contractcheck.domain.PageSlice;
import ac.mju.contractcheck.dto.credit.CreditBalance;
import ac.mju.contractcheck.dto.credit.LedgerItem;
import ac.mju.contractcheck.dto.credit.LedgerPage;
import ac.mju.contractcheck.global.security.CurrentUser;
import ac.mju.contractcheck.service.CreditService;

import java.util.List;
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
        CreditAccount account = credits.me(currentUser.userId(authentication));
        return new CreditBalance(account.freeRemaining(), account.paidRemaining());
    }

    /** FR-CRD-02 유료 차감 */
    @GetMapping("/api/v1/credits/me/ledger")
    public LedgerPage ledger(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageSlice<LedgerEntry> found = credits.ledger(currentUser.userId(authentication), page, size);
        List<LedgerItem> items = found.items().stream().map(LedgerItem::from).toList();
        return new LedgerPage(items, page, size, (int) found.total());
    }
}
