package ac.mju.contractcheck.dto.contract;

import java.util.List;

public record ContractPage(List<ContractItem> items, int page, int size, int total) {}
