package ac.mju.contractcheck.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 근거: 스펙시트 'TC-계약서업로드' TC-UPL-010~012, 'AI·RAG 파이프라인' 3단계(조항 분할). */
class ClauseSplitterTest {

    private final ClauseSplitter splitter = new ClauseSplitter();

    @Test
    @DisplayName("TC-UPL-010: 제N조 패턴은 조항 3개로 나뉘고 no=1~3, title=\"제1조(목적)\" 등으로 분리된다")
    void splitsByArticleMarkers() {
        String text = "제1조(목적) 본 계약은 임대차에 관한 사항을 정한다. "
                + "제2조(보증금) 보증금은 1억원으로 한다. "
                + "제3조(계약기간) 계약기간은 2년으로 한다.";

        SplitClauses<ClauseDraft> result = splitter.split(text);

        assertThat(result.paragraphFallback()).isFalse();
        assertThat(result.clauses()).extracting(ClauseDraft::no).containsExactly(1, 2, 3);
        assertThat(result.clauses()).extracting(ClauseDraft::title)
                .containsExactly("제1조(목적)", "제2조(보증금)", "제3조(계약기간)");
        assertThat(result.clauses().get(1).text()).contains("보증금은 1억원으로 한다.");
        assertThat(result.clauses().get(1).text()).doesNotContain("계약기간");
    }

    @Test
    @DisplayName("TC-UPL-010: 줄바꿈으로 구분된 조항도 같은 결과로 나뉜다")
    void splitsArticlesOnSeparateLines() {
        String text = "제1조(목적)\n본 계약은 임대차에 관한 사항을 정한다.\n\n제2조(보증금)\n보증금은 1억원으로 한다.";

        SplitClauses<ClauseDraft> result = splitter.split(text);

        assertThat(result.clauses()).extracting(ClauseDraft::title).containsExactly("제1조(목적)", "제2조(보증금)");
    }

    @Test
    @DisplayName("TC-UPL-011: 공백이 들어간 \"제 1 조\"도 조항 시작으로 인식한다")
    void recognizesSpacedArticleMarker() {
        SplitClauses<ClauseDraft> result = splitter.split("제 1 조(목적) 목적 조항이다. 제 2 조(보증금) 보증금 조항이다.");

        assertThat(result.clauses()).hasSize(2);
        assertThat(result.clauses().get(0).title()).isEqualTo("제 1 조(목적)");
    }

    @Test
    @DisplayName("TC-UPL-011: \"제5조의2\"는 제5조와 별도 조항이다")
    void articleWithBranchNumberIsSeparate() {
        SplitClauses<ClauseDraft> result = splitter.split("제5조(해지) 해지 조항이다. 제5조의2(특약) 특약 조항이다.");

        assertThat(result.clauses()).extracting(ClauseDraft::title).containsExactly("제5조(해지)", "제5조의2(특약)");
    }

    @Test
    @DisplayName("TC-UPL-011: 본문 중 \"제3조에 따라\" 같은 참조는 분할하지 않는다")
    void doesNotSplitOnReferences() {
        String text = "제3조(수리) 수리 조항이다.\n제4조(비용) 수리비는 제3조에 따라 임대인이 부담한다. "
                + "제3조에 의한 비용은 별도로 정산한다.";

        SplitClauses<ClauseDraft> result = splitter.split(text);

        assertThat(result.clauses()).extracting(ClauseDraft::title).containsExactly("제3조(수리)", "제4조(비용)");
        assertThat(result.clauses().get(1).text()).contains("제3조에 따라").contains("제3조에 의한");
    }

    @Test
    @DisplayName("TC-UPL-011: 본문 중 괄호 제목이 붙은 참조(\"제3조(수리)에 따라\")도 분할하지 않는다")
    void doesNotSplitOnReferencesWithParenthesizedTitle() {
        String text = "제3조(수리) 수리 조항이다. 제4조(비용) 비용은 제3조(수리)에 따라 정한다. 제5조(해지) 해지 조항이다.";

        SplitClauses<ClauseDraft> result = splitter.split(text);

        assertThat(result.clauses()).extracting(ClauseDraft::title).containsExactly("제3조(수리)", "제4조(비용)", "제5조(해지)");
        assertThat(result.clauses().get(1).text()).contains("제3조(수리)에 따라");
    }

