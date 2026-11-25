package ac.mju.contractcheck.global.interceptor;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.regex.Pattern;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * prod용 비식별 본문. 개인정보를 찾아 가리는 대신 <b>남겨도 되는 값만 남긴다</b>. 정규식이 놓치는 값이 있어도 새지 않는다.
 *
 * <ul>
 *   <li>JSON의 구조와 필드 이름은 남긴다. 이름이 식별자 모양이 아니면 가린다.</li>
 *   <li>문자열은 상태·코드·모델처럼 정해 둔 필드이고 값이 짧은 영문 기호일 때만 남기고, 나머지는 길이만 남긴다.</li>
 *   <li>숫자는 쪽 수·금액·토큰 수처럼 정해 둔 필드만 남긴다. 참·거짓과 null은 남긴다.</li>
 *   <li>JSON이 아니면 크기만 남긴다.</li>
 * </ul>
 * 새 필드를 남기려면 개인정보나 비밀값이 들어갈 수 없는지 확인하고 목록에 더한다.
 */
public class ExternalBodyDeidentifier implements ExternalBodyView {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Set<String> TEXT_FIELDS = Set.of(
            "api", "model", "object", "type", "role", "status", "code", "category", "currency", "version",
            "stop_reason", "stopReason", "finish_reason", "approvedAt", "requestedAt");
    private static final Set<String> NUMBER_FIELDS = Set.of(
            "status", "page", "pages", "standard", "enhanced", "amount", "totalAmount", "balanceAmount",
            "input_tokens", "output_tokens", "count");
    /** 한글·공백이 없는 짧은 기호. 문장이나 이름이 이 모양을 벗어나게 한다. */
    private static final Pattern SYMBOL = Pattern.compile("[A-Za-z0-9_.:+\\-]{0,64}");
    private static final Pattern FIELD_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_\\-]{0,39}");
    private static final String HIDDEN_NAME = "(필드)";

    @Override
    public String describe(byte[] body, String contentType) {
        if (body == null || body.length == 0) {
            return "";
        }
        try {
            JsonNode root = JSON.readTree(new String(body, StandardCharsets.UTF_8));
            if (root.isContainer()) {
                String result = JSON.writeValueAsString(hide("", root));
                return result.length() <= ExternalBodySanitizer.MAX_LENGTH
                        ? result
                        : result.substring(0, ExternalBodySanitizer.MAX_LENGTH) + "…(총 " + result.length() + "자)";
            }
        } catch (JacksonException exception) {
            // JSON이 아니면 아래에서 크기만 남긴다. 파서 메시지에는 본문 일부가 담길 수 있어 쓰지 않는다.
        }
        return "(본문 생략 " + body.length + " bytes)";
    }

    private JsonNode hide(String field, JsonNode node) {
        if (node instanceof ObjectNode object) {
            ObjectNode result = JSON.createObjectNode();
            object.properties().forEach(entry -> {
                String name = FIELD_NAME.matcher(entry.getKey()).matches() ? entry.getKey() : HIDDEN_NAME;
                result.set(name, hide(name, entry.getValue()));
            });
            return result;
        }
        if (node instanceof ArrayNode array) {
            if (!array.isEmpty() && array.valueStream().allMatch(JsonNode::isNumber)) {
                return text("(숫자 " + array.size() + "개)");
            }
            ArrayNode result = JSON.createArrayNode();
            array.forEach(element -> result.add(hide(field, element)));
            return result;
        }
        if (node.isString()) {
            String value = node.asString();
            boolean keep = TEXT_FIELDS.contains(field) && SYMBOL.matcher(value).matches();
            return keep ? node : text("(문자열 " + value.length() + "자)");
        }
        if (node.isNumber()) {
            return NUMBER_FIELDS.contains(field) ? node : text("(숫자)");
        }
        return node;
    }

    private static JsonNode text(String value) {
        return JSON.getNodeFactory().stringNode(value);
    }
}
