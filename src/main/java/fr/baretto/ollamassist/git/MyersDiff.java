package fr.baretto.ollamassist.git;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.*;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class MyersDiff {

    private static final String ERROR_MESSAGE_FORMAT = "Invalid backtrack: prevK=%d not found at d=%d";
    private static final String INSERT_PREFIX = "+ ";
    private static final String DELETE_PREFIX = "- ";
    private static final String EQUAL_PREFIX = "  ";

    public static List<Edit> computeDiff(List<String> a, List<String> b) {
        int N = a.size();
        int M = b.size();
        int max = N + M;
        Map<Integer, Integer> v = new HashMap<>();
        v.put(1, 0);

        Map<Integer, Map<Integer, Integer>> trace = new HashMap<>();

        for (int d = 0; d <= max; d++) {
            Map<Integer, Integer> vNew = new HashMap<>();
            for (int k = -d; k <= d; k += 2) {
                int x;
                if (k == -d || (k != d && v.getOrDefault(k - 1, 0) < v.getOrDefault(k + 1, 0))) {
                    x = v.getOrDefault(k + 1, 0);
                } else {
                    x = v.getOrDefault(k - 1, 0) + 1;
                }
                int y = x - k;

                // Snake
                while (x < N && y < M && a.get(x).equals(b.get(y))) {
                    x++;
                    y++;
                }

                vNew.put(k, x);
                if (x >= N && y >= M) {
                    trace.put(d, vNew);
                    return backtrack(trace, a, b, d);
                }
            }
            trace.put(d, vNew);
            v = vNew;
        }

        throw new IllegalStateException("Diff computation failed unexpectedly");
    }

    private static List<Edit> backtrack(Map<Integer, Map<Integer, Integer>> trace,
                                        List<String> a, List<String> b, int d) {
        List<Edit> edits = new ArrayList<>();
        int x = a.size();
        int y = b.size();

        for (int i = d; i > 0; i--) {
            Map<Integer, Integer> v = trace.get(i);
            int k = x - y;
            int prevK;

            if (k == -i || (k != i && v.getOrDefault(k - 1, 0) < v.getOrDefault(k + 1, 0))) {
                prevK = k + 1;
            } else {
                prevK = k - 1;
            }

            Integer prevXObj = trace.get(i - 1).get(prevK);
            if (prevXObj == null) {
                throw new IllegalStateException(String.format(ERROR_MESSAGE_FORMAT, prevK, i - 1));
            }
            int prevX = prevXObj;
            int prevY = prevX - prevK;

            while (x > prevX && y > prevY) {
                edits.add(new Edit(Operation.EQUAL, a.get(x - 1)));
                x--;
                y--;
            }

            if (x == prevX) {
                edits.add(new Edit(Operation.INSERT, b.get(y - 1)));
                y--;
            } else {
                edits.add(new Edit(Operation.DELETE, a.get(x - 1)));
                x--;
            }
        }

        while (x > 0 || y > 0) {
            if (x > 0 && y > 0 && a.get(x - 1).equals(b.get(y - 1))) {
                edits.add(new Edit(Operation.EQUAL, a.get(x - 1)));
                x--;
                y--;
            } else if (x > 0) {
                edits.add(new Edit(Operation.DELETE, a.get(x - 1)));
                x--;
            } else if (y > 0) {
                edits.add(new Edit(Operation.INSERT, b.get(y - 1)));
                y--;
            }
        }

        Collections.reverse(edits);
        return edits;
    }

    public static String computeCompactDiff(String before, String after) {
        if (before == null) before = "";
        if (after == null) after = "";

        List<String> a = Arrays.asList(before.split("\n"));
        List<String> b = Arrays.asList(after.split("\n"));
        List<Edit> edits = computeDiff(a, b);

        StringBuilder sb = new StringBuilder();
        for (Edit edit : edits) {
            if (edit.op != Operation.EQUAL) {
                sb.append(edit).append("\n");
            }
        }
        return sb.toString();
    }

    public enum Operation {EQUAL, INSERT, DELETE}

    public static class Edit {
        public final Operation op;
        public final String line;

        public Edit(Operation op, String line) {
            this.op = op;
            this.line = line;
        }

        @Override
        public String toString() {
            return switch (op) {
                case INSERT -> INSERT_PREFIX + line;
                case DELETE -> DELETE_PREFIX + line;
                case EQUAL -> EQUAL_PREFIX + line;
            };
        }
    }
}