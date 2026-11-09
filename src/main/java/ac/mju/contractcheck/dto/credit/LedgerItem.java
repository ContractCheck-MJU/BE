package ac.mju.contractcheck.dto.credit;

import ac.mju.contractcheck.domain.LedgerEntry;

public record LedgerItem(String type, int amount, String refId, String createdAt) {
    public static LedgerItem from(LedgerEntry entry) {
        return new LedgerItem(entry.type(), entry.amount(), entry.refId(), entry.createdAt());
    }
}
