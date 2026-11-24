package ac.mju.contractcheck.dto.credit;

import java.util.List;

public record LedgerPage(List<LedgerItem> items, int page, int size, int total) {}
