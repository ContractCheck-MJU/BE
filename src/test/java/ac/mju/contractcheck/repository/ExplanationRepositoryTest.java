package ac.mju.contractcheck.repository;

import static org.assertj.core.api.Assertions.assertThat;

import ac.mju.contractcheck.domain.ClauseDraft;
import ac.mju.contractcheck.domain.Contract;
import ac.mju.contractcheck.domain.Explanation;
import ac.mju.contractcheck.domain.ExplanationCitation;
import ac.mju.contractcheck.domain.UserId;
import ac.mju.contractcheck.domain.enums.ContractState;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/** FR-EXP-01·02 설명·인용 저장과 조항 → 계약 → 회원으로 하는 소유자 확인. 테스트마다 롤백된다. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ExplanationRepositoryTest {

    @Autowired
    ExplanationRepository explanations;
    @Autowired
    ContractRepository contracts;
    @Autowired
    ClauseRepository clauses;

    @Test
    @DisplayName("FR-EXP-02 설명과 인용을 저장하면 소유자는 인용까지 다시 읽고 다른 회원은 404처럼 비어 있다")
    void savesAndReadsOwnedWithCitations() {
        UserId owner = new UserId(System.nanoTime());
        long clauseId = clauseOf(owner);

        Explanation saved = explanations.save(explanation(clauseId, owner, List.of(
                new ExplanationCitation("LAW", 1L, 0.876543),
                new ExplanationCitation("PRECEDENT", 2L, 0.61))));

        Explanation read = explanations.findOwned(saved.id(), owner).orElseThrow();
        assertThat(read.text()).isEqualTo("설명");
        assertThat(read.suggestion()).isEqualTo("제안");
        assertThat(read.llmModel()).isEqualTo("claude-sonnet-5-5");
        assertThat(read.promptVersion()).isEqualTo("exp-v1");
        assertThat(read.citations()).as("점수는 소수 넷째 자리로 저장한다").containsExactly(
                new ExplanationCitation("LAW", 1L, 0.8765),
                new ExplanationCitation("PRECEDENT", 2L, 0.61));
        assertThat(explanations.findOwned(saved.id(), new UserId(owner.value() + 1))).isEmpty();
    }

    @Test
    @DisplayName("TC-EXP-010 조항별 설명 개수를 센다")
    void countsByClause() {
        UserId owner = new UserId(System.nanoTime());
        long clauseId = clauseOf(owner);
        long otherClause = clauseOf(owner);

        explanations.save(explanation(clauseId, owner, List.of()));
        explanations.save(explanation(clauseId, owner, List.of()));
        explanations.save(explanation(otherClause, owner, List.of()));

        assertThat(explanations.countByClause(clauseId)).isEqualTo(2);
    }

    private long clauseOf(UserId owner) {
        long contractId = contracts.save(new Contract(
                0, owner, "계약", "MONTHLY", "contracts/" + owner + "/x", ContractState.SPLIT, "", Instant.now(), null)).id();
        return clauses.saveAll(contractId, owner, List.of(new ClauseDraft(1, "제1조(보증금)", "본문"))).getFirst().id();
    }

    private static Explanation explanation(long clauseId, UserId owner, List<ExplanationCitation> citations) {
        return new Explanation(0, clauseId, owner, "설명", "제안", "claude-sonnet-5-5", "exp-v1", Instant.now(), citations);
    }
}
