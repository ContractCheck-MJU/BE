package ac.mju.contractcheck.controller;

import ac.mju.contractcheck.domain.Comparison;
import ac.mju.contractcheck.dto.comparison.ComparisonResult;
import ac.mju.contractcheck.dto.comparison.CreateComparison;
import ac.mju.contractcheck.dto.comparison.CreatedComparison;
import ac.mju.contractcheck.global.security.CurrentUser;
import ac.mju.contractcheck.service.ComparisonService;

import java.util.List;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * FR-CMP-01 수정본 업로드·비교
 * FR-CMP-02 변경 사항 표시
 */
@RestController
@RequiredArgsConstructor
public class ComparisonRestController {
    @NonNull
    private final ComparisonService comparisons;
    @NonNull
    private final CurrentUser currentUser;

    /** FR-CMP-01 수정본 업로드·비교 */
    @PostMapping("/api/v1/comparisons")
    public ResponseEntity<CreatedComparison> create(Authentication authentication, @RequestBody CreateComparison request) {
        Comparison comparison = comparisons.create(
                currentUser.username(authentication), request.beforeContractId(), request.afterContractId());
        return ResponseEntity.created(java.net.URI.create("/api/v1/comparisons/" + comparison.id()))
                .body(new CreatedComparison(comparison.id()));
    }

    /**
     * FR-CMP-02 변경 사항 표시
     *
     * TODO(FR-CMP-02): 서비스가 돌려주는 comparison_pairs를 응답에 넣는다. 현재는 조회만 하고 빈 목록을 내린다. 상세 로직은 ComparisonService.get.
     */
    @GetMapping("/api/v1/comparisons/{id}")
    public ComparisonResult get(Authentication authentication, @PathVariable long id) {
        comparisons.get(currentUser.username(authentication), id);
        return new ComparisonResult(List.of());
    }
}
