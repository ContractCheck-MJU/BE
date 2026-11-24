package ac.mju.contractcheck.domain;

import ac.mju.contractcheck.domain.ClauseMatcher.Match;
import ac.mju.contractcheck.domain.enums.ChangeType;
import ac.mju.contractcheck.domain.enums.RiskLevel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * FR-CMP-01 두 계약의 조항을 짝짓고 변경 유형과 위험 변화를 정한다.
 *
 * <ul>
 *   <li>짝: 임베딩 유사도로 {@link ClauseMatcher}가 정한다. 유사도가 없는(임베딩이 없는) 조합은 짝짓지 않는다.</li>
 *   <li>SAME·MODIFIED: 짝지은 두 조항의 본문을 공백(줄바꿈 포함)을 하나로 줄여 비교해 같으면 SAME, 다르면 MODIFIED다.
 *       금액·날짜처럼 글자 몇 개만 바뀐 조항은 유사도가 높아도 MODIFIED여야 해서 유사도로 정하지 않는다(TC-CMP-006, 팀 결정).
 *       조항 번호가 밀려 제목이 바뀌는 경우가 많아 제목은 비교하지 않는다.</li>
 *   <li>짝이 없는 이전 조항은 DELETED, 이후 조항은 ADDED다.</li>
 *   <li>risk_delta: 등급 점수(SAFE 0·CAUTION 1·HIGH 2)의 이후 − 이전. 판정이 없거나 조항이 없는 쪽은 0으로 본다.</li>
 * </ul>
 * 결과는 이후 계약 조항 번호 순(DELETED는 이전 조항 번호 자리, 같은 자리면 뒤)이다.
 */
public final class ClauseComparator {

    private static final double NO_SIMILARITY = -1.0;

    private ClauseComparator() {}

    public static List<ComparisonPair> compare(
            List<Clause> before,
            List<Clause> after,
            List<ClauseSimilarity> similarities,
            Map<Long, RiskLevel> beforeRisks,
            Map<Long, RiskLevel> afterRisks,
            ComparisonPolicy policy) {
        Map<Long, Integer> beforeIndex = indexById(before);
        Map<Long, Integer> afterIndex = indexById(after);
        double[][] matrix = new double[before.size()][after.size()];
        for (double[] row : matrix) {
            Arrays.fill(row, NO_SIMILARITY);
        }
        for (ClauseSimilarity item : similarities) {
            Integer row = beforeIndex.get(item.beforeClauseId());
            Integer column = afterIndex.get(item.afterClauseId());
            if (row != null && column != null) {
                matrix[row][column] = item.similarity();
            }
        }

        List<Ordered> ordered = new ArrayList<>();
        boolean[] beforeMatched = new boolean[before.size()];
        boolean[] afterMatched = new boolean[after.size()];
        for (Match match : ClauseMatcher.match(matrix, policy.matchThreshold())) {
            Clause oldClause = before.get(match.beforeIndex());
            Clause newClause = after.get(match.afterIndex());
            beforeMatched[match.beforeIndex()] = true;
            afterMatched[match.afterIndex()] = true;
            ChangeType type = normalize(oldClause.text()).equals(normalize(newClause.text()))
                    ? ChangeType.SAME
                    : ChangeType.MODIFIED;
            int delta = score(afterRisks.get(newClause.id())) - score(beforeRisks.get(oldClause.id()));
            ordered.add(new Ordered(
                    new ComparisonPair(oldClause.id(), newClause.id(), match.similarity(), type, delta), newClause.no(), false));
        }
        for (int index = 0; index < after.size(); index++) {
            if (!afterMatched[index]) {
                Clause added = after.get(index);
                ordered.add(new Ordered(
                        new ComparisonPair(null, added.id(), null, ChangeType.ADDED, score(afterRisks.get(added.id()))),
                        added.no(), false));
            }
        }
        for (int index = 0; index < before.size(); index++) {
            if (!beforeMatched[index]) {
                Clause deleted = before.get(index);
                ordered.add(new Ordered(
                        new ComparisonPair(deleted.id(), null, null, ChangeType.DELETED, -score(beforeRisks.get(deleted.id()))),
                        deleted.no(), true));
            }
        }
        ordered.sort(Comparator.comparingInt(Ordered::position).thenComparing(Ordered::deleted));
        return ordered.stream().map(Ordered::pair).toList();
    }

    /** 앞뒤 공백을 지우고 연속 공백(줄바꿈 포함)을 하나로 줄인다. */
    static String normalize(String text) {
        return text == null ? "" : text.strip().replaceAll("\\s+", " ");
    }

    private static int score(RiskLevel level) {
        if (level == null) {
            return 0;
        }
        return switch (level) {
            case SAFE -> 0;
            case CAUTION -> 1;
            case HIGH -> 2;
        };
    }

    private static Map<Long, Integer> indexById(List<Clause> clauses) {
        Map<Long, Integer> index = new HashMap<>();
        for (int position = 0; position < clauses.size(); position++) {
            index.put(clauses.get(position).id(), position);
        }
        return index;
    }

    private record Ordered(ComparisonPair pair, int position, boolean deleted) {}
}
