package ac.mju.contractcheck.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ac.mju.contractcheck.domain.Milestone;
import ac.mju.contractcheck.domain.UserId;
import ac.mju.contractcheck.global.error.ApiException;
import ac.mju.contractcheck.global.error.NotFoundException;
import ac.mju.contractcheck.repository.MilestoneRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

/** FR-MIL-01·02 일정 생성·검증·재생성·구간 조회 규칙. */
class MilestoneServiceTest {

    private static final UserId OWNER = new UserId(7);

    private final MilestoneRepository milestones = mock(MilestoneRepository.class);
    private final ContractService contracts = mock(ContractService.class);
    private final MilestoneService service = new MilestoneService(milestones, contracts);

    @Test
    @DisplayName("FR-MIL-01·TC-MIL-004 자동 생성은 기존 자동 타입만 지우고 갱신거절 통지기한(만기 2개월 전) 포함 5건을 저장한다")
    void generateReplacesAutoMilestonesAndCreatesFiveItems() {
        when(milestones.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        List<Milestone> created = service.generate(OWNER, 10L, "2026-04-01", "2026-04-20", "2026-05-01", "2027-05-01");

        assertThat(created).extracting(Milestone::type)
                .containsExactly("CONTRACT", "BALANCE", "MOVE_IN", "RENEWAL_NOTICE", "END");
        assertThat(created).filteredOn(item -> "RENEWAL_NOTICE".equals(item.type()))
                .extracting(Milestone::dueDate, Milestone::remindDays)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("2027-03-01", 30));

        InOrder order = inOrder(contracts, milestones);
        order.verify(contracts).get(OWNER, 10L);
        order.verify(milestones).deleteByContractAndTypes(
                OWNER, 10L, Set.of("CONTRACT", "BALANCE", "MOVE_IN", "RENEWAL_NOTICE", "END"));
        order.verify(milestones).saveAll(anyList());
    }

    @Test
    @DisplayName("FR-MIL-01 날짜 형식이 잘못되거나 만기 < 입주 등 순서가 어긋나면 400 VALIDATION_ERROR다")
    void generateRejectsInvalidDatesOrOrder() {
        assertValidationError(() -> service.generate(OWNER, 10L, "2026-04-01", "2026-04-20", "2026-05-01", "2026-04-30"));
        assertValidationError(() -> service.generate(OWNER, 10L, "2026-04-10", "2026-04-01", "2026-05-01", "2027-05-01"));
        assertValidationError(() -> service.generate(OWNER, 10L, "not-a-date", "2026-04-20", "2026-05-01", "2027-05-01"));
        verify(milestones, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("FR-MIL-02 목록 조회는 from·to를 파싱해 저장소에 전달하고 from > to이면 400이다")
    void listValidatesAndDelegatesDateRange() {
        when(milestones.findByOwner(OWNER, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 6, 1)))
                .thenReturn(List.of());

        assertThat(service.list(OWNER, "2026-04-01", "2026-06-01")).isEmpty();
        verify(milestones).findByOwner(OWNER, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 6, 1));

        assertValidationError(() -> service.list(OWNER, "2026-06-01", "2026-04-01"));
        assertValidationError(() -> service.list(OWNER, "2026/04/01", null));
    }

    @Test
    @DisplayName("FR-MIL-02 contractId가 없거나(<= 0) 입력이 잘못된 수동 일정은 400 VALIDATION_ERROR다")
    void createRejectsMissingContractIdOrInvalidFields() {
        assertValidationError(() -> service.create(OWNER, 0L, "CUSTOM", "전입신고", "2026-05-02", 3));
        assertValidationError(() -> service.create(OWNER, -1L, "CUSTOM", "전입신고", "2026-05-02", 3));
        assertValidationError(() -> service.create(OWNER, 10L, "  ", "전입신고", "2026-05-02", 3));
        assertValidationError(() -> service.create(OWNER, 10L, "CUSTOM", "", "2026-05-02", 3));
        assertValidationError(() -> service.create(OWNER, 10L, "CUSTOM", "전입신고", "2026-05-02", -1));
        verify(milestones, never()).save(any());
    }

    @Test
    @DisplayName("FR-MIL-02 수정은 전달된 필드만 바꾸고 없거나 타인 일정이면 404 MILESTONE_NOT_FOUND다")
    void updateAppliesOnlyProvidedFieldsAndHidesUnowned() {
        Milestone existing = new Milestone(5L, OWNER, 10L, "CUSTOM", "전입신고", "2026-05-02", 3, false);
        when(milestones.findOwned(5L, OWNER)).thenReturn(Optional.of(existing));
        when(milestones.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Milestone updated = service.update(OWNER, 5L, null, null, null, true);

        assertThat(updated).isEqualTo(new Milestone(5L, OWNER, 10L, "CUSTOM", "전입신고", "2026-05-02", 3, true));

        when(milestones.findOwned(99L, OWNER)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.update(OWNER, 99L, "새 제목", null, null, null))
                .isInstanceOf(NotFoundException.class)
                .extracting(ex -> ((NotFoundException) ex).code())
                .isEqualTo("MILESTONE_NOT_FOUND");
        assertThatThrownBy(() -> service.delete(OWNER, 99L))
                .isInstanceOf(NotFoundException.class)
                .extracting(ex -> ((NotFoundException) ex).code())
                .isEqualTo("MILESTONE_NOT_FOUND");
        verify(milestones, never()).delete(eq(99L));
    }

    private static void assertValidationError(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    assertThat(((ApiException) ex).status()).isEqualTo(400);
                    assertThat(((ApiException) ex).code()).isEqualTo("VALIDATION_ERROR");
                });
    }
}
