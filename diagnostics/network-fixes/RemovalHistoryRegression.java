import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Deterministic controller test; run in a disposable working directory. */
public class RemovalHistoryRegression {
    static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        BioMarkerSelection controller = new BioMarkerSelection() {
            public void startThreads(String[] ips) { }
            protected double evaluateCandidate(String ip, double[][] matrix, int candidate) {
                // Equal candidate scores exercise deterministic tie handling;
                // different iteration scores require sorting a copy of history.
                return (matrix[0].length % 3 + 1) / 10.0;
            }
        };
        PrintStream original = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(captured, true, "UTF-8"));
            for (int count : new int[] {13, 3}) {
                captured.reset();
                String[] labels = new String[count];
                for (int i = 0; i < count; i++) labels[i] = "label" + i;
                labels[0] = "label,\"quoted\"";
                controller.setID(labels);
                controller.backwardsElimination(new double[2][count][2], new String[] {"a", "b"});
                List<String> rows = Files.readAllLines(Paths.get("biomarker-removal-history.csv"), StandardCharsets.UTF_8);
                require(rows.size() == count, "full history / reset between runs");
                require(rows.get(1).startsWith("1,\"label,\"\"quoted\"\"\","), "CSV escaping");
                for (int i = 1; i < count; i++) {
                    double expected = ((count - i) % 3 + 1) / 10.0;
                    require(rows.get(i).startsWith(i + ","), "removal order");
                    require(rows.get(i).endsWith("," + expected), "matching score");
                    if (i > 1) require(rows.get(i).contains("\"label" + (i - 1) + "\""), "original label");
                }
                require(controller.ID.length == 1 && controller.ID[0].equals("label" + (count - 1)), "survivor");
                String console = captured.toString("UTF-8");
                String summary = console.substring(console.indexOf("Top "));
                int entries = 0;
                double previous = Double.POSITIVE_INFINITY;
                int previousOrder = 0;
                for (String line : summary.split("\\R")) {
                    if (!line.matches("\\d+\\. .*")) continue;
                    int end = line.indexOf(" (removal ");
                    double score = Double.parseDouble(line.substring(line.lastIndexOf(": ") + 2, end));
                    int order = Integer.parseInt(line.substring(end + 10, line.length() - 1));
                    require(score <= previous, "descending top scores");
                    if (score == previous) require(order > previousOrder, "stable tie order");
                    previous = score;
                    previousOrder = order;
                    entries++;
                }
                require(entries == Math.min(10, count - 1), "top ten cap / short run");
            }
        } finally {
            System.setOut(original);
        }
        System.out.println("PASS: ordered labels/scores, CSV escaping, ranked top ten, ties, survivor, repeated runs");
    }
}
