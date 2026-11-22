package ac.mju.contractcheck.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ac.mju.contractcheck.client.StorageClient;
import ac.mju.contractcheck.domain.Contract;
import ac.mju.contractcheck.domain.OAuthProfile;
import ac.mju.contractcheck.dto.contract.FileUrl;
import ac.mju.contractcheck.global.error.ApiException;
import ac.mju.contractcheck.global.error.CreditInsufficientException;
import ac.mju.contractcheck.global.error.NotFoundException;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** FR-UPL-01 계약서 업로드·조회·삭제. 저장소(SeaweedFS)는 mock하고 회원은 테스트마다 새로 가입시킨다. */
@SpringBootTest
@ActiveProfiles("test")
class ContractServiceTest {

    private static final int MAX_BYTES = 20 * 1024 * 1024;

    @Autowired
    ContractService contracts;

    @Autowired
    AuthService auth;

    @Autowired
    CreditService credits;

    @MockitoBean
    StorageClient storage;

    @Test
    @DisplayName("TC-UPL-001 PDF 업로드는 UPLOADED로 저장하고 객체를 올리며 이용권은 차감하지 않는다")
    void uploadStoresObjectAndKeepsCredits() {
        String owner = newOwner();

        Contract saved = contracts.upload(owner, pdf(1024), "신촌 원룸", "MONTHLY");

        assertThat(saved.id()).isPositive();
        assertThat(saved.status()).isEqualTo("UPLOADED");
        assertThat(saved.title()).isEqualTo("신촌 원룸");
        assertThat(saved.contractType()).isEqualTo("MONTHLY");
        assertThat(saved.s3Key()).startsWith("contracts/" + owner + "/");
        verify(storage).put(eq(saved.s3Key()), any(byte[].class), eq("application/pdf"));
        assertThat(credits.me(owner).freeRemaining()).isEqualTo(2);
        assertThat(contracts.list(owner, 0, 20).items()).extracting(Contract::id).containsExactly(saved.id());
    }

    @Test
    @DisplayName("TC-UPL-002 확장자가 아니라 매직바이트로 판별해 지원하지 않는 형식은 415이고 저장하지 않는다")
    void unsupportedTypeIsRejectedByMagicBytes() {
        String owner = newOwner();
        MockMultipartFile exeNamedPdf = new MockMultipartFile(
                "file", "contract.pdf", "application/pdf", new byte[] {'M', 'Z', 0, 0, 0, 0});
        MockMultipartFile docx = new MockMultipartFile(
                "file", "contract.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                new byte[] {'P', 'K', 3, 4, 0, 0});

        for (MockMultipartFile file : new MockMultipartFile[] {exeNamedPdf, docx}) {
            assertThatThrownBy(() -> contracts.upload(owner, file, "제목", "MONTHLY"))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> {
                        assertThat(((ApiException) ex).status()).isEqualTo(415);
                        assertThat(((ApiException) ex).code()).isEqualTo("UNSUPPORTED_FILE_TYPE");
                    });
        }
        verify(storage, never()).put(anyString(), any(byte[].class), anyString());
        assertThat(contracts.list(owner, 0, 20).items()).isEmpty();
    }

    @Test
    @DisplayName("TC-UPL-003 정확히 20MB는 통과하고 20MB+1바이트는 413 FILE_TOO_LARGE를 일으킨다")
    void sizeBoundary() {
        String owner = newOwner();

        Contract saved = contracts.upload(owner, pdf(MAX_BYTES), "제목", "JEONSE");

        assertThat(saved.status()).isEqualTo("UPLOADED");
        assertThatThrownBy(() -> contracts.upload(owner, pdf(MAX_BYTES + 1), "제목", "JEONSE"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    assertThat(((ApiException) ex).status()).isEqualTo(413);
                    assertThat(((ApiException) ex).code()).isEqualTo("FILE_TOO_LARGE");
                });
    }

    @Test
    @DisplayName("TC-UPL-004 이용권이 없으면 402 CREDIT_INSUFFICIENT이고 저장하지 않는다")
    void noCreditsIsRejectedBeforeStoring() {
        String owner = newOwner();
        credits.use(owner, 1L);
        credits.use(owner, 2L);

        assertThatThrownBy(() -> contracts.upload(owner, pdf(1024), "제목", "MONTHLY"))
                .isInstanceOf(CreditInsufficientException.class);

        verify(storage, never()).put(anyString(), any(byte[].class), anyString());
        assertThat(contracts.list(owner, 0, 20).items()).isEmpty();
    }

