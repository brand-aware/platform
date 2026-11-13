import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** K-nearest-antigen fallback for missing intensity values. */
public final class NonnegativeKNNImputer {
    private static final double MISSING = 999.0;
    private static final int K = 5;

    private static final class Neighbor {
        final int row;
        final double distance;
        Neighbor(int row, double distance) { this.row = row; this.distance = distance; }
    }

    /** Returns a completed copy; every estimate is an average of observed nonnegative values. */
    public double[][] impute(double[][] input) {
        if (input == null || input.length == 0 || input[0] == null || input[0].length == 0)
            throw new IllegalArgumentException("Cannot impute an empty matrix");
        int columns = input[0].length;
        double[][] completed = new double[input.length][columns];
        for (int row = 0; row < input.length; row++) {
            if (input[row] == null || input[row].length != columns)
                throw new IllegalArgumentException("Input matrix is ragged at row " + (row + 1));
            for (int column = 0; column < columns; column++) {
                double value = input[row][column];
                if (value != MISSING && (!Double.isFinite(value) || value < 0))
                    throw new IllegalArgumentException("Invalid observed intensity at row " + (row + 1)
                            + ", column " + (column + 1) + ": " + value);
                completed[row][column] = value;
            }
        }

        for (int row = 0; row < input.length; row++) {
            for (int column = 0; column < columns; column++) {
                if (input[row][column] == MISSING)
                    completed[row][column] = estimate(input, row, column);
            }
        }
        return completed;
    }

    private double estimate(double[][] input, int targetRow, int targetColumn) {
        List<Neighbor> neighbors = new ArrayList<Neighbor>();
        for (int row = 0; row < input.length; row++) {
            if (row == targetRow || input[row][targetColumn] == MISSING) continue;
            double squaredDistance = 0;
            int overlap = 0;
            for (int column = 0; column < input[targetRow].length; column++) {
                double target = input[targetRow][column];
                double candidate = input[row][column];
                if (target == MISSING || candidate == MISSING) continue;
                double difference = Math.log1p(target) - Math.log1p(candidate);
                squaredDistance += difference * difference;
                overlap++;
            }
            if (overlap > 0) neighbors.add(new Neighbor(row, Math.sqrt(squaredDistance / overlap)));
        }

        Collections.sort(neighbors, new Comparator<Neighbor>() {
            public int compare(Neighbor left, Neighbor right) {
                return Double.compare(left.distance, right.distance);
            }
        });
        if (neighbors.isEmpty()) return columnMedian(input, targetColumn);

        int count = Math.min(K, neighbors.size());
        int exactCount = 0;
        double exactTotal = 0;
        for (int index = 0; index < count; index++) {
            Neighbor neighbor = neighbors.get(index);
            if (neighbor.distance <= 1e-12) {
                exactCount++;
                exactTotal += input[neighbor.row][targetColumn];
            }
        }
        if (exactCount > 0) return exactTotal / exactCount;

        double maxWeight = 0;
        double[] weights = new double[count];
        for (int index = 0; index < count; index++) {
            weights[index] = 1.0 / neighbors.get(index).distance;
            maxWeight = Math.max(maxWeight, weights[index]);
        }
        double weightedTotal = 0, totalWeight = 0;
        for (int index = 0; index < count; index++) {
            double weight = weights[index] / maxWeight;
            weightedTotal += weight * input[neighbors.get(index).row][targetColumn];
            totalWeight += weight;
        }
        return weightedTotal / totalWeight;
    }

    private double columnMedian(double[][] input, int column) {
        List<Double> observed = new ArrayList<Double>();
        for (double[] row : input) if (row[column] != MISSING) observed.add(row[column]);
        if (observed.isEmpty())
            throw new IllegalArgumentException("No observed values available for column " + (column + 1));
        Collections.sort(observed);
        int middle = observed.size() / 2;
        return observed.size() % 2 == 1 ? observed.get(middle)
                : (observed.get(middle - 1) + observed.get(middle)) / 2.0;
    }
}
