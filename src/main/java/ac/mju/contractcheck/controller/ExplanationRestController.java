package ac.mju.contractcheck.controller;

import ac.mju.contractcheck.domain.Explanation;
import ac.mju.contractcheck.dto.explanation.ExplanationResponse;
import ac.mju.contractcheck.dto.explanation.ExplanationToneRequest;
import ac.mju.contractcheck.global.security.CurrentUser;
import ac.mju.contractcheck.service.ExplanationService;

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
 * FR-EXP-01 쉬운말 설명 생성
 * FR-EXP-02 근거 첨부
 * FR-EXP-03 수정 문구 제안
 */
@RestController
@RequiredArgsConstructor
public class ExplanationRestController {
    @NonNull
    private final ExplanationService explanations;
    @NonNull
    private final CurrentUser currentUser;

    /** FR-EXP-01 쉬운말 설명 생성, FR-EXP-03 수정 문구 제안 */
    @PostMapping("/api/v1/clauses/{id}/explanations")
    public ResponseEntity<ExplanationResponse> create(
            Authentication authentication, @PathVariable long id, @RequestBody(required = false) ExplanationToneRequest request) {
        Explanation explanation = explanations.create(
                currentUser.username(authentication), id, request == null ? null : request.tone());
        return ResponseEntity.created(java.net.URI.create("/api/v1/explanations/" + explanation.id()))
                .body(ExplanationResponse.from(explanation));
    }

    /** FR-EXP-02 근거 첨부, FR-EXP-03 수정 문구 제안 */
    @GetMapping("/api/v1/explanations/{id}")
    public ExplanationResponse get(Authentication authentication, @PathVariable long id) {
        return ExplanationResponse.from(explanations.get(currentUser.username(authentication), id));
    }
}
