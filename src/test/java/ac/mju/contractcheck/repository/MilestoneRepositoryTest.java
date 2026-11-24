package ac.mju.contractcheck.repository;

import static org.assertj.core.api.Assertions.assertThat;

import ac.mju.contractcheck.domain.Milestone;
import ac.mju.contractcheck.domain.UserId;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/** FR-MIL-01·02 일정 JPA 영속화, remind_days 배열 매핑, 구간 조회, 소유자 격리. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MilestoneRepositoryTest {

    private static final UserId OWNER = new UserId(880_001L);
    private static final UserId OTHER = new UserId(880_002L);

    @Autowired
    MilestoneRepository milestones;

    @Autowired
    MilestoneJpaRepository rows;

    @Test
    @DisplayName("단일 remindDays는 milestones.remind_days 배열에 1원소로 저장되고 다시 읽을 수 있다")
    void storesRemindDaysAsSingleElementArray() {
        Milestone saved = milestones.save(new Milestone(0, OWNER, 10L, "END", "만기일", "2027-05-01", 30, false));

        MilestoneEntity entity = rows.findById(saved.id()).orElseThrow();
        assertThat(entity.getRemindDays()).containsExactly(30);
        assertThat(milestones.findOwned(saved.id(), OWNER)).hasValueSatisfying(found -> {
            assertThat(found.contractId()).isEqualTo(10L);
            assertThat(found.remindDays()).isEqualTo(30);
            assertThat(found.dueDate()).isEqualTo("2027-05-01");
        });
        assertThat(milestones.findOwned(saved.id(), OTHER)).isEmpty();
    }

    @Test
    @DisplayName("내 일정은 due_date 오름차순으로 조회되고 from·to 구간으로 필터링하며 타인 일정은 섞이지 않는다")
    void findByOwnerSortsAndFiltersByDateRange() {
        milestones.saveAll(List.of(
                new Milestone(0, OWNER, 10L, "END", "만기일", "2027-05-01", 30, false),
                new Milestone(0, OWNER, 10L, "CONTRACT", "계약일", "2026-04-01", 7, false),
                new Milestone(0, OWNER, 10L, "MOVE_IN", "입주일", "2026-05-01", 1, false),
                new Milestone(0, OTHER, 20L, "CONTRACT", "타인 계약일", "2026-04-15", 7, false)));

        assertThat(milestones.findByOwner(OWNER))
                .extracting(Milestone::type)
                .containsExactly("CONTRACT", "MOVE_IN", "END");
        assertThat(milestones.findByOwner(OWNER, LocalDate.of(2026, 4, 15), LocalDate.of(2026, 12, 31)))
                .extracting(Milestone::type)
                .containsExactly("MOVE_IN");
        assertThat(milestones.findByOwner(OWNER, LocalDate.of(2026, 5, 1), null))
                .extracting(Milestone::type)
                .containsExactly("MOVE_IN", "END");
        assertThat(milestones.findByOwner(OWNER, null, LocalDate.of(2026, 5, 1)))
                .extracting(Milestone::type)
                .containsExactly("CONTRACT", "MOVE_IN");
    }

    @Test
    @DisplayName("TC-MIL-004 계약과 자동 타입으로 지우면 수동 일정과 다른 계약 일정은 그대로 남는다")
    void deleteByContractAndTypesPreservesManualAndOtherContractMilestones() {
        milestones.saveAll(List.of(
                new Milestone(0, OWNER, 10L, "CONTRACT", "계약일", "2026-04-01", 7, false),
                new Milestone(0, OWNER, 10L, "CUSTOM", "전입신고", "2026-05-02", 3, false),
                new Milestone(0, OWNER, 11L, "CONTRACT", "다른 계약", "2026-06-01", 7, false)));

        milestones.deleteByContractAndTypes(OWNER, 10L, Set.of("CONTRACT", "BALANCE", "MOVE_IN", "RENEWAL_NOTICE", "END"));

        assertThat(milestones.findByContract(OWNER, 10L))
                .extracting(Milestone::type)
                .containsExactly("CUSTOM");
        assertThat(milestones.findByContract(OWNER, 11L))
                .extracting(Milestone::type)
                .containsExactly("CONTRACT");
    }
}
