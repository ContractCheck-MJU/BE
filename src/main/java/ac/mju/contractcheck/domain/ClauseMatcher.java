package ac.mju.contractcheck.domain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * FR-CMP-01 조항 짝 맞추기. 이전·이후 조항의 유사도 표에서 유사도 합이 최대가 되게 1:1로 짝짓는다(헝가리안 알고리즘, O(n³)).
 * 높은 유사도부터 고르는 방식은 순서가 바뀐 조항에서 짝을 놓칠 수 있어 쓰지 않는다. 임계값 미만인 쌍은 짝으로 보지 않는다.
 * 외부 라이브러리 없이 직접 구현한다(계약서 조항은 수십 개라 비용이 작다).
 */
public final class ClauseMatcher {

    /** 짝이 없음과 같은 비용. 임계값 미만이거나 정사각형으로 채운 가짜 칸에 쓴다. */
    private static final double NO_MATCH_COST = 1.0;

    private ClauseMatcher() {}

    /** 이전 조항 {@code beforeIndex}와 이후 조항 {@code afterIndex}의 짝. 인덱스는 유사도 표의 행·열 번호다. */
    public record Match(int beforeIndex, int afterIndex, double similarity) {}

    /**
     * @param similarity 행=이전 조항, 열=이후 조항인 유사도 표(직사각형). 값이 임계값 미만이면 짝짓지 않는다.
     * @return 이전 조항 순서로 정렬한 짝 목록. 어느 쪽이든 조항이 없으면 빈 목록이다.
     */
    public static List<Match> match(double[][] similarity, double threshold) {
        int rows = similarity.length;
        int columns = rows == 0 ? 0 : similarity[0].length;
        if (rows == 0 || columns == 0) {
            return List.of();
        }
        int size = Math.max(rows, columns);
        double[][] cost = new double[size][size];
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                boolean real = row < rows && column < columns && similarity[row][column] >= threshold;
                cost[row][column] = real ? 1.0 - similarity[row][column] : NO_MATCH_COST;
            }
        }
        int[] assignment = minimumCostAssignment(cost);
        List<Match> matches = new ArrayList<>();
        for (int row = 0; row < rows; row++) {
            int column = assignment[row];
            if (column < columns && similarity[row][column] >= threshold) {
                matches.add(new Match(row, column, similarity[row][column]));
            }
        }
        matches.sort(Comparator.comparingInt(Match::beforeIndex));
        return matches;
    }

    /** 정사각형 비용 표의 최소 비용 배정. 결과의 i번째 값은 i행에 배정된 열이다. */
    private static int[] minimumCostAssignment(double[][] cost) {
        int size = cost.length;
        double[] rowPotential = new double[size + 1];
        double[] columnPotential = new double[size + 1];
        int[] rowOfColumn = new int[size + 1];
        int[] previousColumn = new int[size + 1];
        for (int row = 1; row <= size; row++) {
            rowOfColumn[0] = row;
            int column = 0;
            double[] minimum = new double[size + 1];
            Arrays.fill(minimum, Double.POSITIVE_INFINITY);
            boolean[] used = new boolean[size + 1];
            do {
                used[column] = true;
                int currentRow = rowOfColumn[column];
                double delta = Double.POSITIVE_INFINITY;
                int nextColumn = 0;
                for (int candidate = 1; candidate <= size; candidate++) {
                    if (used[candidate]) {
                        continue;
                    }
                    double reduced = cost[currentRow - 1][candidate - 1] - rowPotential[currentRow] - columnPotential[candidate];
                    if (reduced < minimum[candidate]) {
                        minimum[candidate] = reduced;
                        previousColumn[candidate] = column;
                    }
                    if (minimum[candidate] < delta) {
                        delta = minimum[candidate];
                        nextColumn = candidate;
                    }
                }
                for (int candidate = 0; candidate <= size; candidate++) {
                    if (used[candidate]) {
                        rowPotential[rowOfColumn[candidate]] += delta;
                        columnPotential[candidate] -= delta;
                    } else {
                        minimum[candidate] -= delta;
                    }
                }
                column = nextColumn;
            } while (rowOfColumn[column] != 0);
            do {
                int previous = previousColumn[column];
                rowOfColumn[column] = rowOfColumn[previous];
                column = previous;
            } while (column != 0);
        }
        int[] assignment = new int[size];
        for (int column = 1; column <= size; column++) {
            assignment[rowOfColumn[column] - 1] = column - 1;
        }
        return assignment;
    }
}
