package ac.mju.contractcheck.controller;

import ac.mju.contractcheck.domain.Contract;
import ac.mju.contractcheck.dto.contract.ContractDetail;
import ac.mju.contractcheck.dto.contract.ContractItem;
import ac.mju.contractcheck.dto.contract.ContractPage;
import ac.mju.contractcheck.dto.contract.ContractStatus;
import ac.mju.contractcheck.dto.contract.FileUrl;
import ac.mju.contractcheck.dto.contract.UploadedContract;
import ac.mju.contractcheck.global.security.CurrentUser;
import ac.mju.contractcheck.service.ContractService;

import java.util.List;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * FR-UPL-01 계약서 업로드
 * FR-UPL-02 OCR 변환
 */
@RestController
@RequiredArgsConstructor
public class ContractRestController {

    @NonNull
    private final ContractService contracts;
    @NonNull
    private final CurrentUser currentUser;

    /** FR-UPL-01 계약서 업로드 */
    @PostMapping("/api/v1/contracts")
    public ResponseEntity<UploadedContract> upload(
            Authentication authentication,
            @RequestParam("file") MultipartFile file,
            @RequestParam String title,
            @RequestParam String contractType) {
        Contract contract = contracts.upload(currentUser.username(authentication), file, title, contractType);
        return ResponseEntity.created(java.net.URI.create("/api/v1/contracts/" + contract.id()))
                .body(new UploadedContract(contract.id(), contract.status()));
    }

    /**
     * FR-UPL-01 계약서 업로드
     *
     * TODO(FR-UPL-01): page/size를 서비스 쿼리(OFFSET/LIMIT)와 총건수에 반영한다. 현재는 전체 목록 크기를 total로 넣는다.
     */
    @GetMapping("/api/v1/contracts")
    public ContractPage list(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<ContractItem> items = contracts.list(currentUser.username(authentication)).stream()
                .map(ContractItem::from)
                .toList();
        return new ContractPage(items, page, size, items.size());
    }

    /** FR-UPL-01 계약서 업로드 */
    @GetMapping("/api/v1/contracts/{id}")
    public ContractDetail detail(Authentication authentication, @PathVariable long id) {
        return ContractDetail.from(contracts.get(currentUser.username(authentication), id));
    }

    /**
     * FR-UPL-02 OCR 변환
     *
     * TODO(FR-UPL-02): contracts.status에 따라 실제 진행률을 계산한다. 현재는 UPLOADED이면 10, 그 외 100으로 고정한다. 상세 파이프라인은 ContractService.status.
     * 스펙 미정: progress 산출 방식.
     */
    @GetMapping("/api/v1/contracts/{id}/status")
    public ContractStatus status(Authentication authentication, @PathVariable long id) {
        Contract contract = contracts.status(currentUser.username(authentication), id);
        String errorMessage = contract.errorMessage() == null ? "" : contract.errorMessage();
        return new ContractStatus(contract.status(), "UPLOADED".equals(contract.status()) ? 10 : 100, errorMessage);
    }

    /** FR-UPL-01 계약서 업로드 */
    @GetMapping("/api/v1/contracts/{id}/file-url")
    public FileUrl fileUrl(Authentication authentication, @PathVariable long id) {
        return contracts.fileUrl(currentUser.username(authentication), id);
    }

    /** FR-UPL-01 계약서 업로드 */
    @DeleteMapping("/api/v1/contracts/{id}")
    public ResponseEntity<Void> delete(Authentication authentication, @PathVariable long id) {
        contracts.delete(currentUser.username(authentication), id);
        return ResponseEntity.noContent().build();
    }
}
