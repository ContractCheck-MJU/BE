package ac.mju.contractcheck.controller;

import ac.mju.contractcheck.domain.Milestone;
import ac.mju.contractcheck.dto.common.ItemsResponse;
import ac.mju.contractcheck.dto.milestone.MilestoneCreateRequest;
import ac.mju.contractcheck.dto.milestone.MilestoneGenerateRequest;
import ac.mju.contractcheck.dto.milestone.MilestoneItem;
import ac.mju.contractcheck.dto.milestone.MilestoneUpdateRequest;
import ac.mju.contractcheck.global.security.CurrentUser;
import ac.mju.contractcheck.service.MilestoneService;

import java.util.List;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * FR-MIL-01 일정 자동 추출
 * FR-MIL-02 일정 관리
 * FR-MIL-03 .ics 내보내기
 */
@RestController
@RequiredArgsConstructor
public class MilestoneRestController {
    @NonNull
    private final MilestoneService milestones;
    @NonNull
    private final CurrentUser currentUser;

    /** FR-MIL-01 일정 자동 추출 */
    @PostMapping("/api/v1/contracts/{id}/milestones/generate")
    public ItemsResponse<MilestoneItem> generate(Authentication authentication, @PathVariable long id, @RequestBody MilestoneGenerateRequest request) {
        List<Milestone> created = milestones.generate(
                currentUser.userId(authentication), id,
                request.contractDate(), request.balanceDate(), request.moveInDate(), request.endDate());
        return new ItemsResponse<>(created.stream().map(MilestoneItem::from).toList());
    }

    /** FR-MIL-02 일정 관리 */
    @GetMapping("/api/v1/milestones")
    public ItemsResponse<MilestoneItem> list(
            Authentication authentication,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return new ItemsResponse<>(
                milestones.list(currentUser.userId(authentication), from, to).stream()
                        .map(MilestoneItem::from)
                        .toList());
    }

    /** FR-MIL-02 일정 관리 */
    @PostMapping("/api/v1/milestones")
    public ResponseEntity<MilestoneItem> create(Authentication authentication, @RequestBody MilestoneCreateRequest request) {
        Milestone milestone = milestones.create(
                currentUser.userId(authentication), request.contractId(), request.type(), request.title(), request.dueDate(), request.remindDays());
        return ResponseEntity.created(java.net.URI.create("/api/v1/milestones/" + milestone.id())).body(MilestoneItem.from(milestone));
    }

    /** FR-MIL-02 일정 관리 */
    @PatchMapping("/api/v1/milestones/{id}")
    public MilestoneItem update(Authentication authentication, @PathVariable long id, @RequestBody MilestoneUpdateRequest request) {
        return MilestoneItem.from(milestones.update(
                currentUser.userId(authentication), id, request.title(), request.dueDate(), request.remindDays(), request.done()));
    }

    /** FR-MIL-02 일정 관리 */
    @DeleteMapping("/api/v1/milestones/{id}")
    public ResponseEntity<Void> delete(Authentication authentication, @PathVariable long id) {
        milestones.delete(currentUser.userId(authentication), id);
        return ResponseEntity.noContent().build();
    }

    /** FR-MIL-03 .ics 내보내기 */
    @GetMapping(value = "/api/v1/contracts/{id}/milestones.ics", produces = "text/calendar")
    public ResponseEntity<String> ics(Authentication authentication, @PathVariable long id) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/calendar")).body(milestones.ics(currentUser.userId(authentication), id));
    }
}
