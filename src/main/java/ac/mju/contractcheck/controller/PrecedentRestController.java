package ac.mju.contractcheck.controller;

import ac.mju.contractcheck.dto.common.ItemsResponse;
import ac.mju.contractcheck.service.PrecedentService;

import java.util.Map;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * FR-PRC-01 근거 검색
 */
@RestController
@RequiredArgsConstructor
public class PrecedentRestController {
    @NonNull
    private final PrecedentService precedents;

    /** FR-PRC-01 근거 검색 */
    @GetMapping("/api/v1/precedents/search")
    public ItemsResponse<Map<String, Object>> precedents(
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "5") int k) {
        return new ItemsResponse<>(precedents.searchPrecedents(query, type, k));
    }

    /** FR-PRC-01 근거 검색 */
    @GetMapping("/api/v1/cases/search")
    public ItemsResponse<Map<String, Object>> cases(
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(required = false) Long clauseId,
            @RequestParam(required = false) String unfairType,
            @RequestParam(defaultValue = "5") int k) {
        return new ItemsResponse<>(precedents.searchCases(query, clauseId, unfairType, k));
    }
}
