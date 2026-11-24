package ac.mju.contractcheck.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ac.mju.contractcheck.config.LlmProperties;
import ac.mju.contractcheck.domain.EvidenceHit;
import ac.mju.contractcheck.domain.ExplanationContext;
import ac.mju.contractcheck.domain.ExplanationDraft;
import com.anthropic.client.AnthropicClient;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.models.beta.messages.BetaContentBlock;
import com.anthropic.models.beta.messages.BetaMessage;
import com.anthropic.models.beta.messages.BetaStopReason;
import com.anthropic.models.beta.messages.BetaTextBlock;
import com.anthropic.models.beta.messages.MessageCreateParams;
import org.mockito.ArgumentCaptor;
import com.anthropic.models.messages.Model;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Claude 응답 해석과 실패 변환. 실제 API는 부르지 않고 SDK 클라이언트를 mock한다. */
class AnthropicLlmClientTest {

    private static final LlmProperties PROPERTIES =
            new LlmProperties("test-key", "claude-sonnet-5-5", "low", Duration.ofSeconds(10), 1, 4096);
    private static final ExplanationContext CONTEXT = new ExplanationContext(
            "임차인 [이름]은 보증금 반환을 청구할 수 없다.", "HIGH", "RISK_CANDIDATE",
            List.of(new EvidenceHit(1, 11, "LAW", "주택임대차보호법", "제3조", "보증금 반환 근거", "", 0.8)));

    private final AnthropicClient sdk = mock(AnthropicClient.class, RETURNS_DEEP_STUBS);
    private final AnthropicLlmClient client = new AnthropicLlmClient(sdk, PROPERTIES);

    @Test
    @DisplayName("FR-EXP-01·03 구조화 응답의 설명·수정 문구와 응답 모델·프롬프트 버전으로 초안을 만든다")
    void returnsDraftFromStructuredResponse() {
        respond(BetaStopReason.END_TURN, "{\"text\":\"  쉬운 설명  \",\"suggestion\":\"수정 문구\"}");

        ExplanationDraft draft = client.explain(CONTEXT);

        assertThat(draft).isEqualTo(new ExplanationDraft("쉬운 설명", "수정 문구", "claude-sonnet-5-5", "exp-v1"));
    }

    @Test
    @DisplayName("FR-EXP-01 거절·토큰 초과·빈 문구 응답은 저장하지 않도록 503 LLM_UNAVAILABLE로 바꾼다")
    void rejectsUnusableResponses() {
        respond(BetaStopReason.REFUSAL, "{\"text\":\"설명\",\"suggestion\":\"제안\"}");
        assertThatThrownBy(() -> client.explain(CONTEXT)).isExactlyInstanceOf(LlmException.class);

        respond(BetaStopReason.MAX_TOKENS, "{\"text\":\"설명\",\"suggestion\":\"제안\"}");
        assertThatThrownBy(() -> client.explain(CONTEXT)).isExactlyInstanceOf(LlmException.class);

        respond(BetaStopReason.END_TURN, "{\"text\":\"설명\",\"suggestion\":\" \"}");
        assertThatThrownBy(() -> client.explain(CONTEXT))
                .isInstanceOfSatisfying(LlmException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(503);
                    assertThat(exception.code()).isEqualTo("LLM_UNAVAILABLE");
                });
    }

    @Test
    @DisplayName("FR-EXP-01 설정한 모델·effort·JSON 스키마·대체 모델 베타로 요청한다")
    void sendsConfiguredRequest() {
        respond(BetaStopReason.END_TURN, "{\"text\":\"설명\",\"suggestion\":\"제안\"}");

        client.explain(CONTEXT);

        ArgumentCaptor<MessageCreateParams> params = ArgumentCaptor.forClass(MessageCreateParams.class);
        org.mockito.Mockito.verify(sdk.beta().messages()).create(params.capture());
        MessageCreateParams sent = params.getValue();
        assertThat(sent.model().asString()).isEqualTo("claude-sonnet-5-5");
        assertThat(sent.maxTokens()).isEqualTo(4096);
        assertThat(sent.system().orElseThrow().string()).contains(AnthropicLlmClient.SYSTEM_PROMPT);
        assertThat(sent.outputConfig().orElseThrow().effort().orElseThrow().asString()).isEqualTo("low");
        assertThat(sent.outputConfig().orElseThrow().format()).contains(AnthropicLlmClient.OUTPUT_FORMAT);
        assertThat(sent.betas().orElseThrow()).extracting(Object::toString).contains(AnthropicLlmClient.FALLBACK_BETA);
    }

    @Test
    @DisplayName("FR-EXP-01 JSON이 아닌 응답은 503 LLM_UNAVAILABLE이다")
    void rejectsNonJsonResponse() {
        respond(BetaStopReason.END_TURN, "설명입니다");

        assertThatThrownBy(() -> client.explain(CONTEXT)).isExactlyInstanceOf(LlmException.class);
    }

    @Test
    @DisplayName("TC-EXP-013 소켓 시간 초과는 504 LLM_TIMEOUT, 그 밖의 연결 실패는 503 LLM_UNAVAILABLE이다")
    void mapsTimeoutAndIoFailures() {
        when(sdk.beta().messages().create(any(MessageCreateParams.class)))
                .thenThrow(new AnthropicIoException("timeout", new SocketTimeoutException("read timed out")));
        assertThatThrownBy(() -> client.explain(CONTEXT))
                .isInstanceOfSatisfying(LlmTimeoutException.class,
                        exception -> assertThat(exception.code()).isEqualTo("LLM_TIMEOUT"));

        when(sdk.beta().messages().create(any(MessageCreateParams.class)))
                .thenThrow(new AnthropicIoException("connection reset"));
        assertThatThrownBy(() -> client.explain(CONTEXT)).isExactlyInstanceOf(LlmException.class);
    }

    @Test
    @DisplayName("FR-EXP-02 프롬프트에는 가린 조항을 자료 태그로, 근거는 제목·조문과 함께 넣는다")
    void buildsPromptWithClauseAndEvidence() {
        String prompt = AnthropicLlmClient.userPrompt(CONTEXT);

        assertThat(prompt)
                .contains("위험 등급: HIGH (유형: RISK_CANDIDATE)")
                .contains("<clause>\n임차인 [이름]은 보증금 반환을 청구할 수 없다.\n</clause>")
                .contains("<source type=\"LAW\" title=\"주택임대차보호법\" ref=\"제3조\">\n보증금 반환 근거\n</source>");
        assertThat(AnthropicLlmClient.userPrompt(new ExplanationContext("조항", "", "", List.of())))
                .doesNotContain("위험 등급")
                .contains("관련 근거를 찾지 못했습니다");
    }

    private void respond(BetaStopReason stopReason, String json) {
        BetaMessage message = mock(BetaMessage.class);
        BetaContentBlock block = mock(BetaContentBlock.class);
        BetaTextBlock text = mock(BetaTextBlock.class);
        when(text.text()).thenReturn(json);
        when(block.text()).thenReturn(Optional.of(text));
        when(message.content()).thenReturn(List.of(block));
        when(message.stopReason()).thenReturn(Optional.of(stopReason));
        when(message.model()).thenReturn(Model.of("claude-sonnet-5-5"));
        when(sdk.beta().messages().create(any(MessageCreateParams.class))).thenReturn(message);
    }
}
