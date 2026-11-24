package ac.mju.contractcheck.service;

import ac.mju.contractcheck.domain.PiiRule;
import ac.mju.contractcheck.repository.PiiRuleRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * FR-UPL-04 개인정보 마스킹. 텍스트에서 이름·주민번호·전화번호·계좌번호·주소를 찾아 치환 문자로 바꾼다.
 * 무엇을 어떻게 가릴지는 {@link PiiRuleRepository}가 파일에서 읽은 규칙이 정하고, 이 서비스는 그 규칙을 순서대로 적용한다.
 *
 * <p>마스킹은 외부(추론 서버·Claude)로 나가기 전에 거친다. 그래서 실패하면 원문을 돌려주지 않고 {@link MaskingException}을 던진다.
 * 규칙에서 {@code verify}가 켜진 패턴(주민번호·전화번호·계좌번호)은 마스킹 뒤에 한 번 더 검사해, 남아 있으면 실패로 본다.
 * 로그에는 규칙별 치환 건수만 남기고 원문·치환 결과는 남기지 않는다.
 *
 * <p>TODO(FR-UPL-04): 호칭 없이 쓰인 이름은 정규식으로 가리지 못한다(NER은 아직 없다).
 */
@Slf4j
@Service
public class PiiMaskingService {

    private final List<PiiRule> rules;

    public PiiMaskingService(@NonNull PiiRuleRepository ruleRepository) {
        this.rules = ruleRepository.findAll();
    }

    /**
     * 규칙을 파일에 적힌 순서대로 적용한다. 빈 문자열이나 공백뿐인 텍스트는 그대로 돌려준다.
     *
     * @throws MaskingException 규칙 적용 중 오류가 났거나, 마스킹 뒤에도 verify 패턴이 남았을 때
     */
    public String mask(@NonNull String text) {
        if (text.isBlank()) {
            return text;
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        String masked = text;
        for (PiiRule rule : rules) {
            masked = applyRule(rule, masked, counts);
        }
        for (PiiRule rule : rules) {
            if (rule.verify() && matchesSafely(rule, masked)) {
                throw new MaskingException(rule.id());
            }
        }
        if (!counts.isEmpty()) {
            log.info("개인정보 마스킹 rules={}", counts);
        }
        return masked;
    }

    /** 여러 텍스트를 같은 순서·개수로 마스킹한다. 하나라도 실패하면 전체가 실패한다(일부만 전송하지 않는다). */
    public List<String> maskAll(@NonNull List<String> texts) {
        return texts.stream().map(this::mask).toList();
    }

    private static String applyRule(PiiRule rule, String text, Map<String, Integer> counts) {
        try {
            int count = rule.count(text);
            if (count == 0) {
                return text;
            }
            counts.put(rule.id(), count);
            return rule.apply(text);
        } catch (RuntimeException | StackOverflowError exception) {
            throw new MaskingException(rule.id(), exception);
        }
    }

    private static boolean matchesSafely(PiiRule rule, String text) {
        try {
            return rule.matches(text);
        } catch (RuntimeException | StackOverflowError exception) {
            throw new MaskingException(rule.id(), exception);
        }
    }
}
