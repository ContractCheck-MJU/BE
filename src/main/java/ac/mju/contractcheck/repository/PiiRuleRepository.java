package ac.mju.contractcheck.repository;

import ac.mju.contractcheck.domain.PiiRule;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.YamlMapFactoryBean;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Repository;

/**
 * 개인정보 마스킹 규칙(정규식)을 파일에서 읽어 {@link PiiRule}로 바꾼다(FR-UPL-04). 규칙은 DB가 아니라
 * {@code masking/pii-rules.yml} 한 파일에서만 관리하고, {@code contractcheck.masking.rules-location}으로 다른 파일을 가리킬 수 있다.
 * 어떤 규칙을 어떤 순서로 적용할지는 service가 정하고, 이 클래스는 읽고 검증하고 변환하는 일만 한다.
 *
 * <p>앱 시작 때 한 번 읽는다. 정규식이 틀렸거나 항목이 빠졌거나 id가 겹치면 시작이 실패한다. 마스킹 규칙이 조용히 빠진 채
 * 외부로 전송되는 일을 막기 위해서다.
 */
@Slf4j
@Repository
public class PiiRuleRepository {

    private final List<PiiRule> rules;

    @Autowired
    public PiiRuleRepository(
            @NonNull ResourceLoader resourceLoader,
            @Value("${contractcheck.masking.rules-location:classpath:masking/pii-rules.yml}") @NonNull String location) {
        this.rules = load(resourceLoader.getResource(location), location);
        log.info("마스킹 규칙 {}건을 읽었습니다. location={} ids={}", rules.size(), location, rules.stream().map(PiiRule::id).toList());
    }

    private PiiRuleRepository(List<PiiRule> rules) {
        this.rules = List.copyOf(rules);
    }

    /** 파일 없이 규칙을 직접 넘겨 만든다. 테스트에서 특정 규칙만 확인할 때 쓴다. */
    public static PiiRuleRepository of(@NonNull List<PiiRule> rules) {
        return new PiiRuleRepository(rules);
    }

    /** 파일에 적힌 순서 그대로의 규칙. */
    public @NonNull List<PiiRule> findAll() {
        return rules;
    }

    private static List<PiiRule> load(Resource resource, String location) {
        if (!resource.exists()) {
            throw new IllegalStateException("마스킹 규칙 파일이 없습니다. location=" + location);
        }
        Map<String, Object> root = readYaml(resource, location);
        if (!(root.get("rules") instanceof List<?> items) || items.isEmpty()) {
            throw new IllegalStateException("마스킹 규칙이 하나도 없습니다. 'rules' 목록이 필요합니다. location=" + location);
        }
        List<PiiRule> loaded = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (int index = 0; index < items.size(); index++) {
            PiiRule rule = toRule(items.get(index), index, location);
            if (!ids.add(rule.id())) {
                throw new IllegalStateException("마스킹 규칙 id가 겹칩니다: " + rule.id());
            }
            loaded.add(rule);
        }
        return List.copyOf(loaded);
    }

    private static Map<String, Object> readYaml(Resource resource, String location) {
        YamlMapFactoryBean yaml = new YamlMapFactoryBean();
        yaml.setResources(resource);
        try {
            Map<String, Object> root = yaml.getObject();
            return root == null ? Map.of() : root;
        } catch (RuntimeException exception) {
            throw new IllegalStateException("마스킹 규칙 파일을 읽지 못했습니다. location=" + location, exception);
        }
    }

    private static PiiRule toRule(Object item, int index, String location) {
        if (!(item instanceof Map<?, ?> fields)) {
            throw new IllegalStateException("마스킹 규칙 " + (index + 1) + "번째 항목이 객체가 아닙니다. location=" + location);
        }
        String id = text(fields, "id");
        String label = id == null ? (index + 1) + "번째 규칙" : id;
        String regex = text(fields, "pattern");
        String replacement = text(fields, "replacement");
        if (id == null || regex == null || replacement == null) {
            throw new IllegalStateException("마스킹 규칙 '" + label + "'에 id·pattern·replacement가 모두 있어야 합니다.");
        }
        try {
            return new PiiRule(id, text(fields, "description"), Pattern.compile(regex), replacement, Boolean.TRUE.equals(fields.get("verify")));
        } catch (PatternSyntaxException exception) {
            throw new IllegalStateException("마스킹 규칙 '" + id + "'의 정규식이 올바르지 않습니다: " + exception.getDescription(), exception);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(exception.getMessage(), exception);
        }
    }

    private static String text(Map<?, ?> fields, String key) {
        Object value = fields.get(key);
        return value == null || value.toString().isBlank() ? null : value.toString();
    }
}
