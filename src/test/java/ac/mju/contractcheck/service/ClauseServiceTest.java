package ac.mju.contractcheck.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import ac.mju.contractcheck.client.StorageClient;
import ac.mju.contractcheck.domain.Clause;
import ac.mju.contractcheck.domain.Contract;
import ac.mju.contractcheck.domain.OAuthProfile;
import ac.mju.contractcheck.domain.SplitClauses;
import ac.mju.contractcheck.domain.UserId;
import ac.mju.contractcheck.global.error.ApiException;
import ac.mju.contractcheck.global.error.NotFoundException;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * FR-UPL-03 조항 분할 저장·조회·수정. 분할 규칙 자체는 ClauseSplitterTest가 다룬다.
 * 저장소(SeaweedFS)는 mock하고 회원과 계약은 테스트마다 새로 만든다.
 */
@SpringBootTest
@ActiveProfiles("test")
class ClauseServiceTest {

    private static final String THREE_ARTICLES = "제1조(목적) 이 계약은 임대차에 관한 것이다.\n"
            + "제2조(보증금) 보증금은 1,000만원으로 한다.\n"
            + "제3조(계약기간) 계약기간은 1년으로 한다.";

    @Autowired
    ClauseService clauses;

    @Autowired
    ContractService contracts;

    @Autowired
    AuthService auth;

    @MockitoBean
    StorageClient storage;

    @Test
    @DisplayName("TC-UPL-010 분할한 조항은 번호 순서대로 저장되고 목록으로 조회된다")
    void splitStoresClausesInOrder() {
        UserId owner = newOwner();
        Contract contract = newContract(owner);

        SplitClauses<Clause> stored = clauses.split(owner, contract.id(), THREE_ARTICLES);

        assertThat(stored.paragraphFallback()).isFalse();
        assertThat(stored.clauses()).extracting(Clause::no).containsExactly(1, 2, 3);
        assertThat(stored.clauses()).extracting(Clause::id).doesNotContain(0L);
        assertThat(clauses.list(owner, contract.id()))
                .extracting(Clause::no, Clause::title)
                .containsExactly(
                        tuple(1, "제1조(목적)"),
                        tuple(2, "제2조(보증금)"),
                        tuple(3, "제3조(계약기간)"));
        assertThat(clauses.list(owner, contract.id()).get(1).text()).contains("1,000만원");
    }

    @Test
    @DisplayName("TC-UPL-012 조항 패턴이 없으면 문단으로 대체 분할해 저장하고 대체 분할 표시를 돌려준다")
    void splitFallsBackToParagraphs() {
        UserId owner = newOwner();
        Contract contract = newContract(owner);

        SplitClauses<Clause> stored = clauses.split(owner, contract.id(), "첫 문단입니다.\n\n둘째 문단입니다.");

        assertThat(stored.paragraphFallback()).isTrue();
        assertThat(clauses.list(owner, contract.id())).hasSize(2);
    }

    @Test
    @DisplayName("다시 분할하면 이전 조항은 지우고 새 결과로 바꾼다")
    void resplitReplacesPreviousClauses() {
        UserId owner = newOwner();
        Contract contract = newContract(owner);
        clauses.split(owner, contract.id(), THREE_ARTICLES);

        clauses.split(owner, contract.id(), "제1조(목적) 하나만 남는다.");

        assertThat(clauses.list(owner, contract.id())).extracting(Clause::title).containsExactly("제1조(목적)");
    }

    @Test
    @DisplayName("빈 원문으로 분할하면 이전 조항을 지우고 조항 0건이며 대체 분할도 아니다")
    void splitOfBlankTextClearsClauses() {
        UserId owner = newOwner();
        Contract contract = newContract(owner);
        clauses.split(owner, contract.id(), THREE_ARTICLES);

        SplitClauses<Clause> stored = clauses.split(owner, contract.id(), "   ");

        assertThat(stored.clauses()).isEmpty();
        assertThat(stored.paragraphFallback()).isFalse();
        assertThat(clauses.list(owner, contract.id())).isEmpty();
    }

    @Test
    @DisplayName("분할하지 않은 계약의 조항 목록은 비어 있다")
    void listOfUnsplitContractIsEmpty() {
        UserId owner = newOwner();

        assertThat(clauses.list(owner, newContract(owner).id())).isEmpty();
    }

