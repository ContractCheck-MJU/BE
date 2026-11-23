package ac.mju.contractcheck.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

import ac.mju.contractcheck.client.StorageClient;
import ac.mju.contractcheck.domain.Contract;
import ac.mju.contractcheck.domain.OAuthProfile;
import ac.mju.contractcheck.domain.UserId;
import ac.mju.contractcheck.global.error.NotFoundException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 트랜잭션 경계는 service가 정한다(structure.md). 클래스 단위 @Transactional을 쓰지 않아서(테스트 트랜잭션이 가리지 않도록)
 * service가 실제로 트랜잭션을 열고 닫는지 확인한다. 외부 호출(저장소 SeaweedFS)은 트랜잭션 밖에서 일어나야 한다(AGENTS.md 절대 규칙 4).
 */
@SpringBootTest
@ActiveProfiles("test")
class TransactionBoundaryTest {

    private static final String THREE_ARTICLES = "제1조(목적) 이 계약은 임대차에 관한 것이다.\n"
            + "제2조(보증금) 보증금은 1,000만원으로 한다.\n"
            + "제3조(계약기간) 계약기간은 1년으로 한다.";

    @Autowired
    AuthService auth;
    @Autowired
    ContractService contracts;
    @Autowired
    ClauseService clauses;
    @MockitoBean
    StorageClient storage;

    @Test
    @DisplayName("계약서 삭제: 저장소 원본 삭제는 트랜잭션 밖에서, 계약·조항 삭제는 성공하고 결과가 남는다")
    void deleteCallsStorageOutsideTransaction() {
        UserId owner = newOwner();
        Contract contract = contracts.upload(owner, pdf(), "제목", "MONTHLY");
        clauses.split(owner, contract.id(), THREE_ARTICLES);
        AtomicReference<Boolean> transactionActive = new AtomicReference<>();
        doAnswer(invocation -> {
            transactionActive.set(TransactionSynchronizationManager.isActualTransactionActive());
            return null;
        }).when(storage).delete(anyString());

        contracts.delete(owner, contract.id());

        assertThat(transactionActive.get()).as("저장소 삭제 시점의 트랜잭션").isFalse();
        assertThatThrownBy(() -> contracts.get(owner, contract.id())).isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("계약서 업로드: 저장소 업로드는 트랜잭션 밖에서 하고 새 트랜잭션을 만들지 않는다")
    void uploadCallsStorageOutsideTransaction() {
        UserId owner = newOwner();
        AtomicReference<Boolean> transactionActive = new AtomicReference<>();
        doAnswer(invocation -> {
            transactionActive.set(TransactionSynchronizationManager.isActualTransactionActive());
            return null;
        }).when(storage).put(anyString(), org.mockito.ArgumentMatchers.any(byte[].class), anyString());

        Contract saved = contracts.upload(owner, pdf(), "제목", "MONTHLY");

        assertThat(transactionActive.get()).as("저장소 업로드 시점의 트랜잭션").isFalse();
        assertThat(contracts.get(owner, saved.id()).id()).isEqualTo(saved.id());
    }

    private UserId newOwner() {
        String providerUserId = UUID.randomUUID().toString();
        return auth.signIn(new OAuthProfile("kakao", providerUserId, providerUserId + "@example.com", "닉네임")).id();
    }

    private MockMultipartFile pdf() {
        byte[] content = new byte[1024];
        content[0] = '%';
        content[1] = 'P';
        content[2] = 'D';
        content[3] = 'F';
        return new MockMultipartFile("file", "lease.pdf", "application/pdf", content);
    }
}
