package ac.mju.contractcheck.client;

import ac.mju.contractcheck.config.UpstageProperties;
import ac.mju.contractcheck.domain.OcrResult;
import ac.mju.contractcheck.domain.enums.ContractFileType;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Upstage Document Parse 동기 API({@code POST /v1/document-digitization}) 구현체(FR-UPL-02).
 * {@code contractcheck.ocr.provider=upstage}이고 API 키가 있을 때만 빈으로 등록된다.
 *
 * <p><b>원본 파일을 마스킹하지 않고 그대로 보낸다.</b> 파일 이름은 사용자가 올린 이름 대신 {@code contract.<확장자>}를 쓴다.
 * 이 클래스의 로그에는 쪽 수·글자 수만 남긴다. 호출 기록(가린 본문 포함)은 RestClient에 붙은 공통 인터셉터가 남긴다.
 *
 * <p>동기 API는 100쪽까지라 넘으면 실패한다. 스펙시트 '외부 연동'의 "비동기 폴링"(비동기 API)은 쓰지 않는다.
 * 재시도는 호출하는 쪽(ContractProcessor)이 한다.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "contractcheck.ocr", name = "provider", havingValue = "upstage")
@ConditionalOnExpression("'${contractcheck.ocr.upstage.api-key:}'.length() > 0")
public class UpstageOcrClient implements OcrClient {

    private static final String PARSE_PATH = "/v1/document-digitization";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final RestClient restClient;
    private final String authorization;
    private final String model;

    public UpstageOcrClient(@NonNull UpstageProperties properties, @NonNull RestClient upstageRestClient) {
        this.restClient = upstageRestClient;
        this.authorization = "Bearer " + properties.apiKey();
        this.model = properties.model();
    }

    @Override
    public OcrResult extract(byte[] content, ContractFileType type) {
        Response response;
        try {
            response = restClient.post()
                    .uri(PARSE_PATH)
                    .header(HttpHeaders.AUTHORIZATION, authorization)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(form(content, type))
                    .exchange((request, result) -> new Response(result.getStatusCode().value(), result.bodyTo(byte[].class)));
        } catch (ResourceAccessException exception) {
            throw translate(exception);
        } catch (RestClientException exception) {
            throw new OcrException("Upstage OCR 호출에 실패했습니다.", exception);
        }
        if (response.status() / 100 != 2) {
            throw new OcrException("Upstage OCR이 오류를 돌려줬습니다. status=" + response.status() + " code=" + errorCode(response.body()));
        }
        OcrResult result = parse(response.body());
        log.info("OCR 완료 provider=upstage pages={} chars={}", result.pageCount(), result.text().length());
        return result;
    }

    private MultiValueMap<String, Object> form(byte[] content, ContractFileType type) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(MediaType.parseMediaType(type.contentType()));
        form.add("document", new HttpEntity<>(namedResource(content, fileName(type)), fileHeaders));
        form.add("model", model);
        form.add("mode", "standard"); // 쪽당 단가가 낮은 모드. enhanced·auto는 더 비싸다.
        form.add("ocr", "auto"); // 글자 층이 있는 PDF는 그대로 읽고, 이미지는 인식한다.
        form.add("output_formats", "['text']"); // 기본 출력은 HTML뿐이라 지정해야 text가 채워진다.
        form.add("coordinates", "false");
        return form;
    }

    /** multipart 파일 파트에는 파일 이름이 있어야 한다. */
    private static ByteArrayResource namedResource(byte[] content, String fileName) {
        return new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return fileName;
            }
        };
    }

    private static String fileName(ContractFileType type) {
        return switch (type) {
            case PDF -> "contract.pdf";
            case JPEG -> "contract.jpg";
            case PNG -> "contract.png";
        };
    }

    private static OcrException translate(ResourceAccessException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof SocketTimeoutException || cause instanceof HttpTimeoutException) {
                return new OcrTimeoutException("Upstage OCR 응답을 시간 안에 받지 못했습니다.");
            }
        }
        return new OcrException("Upstage OCR에 연결하지 못했습니다.", exception);
    }

    /** 오류 본문의 {@code error.code}. 읽을 수 없으면 빈 문자열이다. 메시지는 파일 내용이 섞일 수 있어 쓰지 않는다. */
    private static String errorCode(byte[] body) {
        if (body == null) {
            return "";
        }
        try {
            return JSON.readTree(body).path("error").path("code").asString("");
        } catch (JacksonException exception) {
            return "";
        }
    }

    /** {@code content.text}가 없는 응답은 글자 없는 문서가 아니라 실패다. 파서 예외에는 본문 일부가 담길 수 있어 원인으로 넘기지 않는다. */
    private static OcrResult parse(byte[] body) {
        if (body == null) {
            throw new OcrException("Upstage OCR 응답이 비어 있습니다.");
        }
        try {
            JsonNode root = JSON.readTree(body);
            JsonNode text = root.path("content").path("text");
            if (!text.isString()) {
                throw new OcrException("Upstage OCR 응답에 본문 텍스트가 없습니다.");
            }
            return new OcrResult(text.asString(), root.path("usage").path("pages").asInt(1));
        } catch (JacksonException exception) {
            throw new OcrException("Upstage OCR 응답을 읽지 못했습니다.");
        }
    }

    private record Response(int status, byte[] body) {}
}
