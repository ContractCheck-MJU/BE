package ac.mju.contractcheck.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ac.mju.contractcheck.client.InferenceException;
import ac.mju.contractcheck.domain.EvidenceHit;
import ac.mju.contractcheck.global.error.ApiException;
import ac.mju.contractcheck.repository.memory.PrecedentRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** FR-PRC-01 법령·판례 근거 검색 검증 및 위임 규칙. */
class PrecedentServiceTest {

    private static final EvidenceHit LAW_HIT = new EvidenceHit(
            7, 70, "LAW", "민법 제640조(차임연체와 해지)", "제640조",
            "임차인의 차임연체액이 2기의 차임액에 달하는 때", "https://law.go.kr/640", 0.81);

    private final EvidenceRetriever retriever = mock(EvidenceRetriever.class);
    private final PrecedentRepository precedents = new PrecedentRepository();
    private final PrecedentService service = new PrecedentService(retriever, precedents);

    @Test
    @DisplayName("FR-PRC-01 type이 비어 있으면 전체 유형(null)으로 질의어를 다듬어 검색한다")
    void searchWithoutTypeQueriesAllDocTypes() {
        when(retriever.search("차임 연체 해지", null, 5)).thenReturn(List.of(LAW_HIT));

        assertThat(service.searchPrecedents("  차임 연체 해지  ", null, 5)).containsExactly(LAW_HIT);
        assertThat(service.searchPrecedents("차임 연체 해지", "   ", 5)).containsExactly(LAW_HIT);
    }

    @Test
    @DisplayName("FR-PRC-01 type(LAW·PRECEDENT)과 k 경계값(3·5)을 그대로 전달해 검색한다")
    void searchWithDocTypeAndBoundaryLimits() {
        when(retriever.search("보증금 반환", "LAW", 3)).thenReturn(List.of(LAW_HIT));
        when(retriever.search("보증금 반환", "PRECEDENT", 5)).thenReturn(List.of());

        assertThat(service.searchPrecedents("보증금 반환", "LAW", 3)).containsExactly(LAW_HIT);
        assertThat(service.searchPrecedents("보증금 반환", "PRECEDENT", 5)).isEmpty();
        verify(retriever).search("보증금 반환", "LAW", 3);
        verify(retriever).search("보증금 반환", "PRECEDENT", 5);
    }

    @Test
    @DisplayName("FR-PRC-01 검색어가 null이거나 공백이면 400 VALIDATION_ERROR이고 검색기를 부르지 않는다")
    void blankQueryIsRejected() {
        for (String blank : new String[] {null, "", "   "}) {
            assertValidationError(() -> service.searchPrecedents(blank, null, 5));
        }
        verify(retriever, never()).search(anyString(), org.mockito.ArgumentMatchers.any(), anyInt());
    }

    @Test
    @DisplayName("FR-PRC-01 k가 3 미만이거나 5 초과이면 400 VALIDATION_ERROR이고 검색기를 부르지 않는다")
    void outOfRangeLimitIsRejected() {
        for (int invalidK : new int[] {-1, 0, 2, 6, 20}) {
            assertValidationError(() -> service.searchPrecedents("차임 연체", null, invalidK));
        }
        verify(retriever, never()).search(anyString(), org.mockito.ArgumentMatchers.any(), anyInt());
    }

    @Test
    @DisplayName("FR-PRC-01 허용되지 않은 문서 유형이면 400 VALIDATION_ERROR이고 검색기를 부르지 않는다")
    void unknownDocTypeIsRejected() {
        for (String invalidType : new String[] {"CASE", "law", "KFTC"}) {
            assertValidationError(() -> service.searchPrecedents("차임 연체", invalidType, 5));
        }
        verify(retriever, never()).search(anyString(), org.mockito.ArgumentMatchers.any(), anyInt());
    }

    @Test
    @DisplayName("FR-PRC-01 추론 서버 장애는 InferenceException 그대로 올라간다")
    void inferenceFailureIsPropagated() {
        when(retriever.search("차임 연체", null, 5)).thenThrow(new InferenceException("inference down"));

        assertThatThrownBy(() -> service.searchPrecedents("차임 연체", null, 5))
                .isInstanceOf(InferenceException.class);
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
