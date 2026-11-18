package ac.mju.contractcheck.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * OCR 원문을 조항으로 나눈다(FR-UPL-03, TC-UPL-010~012).
 *
 * <ul>
 *   <li>"제N조", "제 N 조", "제N조의M"은 조항의 시작이다. 줄 시작이면 "(제목)"이 붙거나 뒤가 공백·줄 끝일 때,
 *       줄 중간이면 "(제목)"이 붙고 그 뒤가 공백·줄 끝일 때만 시작으로 본다.
 *       본문 중 "제3조에 따라", "제3조(수리)에 따라" 같은 참조는 분할하지 않는다.</li>
 *   <li>[특약사항] 구간에서는 1부터 이어지는 "N." / "N)" 번호마다 개별 조항이다. 한 줄에 이어 써도 나눈다.</li>
 *   <li>제N조가 하나도 없고 특약사항 표시도 없으면 줄 시작의 "N." / "N)" 번호마다 조항이다.
 *       제N조가 있는 문서의 하위 번호는 그 조항 안에 남긴다.</li>
 *   <li>어떤 패턴도 없으면 빈 줄 기준 문단으로 대체 분할하고 {@link ClauseSplitResult#paragraphFallback()}을 true로 둔다.</li>
 * </ul>
 *
 * 조항의 text는 제목을 포함한 원문 구간이다. 첫 조항 앞의 문구(문서 제목, 당사자 표시)는 제목 없는 첫 조항으로 남긴다.
 *
 * 스펙 미정: 번호 조항·문단 조항의 title(지금은 빈 문자열), 첫 조항 앞 문구의 처리 방식, 분할 결과를 저장할 때 마스킹 본을 쓸지 여부.
 */
public class ClauseSplitter {

    private static final Pattern ARTICLE = Pattern.compile("(?<![\\p{L}\\d])제\\s*\\d+\\s*조(?:\\s*의\\s*\\d+)?");
    private static final Pattern TITLE_PAREN = Pattern.compile("^\\s*\\([^()\\n]{1,100}\\)");
    private static final Pattern SPECIAL_MARK = Pattern.compile("\\[\\s*특약\\s*사항\\s*\\]|(?m)^[ \\t]*특약\\s*사항[ \\t]*[:：]?[ \\t]*$");
    private static final Pattern NUMBER = Pattern.compile("(?<![\\p{L}\\d.,])(\\d{1,2})\\s*[.)]\\s+(?=\\S)");
    private static final Pattern BLANK_LINES = Pattern.compile("\\n\\s*\\n");

    public ClauseSplitResult split(String rawText) {
        String text = normalize(rawText);
        if (text.isEmpty()) {
            return new ClauseSplitResult(List.of(), false);
        }
        List<Mark> marks = findMarks(text);
        if (marks.isEmpty()) {
            return new ClauseSplitResult(paragraphs(text), true);
        }
        return new ClauseSplitResult(clausesFrom(text, marks), false);
    }

    private static String normalize(String rawText) {
        if (rawText == null) {
            return "";
        }
        return rawText.replace("\r\n", "\n").replace('\r', '\n').strip();
    }

    private List<Mark> findMarks(String text) {
        List<Mark> articles = articleMarks(text);
        List<Mark> specials = specialMarks(text);
        List<Mark> marks = new ArrayList<>(articles);
        marks.addAll(specials);
        marks.addAll(numberMarks(text, articles, specials));
        marks.sort(Comparator.comparingInt(Mark::start));
        return marks;
    }

    private List<Mark> articleMarks(String text) {
        List<Mark> marks = new ArrayList<>();
        Matcher matcher = ARTICLE.matcher(text);
        while (matcher.find()) {
            int end = matcher.end();
            Matcher paren = TITLE_PAREN.matcher(text.substring(end));
            boolean hasTitle = paren.find();
            int titleEnd = hasTitle ? end + paren.end() : end;
            boolean atLineStart = startsLine(text, matcher.start());
            boolean heading = atLineStart
                    ? (hasTitle || endsHeadingWord(text, end))
                    : (hasTitle && endsHeadingWord(text, titleEnd));
            if (!heading) {
                continue;
            }
            String title = text.substring(matcher.start(), titleEnd).replaceAll("\\s+", " ").strip();
            marks.add(new Mark(matcher.start(), Kind.ARTICLE, title));
        }
        return marks;
    }

    private List<Mark> specialMarks(String text) {
        List<Mark> marks = new ArrayList<>();
        Matcher matcher = SPECIAL_MARK.matcher(text);
        while (matcher.find()) {
            marks.add(new Mark(matcher.start(), Kind.SPECIAL, "특약사항"));
        }
        return marks;
    }

    private List<Mark> numberMarks(String text, List<Mark> articles, List<Mark> specials) {
        List<Mark> marks = new ArrayList<>();
        if (articles.isEmpty() && specials.isEmpty()) {
            Matcher matcher = NUMBER.matcher(text);
            while (matcher.find()) {
                if (startsLine(text, matcher.start())) {
                    marks.add(new Mark(matcher.start(), Kind.NUMBER, ""));
                }
            }
            return marks;
        }
        for (Mark special : specials) {
            int zoneEnd = nextArticleStart(articles, special.start(), text.length());
            int expected = 1;
            Matcher matcher = NUMBER.matcher(text).region(special.start(), zoneEnd);
            while (matcher.find()) {
                if (Integer.parseInt(matcher.group(1)) == expected) {
                    marks.add(new Mark(matcher.start(), Kind.NUMBER, ""));
                    expected++;
                }
            }
        }
        return marks;
    }

    private static int nextArticleStart(List<Mark> articles, int from, int fallback) {
        return articles.stream().mapToInt(Mark::start).filter(start -> start > from).min().orElse(fallback);
    }

    private static boolean startsLine(String text, int index) {
        for (int i = index - 1; i >= 0; i--) {
            char ch = text.charAt(i);
            if (ch == '\n') {
                return true;
            }
            if (!Character.isWhitespace(ch)) {
                return false;
            }
        }
        return true;
    }

    private static boolean endsHeadingWord(String text, int end) {
        return end >= text.length() || Character.isWhitespace(text.charAt(end));
    }

    private List<ClauseDraft> clausesFrom(String text, List<Mark> marks) {
        List<ClauseDraft> clauses = new ArrayList<>();
        String preamble = text.substring(0, marks.get(0).start()).strip();
        if (!preamble.isEmpty()) {
            clauses.add(new ClauseDraft(clauses.size() + 1, "", preamble));
        }
        for (int i = 0; i < marks.size(); i++) {
            Mark mark = marks.get(i);
            int end = (i + 1 < marks.size()) ? marks.get(i + 1).start() : text.length();
            String segment = text.substring(mark.start(), end).strip();
            if (mark.kind() == Kind.SPECIAL && isOnlyHeader(segment)) {
                continue;
            }
            clauses.add(new ClauseDraft(clauses.size() + 1, mark.title(), segment));
        }
        return clauses;
    }

    private static boolean isOnlyHeader(String segment) {
        return SPECIAL_MARK.matcher(segment).replaceAll("").isBlank();
    }

    private List<ClauseDraft> paragraphs(String text) {
        List<ClauseDraft> clauses = new ArrayList<>();
        for (String paragraph : BLANK_LINES.split(text)) {
            String stripped = paragraph.strip();
            if (!stripped.isEmpty()) {
                clauses.add(new ClauseDraft(clauses.size() + 1, "", stripped));
            }
        }
        return clauses;
    }

    private enum Kind {
        ARTICLE,
        SPECIAL,
        NUMBER
    }

    private record Mark(int start, Kind kind, String title) {}
}
