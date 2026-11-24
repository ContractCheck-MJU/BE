package ac.mju.contractcheck.domain;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.NonNull;

/**
 * 개인정보 마스킹 규칙 한 건(FR-UPL-04). 정규식과 치환 문자를 가진다. 규칙 내용은 파일에서 오고 이 클래스는 파일 형식을 모른다.
 * id·치환 문자가 비었거나 치환 문자가 없는 캡처 그룹을 참조하면 만들어지지 않는다(정합성).
 *
 * @param id 규칙 이름. 로그에 건수와 함께 남기며 원문은 남기지 않는다.
 * @param description 무엇을 가리는 규칙인지
 * @param pattern 가릴 값을 찾는 정규식
 * @param replacement 치환 문자. {@code $1}처럼 캡처 그룹을 참조할 수 있다.
 * @param verify true면 마스킹 뒤에도 이 패턴이 남아 있는지 검사해, 남아 있으면 외부 전송을 막는다.
 */
public record PiiRule(String id, String description, @NonNull Pattern pattern, String replacement, boolean verify) {

    private static final Pattern GROUP_REFERENCE = Pattern.compile("(?<!\\\\)\\$(\\d+)");

    public PiiRule {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("마스킹 규칙 id가 비어 있습니다.");
        }
        if (replacement == null || replacement.isEmpty()) {
            throw new IllegalArgumentException("마스킹 규칙 '" + id + "'의 치환 문자가 비어 있습니다.");
        }
        description = description == null ? "" : description;
        int groups = pattern.matcher("").groupCount();
        Matcher reference = GROUP_REFERENCE.matcher(replacement);
        while (reference.find()) {
            if (Integer.parseInt(reference.group(1)) > groups) {
                throw new IllegalArgumentException(
                        "마스킹 규칙 '" + id + "'의 치환 문자가 없는 그룹 $" + reference.group(1) + "을 참조합니다. 그룹 수=" + groups);
            }
        }
    }

    /** 패턴에 맞는 모든 부분을 치환 문자로 바꾼 텍스트. */
    public String apply(@NonNull String text) {
        return pattern.matcher(text).replaceAll(replacement);
    }

    /** 패턴에 맞는 부분의 개수. 마스킹 건수 로그에 쓴다. */
    public int count(@NonNull String text) {
        return (int) pattern.matcher(text).results().count();
    }

    public boolean matches(@NonNull String text) {
        return pattern.matcher(text).find();
    }
}
