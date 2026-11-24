package ac.mju.contractcheck.repository;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** V1__init.sql의 {@code milestones}. 계약이 삭제되면 contract_id가 NULL이 될 수 있다(V2 ON DELETE SET NULL). */
@Entity
@Table(name = "milestones")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class MilestoneEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    private Long contractId;

    @Column(length = 30)
    private String type;

    @Column(length = 200)
    private String title;

    private LocalDate dueDate;

    @Column(name = "remind_days")
    private Integer[] remindDays;

    private boolean done;

    MilestoneEntity(
            Long id,
            Long userId,
            Long contractId,
            String type,
            String title,
            LocalDate dueDate,
            Integer[] remindDays,
            boolean done) {
        this.id = id;
        this.userId = userId;
        this.contractId = contractId;
        this.type = type;
        this.title = title;
        this.dueDate = dueDate;
        this.remindDays = remindDays;
        this.done = done;
    }
}