    @Test
    @DisplayName("TC-UPL-013 조항 수정은 전달한 값만 바꾸고 저장된다")
    void updateChangesOnlyGivenFields() {
        UserId owner = newOwner();
        Contract contract = newContract(owner);
        Clause second = clauses.split(owner, contract.id(), THREE_ARTICLES).clauses().get(1);

        Clause updated = clauses.update(owner, second.id(), null, null, "보증금은 2,000만원으로 한다.");

        assertThat(updated.text()).isEqualTo("보증금은 2,000만원으로 한다.");
        assertThat(updated.title()).isEqualTo("제2조(보증금)");
        assertThat(updated.no()).isEqualTo(2);
        Clause reloaded = clauses.list(owner, contract.id()).get(1);
        assertThat(reloaded.text()).isEqualTo("보증금은 2,000만원으로 한다.");
    }

    @Test
    @DisplayName("조항 번호와 제목도 수정할 수 있다")
    void updateCanChangeNumberAndTitle() {
        UserId owner = newOwner();
        Contract contract = newContract(owner);
        Clause first = clauses.split(owner, contract.id(), THREE_ARTICLES).clauses().get(0);

        Clause updated = clauses.update(owner, first.id(), 7, "제7조(특약)", null);

        assertThat(updated.no()).isEqualTo(7);
        assertThat(updated.title()).isEqualTo("제7조(특약)");
        assertThat(clauses.list(owner, contract.id())).extracting(Clause::no).containsExactly(2, 3, 7);
    }

    @Test
    @DisplayName("수정 값이 어긋나면 400 VALIDATION_ERROR이고 조항은 그대로다")
    void invalidEditIsRejected() {
        UserId owner = newOwner();
        Contract contract = newContract(owner);
        Clause first = clauses.split(owner, contract.id(), THREE_ARTICLES).clauses().get(0);

        assertValidationError(() -> clauses.update(owner, first.id(), 0, null, null));
        assertValidationError(() -> clauses.update(owner, first.id(), null, "가".repeat(201), null));
        assertValidationError(() -> clauses.update(owner, first.id(), null, null, "   "));

        assertThat(clauses.list(owner, contract.id()).get(0).title()).isEqualTo("제1조(목적)");
    }

    @Test
    @DisplayName("TC-UPL-016 타인의 계약 조항은 분할·조회·수정 모두 404이고 데이터가 바뀌지 않는다")
    void strangerCannotTouchClauses() {
        UserId owner = newOwner();
        UserId stranger = newOwner();
        Contract contract = newContract(owner);
        Clause first = clauses.split(owner, contract.id(), THREE_ARTICLES).clauses().get(0);

        assertNotFound(() -> clauses.list(stranger, contract.id()), "CONTRACT_NOT_FOUND");
        assertNotFound(() -> clauses.split(stranger, contract.id(), "제1조(침입) 덮어쓰기"), "CONTRACT_NOT_FOUND");
        assertNotFound(() -> clauses.update(stranger, first.id(), null, "침입", null), "CLAUSE_NOT_FOUND");

        assertThat(clauses.list(owner, contract.id())).hasSize(3);
        assertThat(clauses.list(owner, contract.id()).get(0).title()).isEqualTo("제1조(목적)");
    }

    @Test
    @DisplayName("없는 조항 수정은 404 CLAUSE_NOT_FOUND다")
    void missingClauseIsNotFound() {
        assertNotFound(() -> clauses.update(newOwner(), 999_999L, null, "없음", null), "CLAUSE_NOT_FOUND");
    }

    @Test
    @DisplayName("계약을 지우면 그 조항도 함께 지우고 다른 계약의 조항은 그대로 둔다")
    void deletingContractDeletesItsClauses() {
        UserId owner = newOwner();
        Contract doomed = newContract(owner);
        Contract kept = newContract(owner);
        clauses.split(owner, doomed.id(), THREE_ARTICLES);
        clauses.split(owner, kept.id(), "제1조(목적) 남는 계약이다.");

        contracts.delete(owner, doomed.id());

        assertNotFound(() -> clauses.list(owner, doomed.id()), "CONTRACT_NOT_FOUND");
        assertThat(clauses.list(owner, kept.id())).hasSize(1);
    }

    private void assertValidationError(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).code())
                .isEqualTo("VALIDATION_ERROR");
    }

    private void assertNotFound(Runnable action, String code) {
        assertThatThrownBy(action::run)
                .isInstanceOf(NotFoundException.class)
                .extracting(ex -> ((NotFoundException) ex).code())
                .isEqualTo(code);
    }

    private Contract newContract(UserId owner) {
        byte[] content = new byte[64];
        content[0] = '%';
        content[1] = 'P';
        content[2] = 'D';
        content[3] = 'F';
        return contracts.upload(owner, new MockMultipartFile("file", "lease.pdf", "application/pdf", content), "계약서", "MONTHLY");
    }

    private UserId newOwner() {
        String providerUserId = UUID.randomUUID().toString();
        return auth.signIn(
                new OAuthProfile("kakao", providerUserId, providerUserId + "@example.com", "닉네임")).id();
    }
}
