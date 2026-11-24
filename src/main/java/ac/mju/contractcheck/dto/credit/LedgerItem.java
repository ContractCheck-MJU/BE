package ac.mju.contractcheck.dto.credit;

import ac.mju.contractcheck.domain.LedgerEntry;

public record LedgerItem(String type, int amount, String refType, String refId, String createdAt) {
    public static LedgerItem from(LedgerEntry entry) {
        return new LedgerItem(
                entry.type().name(), entry.amount(), entry.refType() == null ? "" : entry.refType().name(), entry.refId(), entry.createdAt());
    }
}
