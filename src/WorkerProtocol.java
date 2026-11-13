import java.io.*;

/** BWP1: big-endian, one request/response per TCP connection. */
public final class WorkerProtocol {
    public static final int MAGIC = 0x42575031, PORT = 6003, PING = 1, EVALUATE = 2;
    private WorkerProtocol() { }
    public static void header(DataOutputStream out, int command, long job) throws IOException {
        out.writeInt(MAGIC); out.writeByte(command); out.writeLong(job);
    }
    public static void dimensions(int rows, int columns, int folds) throws IOException {
        if (rows < 4 || rows > 100000 || rows % 2 != 0 || columns < 1
                || (long) rows * columns > 10000000 || folds < 2 || folds > rows / 2)
            throw new IOException("Invalid dimensions/folds (paired rows required)");
    }
    public static void validate(double[][] matrix, int folds) throws IOException {
        if (matrix == null || matrix.length == 0 || matrix[0] == null) throw new IOException("Empty matrix");
        dimensions(matrix.length, matrix[0].length, folds);
        for (double[] row : matrix) {
            if (row == null || row.length != matrix[0].length) throw new IOException("Ragged matrix");
            for (double value : row) if (!Double.isFinite(value)) throw new IOException("Nonfinite value");
        }
    }
    public static void success(DataOutputStream out, long job, double value) throws IOException {
        out.writeInt(MAGIC); out.writeLong(job); out.writeBoolean(true); out.writeDouble(value); out.flush();
    }
    public static void failure(DataOutputStream out, long job, String message) throws IOException {
        out.writeInt(MAGIC); out.writeLong(job); out.writeBoolean(false);
        out.writeUTF(message.substring(0, Math.min(message.length(), 2000))); out.flush();
    }
    public static double result(DataInputStream in, long job) throws IOException {
        double value = resultValue(in, job);
        if (value < 0 || value > 1) throw new IOException("Invalid error rate: " + value);
        return value;
    }
    public static double resultValue(DataInputStream in, long job) throws IOException {
        if (in.readInt() != MAGIC || in.readLong() != job) throw new IOException("Invalid response header/job ID");
        if (!in.readBoolean()) throw new IOException("Worker failed: " + in.readUTF());
        double value = in.readDouble();
        if (!Double.isFinite(value)) throw new IOException("Invalid numeric response: " + value);
        return value;
    }
}
