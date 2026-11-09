package ac.mju.contractcheck.controller;

import ac.mju.contractcheck.domain.Analysis;
import ac.mju.contractcheck.dto.analysis.AcceptedAnalysis;
import ac.mju.contractcheck.dto.analysis.AnalysisResponse;
import ac.mju.contractcheck.global.security.CurrentUser;
import ac.mju.contractcheck.service.AnalysisService;

import java.util.List;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * FR-ANL-01 조항 위험 분류
 * FR-ANL-02 분석 결과 조회
 */
@RestController
@RequiredArgsConstructor
public class AnalysisRestController {
    @NonNull
    private final AnalysisService analyses;
    @NonNull
    private final CurrentUser currentUser;

    /** FR-ANL-01 조항 위험 분류 */
    @PostMapping("/api/v1/contracts/{id}/analyses")
    public ResponseEntity<AcceptedAnalysis> start(Authentication authentication, @PathVariable long id) {
        Analysis analysis = analyses.start(currentUser.username(authentication), id);
        return ResponseEntity.accepted().body(new AcceptedAnalysis(analysis.id(), analysis.status()));
    }

    /**
     * FR-ANL-02 분석 결과 조회
     *
     * TODO(FR-ANL-02): 경로의 id로 risk_results를 조회해 응답에 넣는다. 현재 analyses.results()가 분석 ID를 쓰지 않아 항상 빈 목록이다.
     */
    @GetMapping("/api/v1/analyses/{id}")
    public AnalysisResponse get(Authentication authentication, @PathVariable long id) {
        Analysis analysis = analyses.get(currentUser.username(authentication), id);
        return new AnalysisResponse(analysis.overallScore(), analysis.summary(), analyses.results());
    }
}
