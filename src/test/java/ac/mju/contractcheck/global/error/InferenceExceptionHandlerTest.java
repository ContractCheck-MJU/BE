package ac.mju.contractcheck.global.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ac.mju.contractcheck.client.InferenceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class InferenceExceptionHandlerTest {

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new FailingController())
            .setControllerAdvice(new ApiExceptionHandler())
            .build();

    @Test
    @DisplayName("추론 서버 장애는 503 INFERENCE_UNAVAILABLE이고 내부 원인(주소·키·본문)은 응답에 없다")
    void inferenceFailureIsServiceUnavailable() throws Exception {
        mvc.perform(get("/inference"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("INFERENCE_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("분석 서버 점검 중입니다. 잠시 후 다시 시도해 주세요."))
                .andExpect(jsonPath("$.traceId").exists());
    }

    @Test
    @DisplayName("새 외부 연동 예외는 ApiExceptionHandler를 고치지 않아도 자기 상태 코드·오류 코드로 응답한다")
    void newExternalServiceExceptionNeedsNoHandlerChange() throws Exception {
        mvc.perform(get("/llm"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("LLM_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("설명 생성 서버에 문제가 있습니다."));
    }

    @RestController
    static class FailingController {

        @GetMapping("/inference")
        String inference() {
            throw new InferenceException("http://10.0.0.5:8000 secret-key 조항 본문");
        }

        @GetMapping("/llm")
        String llm() {
            throw new FakeLlmException();
        }
    }

    /** 앞으로 Claude 연동이 추가될 때의 모습(스펙: 502 LLM_UNAVAILABLE). 핸들러에는 이 클래스가 등장하지 않는다. */
    static class FakeLlmException extends ExternalServiceException {

        FakeLlmException() {
            super("내부 원인 api.anthropic.com 429", null);
        }

        @Override
        public int status() {
            return 502;
        }

        @Override
        public String code() {
            return "LLM_UNAVAILABLE";
        }

        @Override
        public String publicMessage() {
            return "설명 생성 서버에 문제가 있습니다.";
        }
    }
}
