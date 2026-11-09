package ac.mju.contractcheck.dto.contract;

import ac.mju.contractcheck.domain.Contract;

public record ContractDetail(long contractId, String title, String contractType, String status, String createdAt) {
    public static ContractDetail from(Contract contract) {
        return new ContractDetail(
                contract.id(), contract.title(), contract.contractType(), contract.status(), contract.createdAt().toString());
    }
}
