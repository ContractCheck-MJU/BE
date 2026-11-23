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
        mvc.perform(get("/fail"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("INFERENCE_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("분석 서버 점검 중입니다. 잠시 후 다시 시도해 주세요."))
                .andExpect(jsonPath("$.traceId").exists());
    }

    @RestController
    static class FailingController {

        @GetMapping("/fail")
        String fail() {
            throw new InferenceException("http://10.0.0.5:8000 secret-key 조항 본문");
        }
    }
}