    @Test
    @DisplayName("API-CON-01 제목이 비었거나 200자를 넘거나 계약 유형이 MONTHLY·JEONSE가 아니면 400 VALIDATION_ERROR를 일으킨다")
    void invalidMetadataIsRejected() {
        String owner = newOwner();

        for (String[] invalid : new String[][] {{"", "MONTHLY"}, {"가".repeat(201), "MONTHLY"}, {"제목", "SALE"}}) {
            assertThatThrownBy(() -> contracts.upload(owner, pdf(1024), invalid[0], invalid[1]))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> assertThat(((ApiException) ex).status()).isEqualTo(400));
        }
        verify(storage, never()).put(anyString(), any(byte[].class), anyString());
    }

    @Test
    @DisplayName("TC-UPL-016 다른 회원의 계약은 상세·file-url·삭제 모두 404 CONTRACT_NOT_FOUND를 일으키고 데이터는 그대로 유지된다")
    void otherMembersContractIsHidden() {
        String owner = newOwner();
        String stranger = newOwner();
        Contract saved = contracts.upload(owner, pdf(1024), "제목", "MONTHLY");

        assertNotFound(() -> contracts.get(stranger, saved.id()));
        assertNotFound(() -> contracts.fileUrl(stranger, saved.id()));
        assertNotFound(() -> contracts.delete(stranger, saved.id()));

        assertThat(contracts.get(owner, saved.id()).id()).isEqualTo(saved.id());
        verify(storage, never()).delete(anyString());
    }

    @Test
    @DisplayName("TC-UPL-017 file-url은 저장소의 5분짜리 사전서명 URL이어야 한다")
    void fileUrlIsPresignedForFiveMinutes() {
        String owner = newOwner();
        Contract saved = contracts.upload(owner, pdf(1024), "제목", "MONTHLY");
        when(storage.presignGet(eq(saved.s3Key()), eq(Duration.ofMinutes(5)))).thenReturn("https://storage.test/signed");

        FileUrl fileUrl = contracts.fileUrl(owner, saved.id());

        assertThat(fileUrl.url()).isEqualTo("https://storage.test/signed");
        assertThat(fileUrl.expiresAt()).isNotBlank();
    }

    @Test
    @DisplayName("TC-CRD-011 삭제하면 저장소 객체와 행을 지우고 쓴 이용권은 복구하지 않는다")
    void deleteRemovesObjectAndRow() {
        String owner = newOwner();
        Contract saved = contracts.upload(owner, pdf(1024), "제목", "MONTHLY");
        credits.use(owner, 7L);

        contracts.delete(owner, saved.id());

        verify(storage).delete(saved.s3Key());
        assertNotFound(() -> contracts.get(owner, saved.id()));
        assertThat(credits.me(owner).freeRemaining()).isEqualTo(1);
    }

    @Test
    @DisplayName("저장소 업로드가 실패하면 계약 행을 만들지 않는다")
    void storageFailureLeavesNoRow() {
        String owner = newOwner();
        org.mockito.Mockito.doThrow(new IllegalStateException("storage down"))
                .when(storage).put(startsWith("contracts/"), any(byte[].class), anyString());

        assertThatThrownBy(() -> contracts.upload(owner, pdf(1024), "제목", "MONTHLY"))
                .isInstanceOf(IllegalStateException.class);

        assertThat(contracts.list(owner, 0, 20).items()).isEmpty();
    }

    private void assertNotFound(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(NotFoundException.class)
                .extracting(ex -> ((NotFoundException) ex).code())
                .isEqualTo("CONTRACT_NOT_FOUND");
    }

    private String newOwner() {
        String providerUserId = UUID.randomUUID().toString();
        return String.valueOf(auth.signIn(
                new OAuthProfile("kakao", providerUserId, providerUserId + "@example.com", "닉네임")).id());
    }

    private MockMultipartFile pdf(int size) {
        byte[] content = new byte[size];
        content[0] = '%';
        content[1] = 'P';
        content[2] = 'D';
        content[3] = 'F';
        return new MockMultipartFile("file", "lease.pdf", "application/pdf", content);
    }
}
