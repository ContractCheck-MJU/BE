package ac.mju.contractcheck.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import ac.mju.contractcheck.config.InferenceProperties;
import ac.mju.contractcheck.domain.ClauseClassification;
import ac.mju.contractcheck.domain.ClauseText;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** 실제 추론 서버를 부르지 않고 MockRestServiceServer로 스펙시트 /classify·/embed 계약을 확인한다. */
class HttpInferenceClientTest {

    private static final String BASE = "http://inference.test";

    private MockRestServiceServer server;
    private HttpInferenceClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        InferenceProperties properties = new InferenceProperties(BASE, "test-key", Duration.ofSeconds(1), 3, Duration.ZERO, 64);
        client = new HttpInferenceClient(properties, builder.build());
    }

    @Test
    @DisplayName("분류: 위험 조항은 해당 유형의 확률을, 정상 조항은 위험 아님을 입력 순서대로 돌려주고 키 헤더를 보낸다")
    void classifyMapsRiskyAndSafeInOrder() {
        server.expect(requestTo(BASE + "/classify"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Api-Key", "test-key"))
                .andExpect(jsonPath("$.clauses[0].id").value("11"))
                .andExpect(jsonPath("$.clauses[1].text").value("정상 조항"))
                .andRespond(withSuccess("""
                        {"results":[
                          {"id":"12","isRisky":false,"riskType":null,"probs":{"정상":0.97},"modelVersion":"m-1"},
                          {"id":"11","isRisky":true,"riskType":"면책","probs":{"면책":0.95,"정상":0.05},"modelVersion":"m-1"}]}
                        """, MediaType.APPLICATION_JSON));

        List<ClauseClassification> results = client.classify(
                List.of(new ClauseText("11", "위험 조항"), new ClauseText("12", "정상 조항")));

        assertThat(results).extracting(ClauseClassification::clauseId).containsExactly("11", "12");
        assertThat(results.get(0).classification().risky()).isTrue();
        assertThat(results.get(0).classification().probability()).isEqualTo(0.95);
        assertThat(results.get(0).riskType()).isEqualTo("면책");
        assertThat(results.get(0).modelVersion()).isEqualTo("m-1");
        assertThat(results.get(1).classification().risky()).isFalse();
        assertThat(results.get(1).riskType()).isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("분류: 64개를 넘으면 배치로 나눠 보내고 결과를 이어 붙인다(단건 루프 없음)")
    void classifyIsBatched() {
        List<ClauseText> clauses = IntStream.range(0, 130).mapToObj(i -> new ClauseText("c" + i, "조항 " + i)).toList();
        for (int from = 0; from < 130; from += 64) {
            int to = Math.min(130, from + 64);
            server.expect(requestTo(BASE + "/classify"))
                    .andExpect(jsonPath("$.clauses.length()").value(to - from))
                    .andRespond(withSuccess(classifyBody(from, to), MediaType.APPLICATION_JSON));
        }

        List<ClauseClassification> results = client.classify(clauses);

        assertThat(results).hasSize(130);
        assertThat(results.get(129).clauseId()).isEqualTo("c129");
        server.verify();
    }

    @Test
    @DisplayName("TC-ANL-016: 서버가 5xx를 주면 재시도하고, 끝내 실패하면 InferenceException이다")
    void serverErrorsAreRetriedThenFail() {
        server.expect(requestTo(BASE + "/classify")).andRespond(withServerError());
        server.expect(requestTo(BASE + "/classify")).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(requestTo(BASE + "/classify")).andRespond(withServerError());

        assertThatThrownBy(() -> client.classify(List.of(new ClauseText("1", "조항"))))
                .isInstanceOf(InferenceException.class)
                .hasMessageContaining("재시도 소진");
        server.verify();
    }

    @Test
    @DisplayName("일시적 5xx 뒤 성공하면 결과를 돌려준다")
    void transientFailureRecovers() {
        server.expect(requestTo(BASE + "/classify")).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(requestTo(BASE + "/classify"))
                .andRespond(withSuccess(classifyBody(0, 1), MediaType.APPLICATION_JSON));

        assertThat(client.classify(List.of(new ClauseText("c0", "조항")))).hasSize(1);
        server.verify();
    }

    @Test
    @DisplayName("4xx(키 오류 등)는 재시도하지 않고 바로 실패한다")
    void clientErrorsAreNotRetried() {
        server.expect(requestTo(BASE + "/classify")).andRespond(withStatus(HttpStatus.FORBIDDEN)
                .contentType(MediaType.APPLICATION_JSON).body("{\"detail\":{\"code\":\"API_KEY_INVALID\"}}"));

        assertThatThrownBy(() -> client.classify(List.of(new ClauseText("1", "조항"))))
                .isInstanceOf(InferenceException.class)
                .hasMessageContaining("403");
        server.verify();
    }

    @Test
    @DisplayName("분류 응답의 개수·id가 요청과 다르거나 위험 확률이 없으면 실패한다")
    void malformedClassifyResponsesAreRejected() {
        server.expect(requestTo(BASE + "/classify")).andRespond(withSuccess(
                "{\"results\":[]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/classify")).andRespond(withSuccess(
                "{\"results\":[{\"id\":\"other\",\"isRisky\":false,\"probs\":{}}]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/classify")).andRespond(withSuccess(
                "{\"results\":[{\"id\":\"1\",\"isRisky\":true,\"riskType\":\"면책\",\"probs\":{}}]}", MediaType.APPLICATION_JSON));

        List<ClauseText> request = List.of(new ClauseText("1", "조항"));
        assertThatThrownBy(() -> client.classify(request)).isInstanceOf(InferenceException.class).hasMessageContaining("개수");
        assertThatThrownBy(() -> client.classify(request)).isInstanceOf(InferenceException.class).hasMessageContaining("요청한 조항이 없습니다");
        assertThatThrownBy(() -> client.classify(request)).isInstanceOf(InferenceException.class).hasMessageContaining("확률");
        server.verify();
    }

    @Test
    @DisplayName("임베딩: 1024차원 정규화 벡터를 입력 순서대로 받는다")
    void embedReturnsVectors() {
        server.expect(requestTo(BASE + "/embed"))
                .andExpect(header("X-Internal-Api-Key", "test-key"))
                .andExpect(jsonPath("$.texts.length()").value(2))
                .andExpect(jsonPath("$.normalize").value(true))
                .andRespond(withSuccess("{\"vectors\":[" + vector(1024, 0.5f) + "," + vector(1024, 0.25f) + "]}",
                        MediaType.APPLICATION_JSON));

        List<float[]> vectors = client.embed(List.of("조항 가", "조항 나"));

        assertThat(vectors).hasSize(2);
        assertThat(vectors.get(0)).hasSize(1024).startsWith(0.5f);
        assertThat(vectors.get(1)[0]).isEqualTo(0.25f);
        server.verify();
    }

    @Test
    @DisplayName("임베딩: 차원이 1024가 아니거나 개수가 다르면 실패한다")
    void embedRejectsWrongDimensionOrCount() {
        server.expect(requestTo(BASE + "/embed"))
                .andRespond(withSuccess("{\"vectors\":[" + vector(768, 0.1f) + "]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/embed"))
                .andRespond(withSuccess("{\"vectors\":[]}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.embed(List.of("조항"))).isInstanceOf(InferenceException.class).hasMessageContaining("차원");
        assertThatThrownBy(() -> client.embed(List.of("조항"))).isInstanceOf(InferenceException.class).hasMessageContaining("개수");
        server.verify();
    }

    @Test
    @DisplayName("빈 입력이면 추론 서버를 부르지 않는다")
    void emptyInputMakesNoRequest() {
        assertThat(client.classify(List.of())).isEmpty();
        assertThat(client.embed(List.of())).isEmpty();
        server.verify();
    }

    private static String classifyBody(int from, int to) {
        return "{\"results\":[" + IntStream.range(from, to)
                .mapToObj(i -> "{\"id\":\"c" + i + "\",\"isRisky\":false,\"riskType\":null,\"probs\":{\"정상\":0.9},\"modelVersion\":\"m\"}")
                .collect(Collectors.joining(",")) + "]}";
    }

    private static String vector(int dimension, float first) {
        List<String> values = new ArrayList<>();
        for (int i = 0; i < dimension; i++) {
            values.add(i == 0 ? Float.toString(first) : "0.0");
        }
        return "[" + String.join(",", values) + "]";
    }
}
