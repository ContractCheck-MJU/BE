package ac.mju.contractcheck.controller;

import ac.mju.contractcheck.domain.Clause;
import ac.mju.contractcheck.dto.clause.ClauseItem;
import ac.mju.contractcheck.dto.clause.ClauseUpdateRequest;
import ac.mju.contractcheck.dto.common.ItemsResponse;
import ac.mju.contractcheck.global.security.CurrentUser;
import ac.mju.contractcheck.service.ClauseService;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * FR-UPL-03 조항 분할
 */
@RestController
@RequiredArgsConstructor
public class ClauseRestController {
    @NonNull
    private final ClauseService clauses;
    @NonNull
    private final CurrentUser currentUser;

    /** FR-UPL-03 조항 분할 */
    @GetMapping("/api/v1/contracts/{id}/clauses")
    public ItemsResponse<ClauseItem> list(Authentication authentication, @PathVariable long id) {
        return new ItemsResponse<>(clauses.list(currentUser.username(authentication), id).stream().map(ClauseItem::from).toList());
    }

    /** FR-UPL-03 조항 분할 */
    @PatchMapping("/api/v1/clauses/{id}")
    public ClauseItem update(Authentication authentication, @PathVariable long id, @RequestBody ClauseUpdateRequest request) {
        Clause clause = clauses.update(currentUser.username(authentication), id, request.no(), request.title(), request.text());
        return ClauseItem.from(clause);
    }
}
