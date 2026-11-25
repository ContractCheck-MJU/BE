package ac.mju.contractcheck.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import ac.mju.contractcheck.config.UpstageProperties;
import ac.mju.contractcheck.domain.OcrResult;
import ac.mju.contractcheck.domain.enums.ContractFileType;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Upstage Document Parse 호출 계약(URL·인증·폼 필드)과 응답·오류 변환. 실제 API는 부르지 않고 MockRestServiceServer로 흉내 낸다. */
class UpstageOcrClientTest {

    private static final String API_KEY = "up_unitTestKey";
    private static final String BASE_URL = "https://api.upstage.test";
    private static final String URL = BASE_URL + "/v1/document-digitization";
    private static final byte[] PDF = "%PDF-1.7 fake".getBytes(StandardCharsets.US_ASCII);
    private static final String SUCCESS_BODY = """
            {"api":"2.0","content":{"html":"","markdown":"","text":"제1조 (목적)\\n임대차 계약을 체결한다.\\n제2조 (보증금)"},
             "elements":[],"model":"document-parse-260930","usage":{"pages":2}}
            """;

    private MockRestServiceServer server;
    private UpstageOcrClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new UpstageOcrClient(new UpstageProperties(BASE_URL, API_KEY, "document-parse"), builder.build());
    }

    @Test
    @DisplayName("FR-UPL-02 Bearer 키와 multipart 폼(파일·모델·text 출력)으로 부르고 본문 텍스트와 쪽 수를 돌려준다")
    void extractSendsContractAndMapsResponse() {
        StringBuilder sent = new StringBuilder();
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + API_KEY))
                .andExpect(request -> {
                    assertThat(request.getHeaders().getContentType().isCompatibleWith(MediaType.MULTIPART_FORM_DATA)).isTrue();
                    sent.append(((MockClientHttpRequest) request).getBodyAsString(StandardCharsets.UTF_8));
                })
                .andRespond(withSuccess(SUCCESS_BODY, MediaType.APPLICATION_JSON));

        OcrResult result = client.extract(PDF, ContractFileType.PDF);

        assertThat(result.text()).isEqualTo("제1조 (목적)\n임대차 계약을 체결한다.\n제2조 (보증금)");
        assertThat(result.pageCount()).isEqualTo(2);
        assertThat(sent.toString())
                .contains("name=\"document\"; filename=\"contract.pdf\"")
                .contains("Content-Type: application/pdf")
                .contains("%PDF-1.7 fake")
                .containsPattern("name=\"model\"[\\s\\S]*?\r\n\r\ndocument-parse\r\n")
                .containsPattern("name=\"mode\"[\\s\\S]*?\r\n\r\nstandard\r\n")
                .containsPattern("name=\"ocr\"[\\s\\S]*?\r\n\r\nauto\r\n")
                .containsPattern("name=\"output_formats\"[\\s\\S]*?\r\n\r\n\\['text'\\]\r\n")
                .containsPattern("name=\"coordinates\"[\\s\\S]*?\r\n\r\nfalse\r\n");
        server.verify();
    }

    @ParameterizedTest
    @CsvSource({"JPEG, contract.jpg, image/jpeg", "PNG, contract.png, image/png"})
    @DisplayName("FR-UPL-02 이미지는 형식에 맞는 파일 이름과 Content-Type으로 보낸다")
    void imageUsesMatchingFileName(ContractFileType type, String fileName, String contentType) {
        server.expect(requestTo(URL))
                .andExpect(request -> assertThat(((MockClientHttpRequest) request).getBodyAsString(StandardCharsets.UTF_8))
                        .contains("filename=\"" + fileName + "\"")
                        .contains("Content-Type: " + contentType))
                .andRespond(withSuccess(SUCCESS_BODY, MediaType.APPLICATION_JSON));

        client.extract(new byte[] {1, 2, 3}, type);

        server.verify();
    }

    @Test
    @DisplayName("FR-UPL-02 글자가 없으면 예외가 아니라 빈 텍스트다")
    void blankTextIsEmptyResult() {
        server.expect(requestTo(URL)).andRespond(withSuccess(
                "{\"content\":{\"text\":\"\"},\"usage\":{\"pages\":1}}", MediaType.APPLICATION_JSON));

        OcrResult result = client.extract(PDF, ContractFileType.PDF);

        assertThat(result.empty()).isTrue();
        assertThat(result.pageCount()).isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource({"401, invalid_api_key", "403, api_key_is_not_allowed", "413, payload_too_large",
            "429, too_many_requests", "500, internal_server_error"})
    @DisplayName("FR-UPL-02 오류 응답은 상태와 오류 코드만 담은 OcrException이고 키·응답 본문은 담지 않는다")
    void errorStatusBecomesOcrException(int status, String code) {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.valueOf(status)).contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":{\"message\":\"provider detail\",\"code\":\"" + code + "\"}}"));

        assertThatThrownBy(() -> client.extract(PDF, ContractFileType.PDF))
                .isExactlyInstanceOf(OcrException.class)
                .hasMessageContaining(String.valueOf(status))
                .hasMessageContaining(code)
                .message().doesNotContain(API_KEY, "provider detail");
    }

    @ParameterizedTest
    @ValueSource(strings = {"<html>bad gateway</html>", "{\"usage\":{\"pages\":1}}", "{\"content\":{\"html\":\"<p/>\"}}", ""})
    @DisplayName("FR-UPL-02 본문 텍스트를 읽을 수 없는 성공 응답은 빈 결과가 아니라 실패다")
    void unreadableSuccessResponseFails(String body) {
        server.expect(requestTo(URL)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.extract(PDF, ContractFileType.PDF)).isExactlyInstanceOf(OcrException.class);
    }

    @Test
    @DisplayName("FR-UPL-02 응답 시간 초과는 OcrTimeoutException이다")
    void timeoutBecomesOcrTimeout() {
        server.expect(requestTo(URL)).andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertThatThrownBy(() -> client.extract(PDF, ContractFileType.PDF)).isInstanceOf(OcrTimeoutException.class);
    }

    @Test
    @DisplayName("FR-UPL-02 연결 실패는 시간 초과가 아닌 OcrException이다")
    void connectionFailureBecomesOcrException() {
        server.expect(requestTo(URL)).andRespond(withException(new IOException("Connection refused")));

        assertThatThrownBy(() -> client.extract(PDF, ContractFileType.PDF)).isExactlyInstanceOf(OcrException.class);
    }
}