    @Test
    @DisplayName("TC-UPL-011: 제N조가 없는 문서는 \"1.\", \"2)\" 번호 줄마다 조항이 된다")
    void splitsByNumberedLinesWhenNoArticles() {
        String text = "1. 임대인은 보증금을 반환한다.\n2) 임차인은 차임을 낸다.\n3. 계약은 서면으로 한다.";

        SplitClauses<ClauseDraft> result = splitter.split(text);

        assertThat(result.paragraphFallback()).isFalse();
        assertThat(result.clauses()).hasSize(3);
        assertThat(result.clauses().get(1).text()).contains("차임을 낸다");
    }

    @Test
    @DisplayName("TC-UPL-011: [특약사항] 하위 번호마다 개별 조항이 된다")
    void splitsSpecialTermsByNumber() {
        String text = "제1조(목적) 목적 조항이다. 제2조(보증금) 보증금 조항이다. "
                + "[특약사항] 1. 반려동물 금지 2. 흡연 금지 3. 야간 소음 금지";

        SplitClauses<ClauseDraft> result = splitter.split(text);

        assertThat(result.clauses()).hasSize(5);
        assertThat(result.clauses().subList(2, 5)).extracting(ClauseDraft::text)
                .containsExactly("1. 반려동물 금지", "2. 흡연 금지", "3. 야간 소음 금지");
    }

    @Test
    @DisplayName("TC-UPL-011: 제N조 안의 하위 번호나 금액의 소수점은 분할하지 않는다")
    void doesNotSplitNumbersInsideArticle() {
        String text = "제1조(보증금)\n1. 보증금은 1.5억원으로 한다.\n2. 보증금은 계약 시 지급한다.\n제2조(기간) 기간은 2년이다.";

        SplitClauses<ClauseDraft> result = splitter.split(text);

        assertThat(result.clauses()).extracting(ClauseDraft::title).containsExactly("제1조(보증금)", "제2조(기간)");
        assertThat(result.clauses().get(0).text()).contains("1.5억원").contains("2. 보증금은 계약 시 지급한다.");
    }

    @Test
    @DisplayName("TC-UPL-012: 조항 패턴이 하나도 없으면 문단 단위로 대체 분할하고 대체 분할 표시를 한다")
    void fallsBackToParagraphs() {
        String text = "첫째 문단입니다.\n\n둘째 문단입니다.\n\n셋째 문단입니다.\n\n넷째 문단입니다.\n\n다섯째 문단입니다.\n\n여섯째 문단입니다.";

        SplitClauses<ClauseDraft> result = splitter.split(text);

        assertThat(result.paragraphFallback()).isTrue();
        assertThat(result.clauses()).hasSize(6);
        assertThat(result.clauses()).extracting(ClauseDraft::no).containsExactly(1, 2, 3, 4, 5, 6);
        assertThat(result.clauses().get(5).text()).isEqualTo("여섯째 문단입니다.");
    }

    @Test
    @DisplayName("첫 조항 앞의 문구(당사자 표시 등)는 버리지 않고 제목 없는 첫 조항으로 남긴다")
    void keepsPreambleBeforeFirstArticle() {
        SplitClauses<ClauseDraft> result = splitter.split("부동산 임대차 계약서\n제1조(목적) 목적 조항이다.");

        assertThat(result.clauses()).hasSize(2);
        assertThat(result.clauses().get(0).title()).isEmpty();
        assertThat(result.clauses().get(0).text()).isEqualTo("부동산 임대차 계약서");
        assertThat(result.clauses().get(1).title()).isEqualTo("제1조(목적)");
    }

    @Test
    @DisplayName("빈 문자열이나 공백만 있으면 조항 0건이고 대체 분할도 아니다")
    void blankTextHasNoClauses() {
        assertThat(splitter.split("").clauses()).isEqualTo(List.of());
        assertThat(splitter.split("  \n \n ").paragraphFallback()).isFalse();
    }
}
