package ac.mju.contractcheck.global.interceptor;

import ac.mju.contractcheck.service.PiiMaskingService;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 외부 호출의 요청·응답 본문을 로그에 남길 수 있는 문자열로 바꾼다.
 *
 * <ul>
 *   <li>키·토큰·결제 키 필드는 값을 지우고, 이름·이메일·계좌처럼 필드 이름으로 알 수 있는 개인정보도 지운다.</li>
 *   <li>나머지 문자열은 {@link PiiMaskingService}로 가린다. 마스킹에 실패하면 원문 대신 실패 표시를 남긴다.</li>
 *   <li>URL은 호스트까지만, 숫자 배열(임베딩 벡터 등)과 긴 인코딩 문자열, 파일 본문은 크기만 남긴다.</li>
 * </ul>
 *
 * 한계: 마스킹은 정규식 규칙이라 놓치는 값이 있다(호칭 뒤에 다른 항목이 이어지는 이름 등).
 */
@RequiredArgsConstructor
public class ExternalBodySanitizer {

    static final int MAX_LENGTH = 100_000;
    private static final int MAX_NUMBERS = 8;
    private static final String REMOVED = "[제거]";
    private static final String PERSONAL = "[개인정보]";
    private static final String MASKING_FAILED = "[마스킹 실패]";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Pattern ENCODED = Pattern.compile("[A-Za-z0-9+/=_\\-]{500,}");
    private static final Pattern URL = Pattern.compile("^(https?://)(?:[^/@\\s]*@)?([^/?#\\s]+)[/?#]\\S*$");
    /** 필드 이름은 소문자로, 밑줄·붙임표 없이 비교한다. */
    private static final Set<String> SECRET_FIELDS = Set.of(
            "authorization", "apikey", "xinternalapikey", "secret", "secretkey", "clientsecret", "password",
            "token", "accesstoken", "refreshtoken", "idtoken", "paymentkey", "billingkey",
            "transactionkey", "lasttransactionkey");
    private static final Set<String> PERSONAL_FIELDS = Set.of(
            "customername", "customeremail", "customermobilephone", "email", "accountnumber", "depositorname",
            "holdername", "number", "mobilephone", "phonenumber");

    @NonNull
    private final PiiMaskingService masking;

    public String sanitize(byte[] body, String contentType) {
        if (body == null || body.length == 0) {
            return "";
        }
        String type = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        if (!type.isEmpty() && !type.contains("json") && !type.startsWith("text/")) {
            return "(본문 생략 " + body.length + " bytes)";
        }
        String text = new String(body, StandardCharsets.UTF_8);
        try {
            return clip(JSON.writeValueAsString(clean(JSON.readTree(text))));
        } catch (JacksonException exception) {
            return clip(mask(text).replace("\r", "\\r").replace("\n", "\\n"));
        }
    }

    private JsonNode clean(JsonNode node) {
        if (node instanceof ObjectNode object) {
            ObjectNode result = JSON.createObjectNode();
            object.properties().forEach(entry -> {
                String name = entry.getKey().toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
                JsonNode value = entry.getValue();
                if (value.isNull() || value.isContainer()) {
                    result.set(entry.getKey(), clean(value));
                } else if (SECRET_FIELDS.contains(name)) {
                    result.put(entry.getKey(), REMOVED);
                } else if (PERSONAL_FIELDS.contains(name)) {
                    result.put(entry.getKey(), PERSONAL);
                } else {
                    result.set(entry.getKey(), clean(value));
                }
            });
            return result;
        }
        if (node instanceof ArrayNode array) {
            if (array.size() > MAX_NUMBERS && array.valueStream().allMatch(JsonNode::isNumber)) {
                return JSON.getNodeFactory().stringNode("(숫자 " + array.size() + "개)");
            }
            ArrayNode result = JSON.createArrayNode();
            array.forEach(element -> result.add(clean(element)));
            return result;
        }
        if (node.isString()) {
            return JSON.getNodeFactory().stringNode(cleanString(node.asString()));
        }
        return node;
    }

    private String cleanString(String value) {
        if (ENCODED.matcher(value).matches()) {
            return "(문자열 " + value.length() + "자)";
        }
        var url = URL.matcher(value);
        if (url.matches()) {
            return url.group(1) + url.group(2) + "/…";
        }
        return mask(value);
    }

    private String mask(String value) {
        try {
            return masking.mask(value);
        } catch (RuntimeException exception) {
            return MASKING_FAILED;
        }
    }

    private static String clip(String value) {
        return value.length() <= MAX_LENGTH ? value : value.substring(0, MAX_LENGTH) + "…(총 " + value.length() + "자)";
    }
}
