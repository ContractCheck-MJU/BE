package ac.mju.contractcheck.repository;

import ac.mju.contractcheck.domain.Contract;
import ac.mju.contractcheck.domain.PageSlice;
import ac.mju.contractcheck.domain.UserId;
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
 * 이 클래스는 contracts 행만 다룬다. 계약을 지우면 하위 데이터(조항·분석·위험 결과·설명·비교)는 DB가 ON DELETE CASCADE로 함께 지우고
 * 일정은 계약 연결만 끊는다(V2__contract_descendants_on_delete.sql). 코드에서 조항을 먼저 지우는 것은 H2 테스트 스키마가
 * 엔티티에서 만들어져 FK·cascade가 없기 때문이며(운영에서는 중복 삭제가 무해하다), 그 순서는 호출하는 service
 * (ContractService.delete, MemberService.withdraw)가 정한다. 트랜잭션은 열지 않고, 삭제 메서드는 호출하는 service가 연 트랜잭션 안에서 실행한다.
 */
@Repository
@RequiredArgsConstructor
public class ContractRepository {

    @NonNull
    private final ContractJpaRepository contracts;

    public @NonNull Contract save(@NonNull Contract contract) {
        ContractEntity saved = contracts.save(new ContractEntity(
                contract.owner().value(), contract.title(), contract.contractType(), contract.s3Key(), contract.status(),
                contract.createdAt()));
        return toContract(saved);
    }

    public @NonNull Optional<Contract> findOwned(long id, UserId owner) {
        return contracts.findByIdAndUserId(id, owner.value()).map(this::toContract);
    }

    public @NonNull PageSlice<Contract> findPage(UserId owner, int page, int size) {
        Page<ContractEntity> found = contracts.findByUserIdOrderByCreatedAtDescIdDesc(owner.value(), PageRequest.of(page, size));
        List<Contract> items = found.getContent().stream().map(this::toContract).toList();
        return new PageSlice<>(items, found.getTotalElements());
    }

    public void delete(long id) {
        contracts.deleteById(id);
    }

    /** 회원이 가진 모든 계약의 저장소 객체 키. 저장소 키가 없는 계약은 뺀다. */
    public @NonNull List<String> findS3KeysByOwner(UserId owner) {
        return contracts.findS3KeysByUserId(owner.value());
    }

    /** 회원이 가진 모든 계약 행과 그 조항을 지운다. */
    public void deleteAllByOwner(UserId owner) {
        contracts.deleteByUserId(owner.value());
    }

    private Contract toContract(ContractEntity entity) {
        return new Contract(
                entity.getId(),
                new UserId(entity.getUserId()),
                entity.getTitle(),
                entity.getContractType(),
                entity.getS3Key(),
                entity.getStatus(),
                entity.getMaskedText() == null ? "" : entity.getMaskedText(),
                entity.getCreatedAt(),
                null);
    }
}
