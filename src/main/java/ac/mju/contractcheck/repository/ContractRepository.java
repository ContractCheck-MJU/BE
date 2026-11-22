package ac.mju.contractcheck.repository;

import ac.mju.contractcheck.domain.Contract;
import ac.mju.contractcheck.domain.PageSlice;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

/**
 * V1__init.sql의 {@code contracts}에 저장한다. 소유자 확인은 contracts.user_id로 하며,
 * 다른 회원의 계약은 없는 것과 같게 {@link Optional#empty()}를 돌려준다.
 * errorMessage에 해당하는 컬럼은 스키마에 없어 저장하지 않는다(OCR 실패 표현은 FR-UPL-02에서 정한다).
 */
@Repository
@RequiredArgsConstructor
public class ContractRepository {

    @NonNull
    private final ContractJpaRepository contracts;

    public @NonNull Contract save(@NonNull Contract contract) {
        long userId = OwnerId.parse(contract.owner()).orElseThrow();
        ContractEntity saved = contracts.save(new ContractEntity(
                userId, contract.title(), contract.contractType(), contract.s3Key(), contract.status(),
                contract.createdAt() == null ? Instant.now() : contract.createdAt()));
        return toContract(saved);
    }

    public @NonNull Optional<Contract> findOwned(long id, String owner) {
        return OwnerId.parse(owner)
                .flatMap(userId -> contracts.findByIdAndUserId(id, userId))
                .map(this::toContract);
    }

    public @NonNull PageSlice<Contract> findPage(String owner, int page, int size) {
        return OwnerId.parse(owner)
                .map(userId -> {
                    Page<ContractEntity> found = contracts.findByUserIdOrderByCreatedAtDescIdDesc(
                            userId, PageRequest.of(page, size));
                    List<Contract> items = found.getContent().stream().map(this::toContract).toList();
                    return new PageSlice<>(items, found.getTotalElements());
                })
                .orElseGet(() -> new PageSlice<>(List.of(), 0));
    }

    public void delete(long id) {
        contracts.deleteById(id);
    }

    /** 회원이 가진 모든 계약의 저장소 객체 키. 저장소 키가 없는 계약은 뺀다. */
    public @NonNull List<String> findS3KeysByOwner(String owner) {
        return OwnerId.parse(owner).map(contracts::findS3KeysByUserId).orElseGet(List::of);
    }

    /** 회원이 가진 모든 계약 행을 지운다. 트랜잭션 안에서 부른다. */
    public void deleteAllByOwner(String owner) {
        OwnerId.parse(owner).ifPresent(contracts::deleteByUserId);
    }

    private Contract toContract(ContractEntity entity) {
        return new Contract(
                entity.getId(),
                String.valueOf(entity.getUserId()),
                entity.getTitle(),
                entity.getContractType(),
                entity.getS3Key(),
                entity.getStatus(),
                entity.getMaskedText() == null ? "" : entity.getMaskedText(),
                entity.getCreatedAt(),
                null);
    }
}
