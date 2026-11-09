package ac.mju.contractcheck.dto.contract;

import ac.mju.contractcheck.domain.Contract;

public record ContractItem(long contractId, String title, String status, String createdAt) {
    public static ContractItem from(Contract contract) {
        return new ContractItem(contract.id(), contract.title(), contract.status(), contract.createdAt().toString());
    }
}
