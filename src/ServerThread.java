import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/** One TCP request to a remote WorkerClient. */
public class ServerThread extends Thread {
    private static final AtomicLong IDS = new AtomicLong();
    private static final ScheduledThreadPoolExecutor DEADLINES = new ScheduledThreadPoolExecutor(1, r -> {
        Thread thread = new Thread(r, "worker-timeouts"); thread.setDaemon(true); return thread;
    });
    static { DEADLINES.setRemoveOnCancelPolicy(true); }
    private final Socket socket = new Socket();
    private final long job = IDS.incrementAndGet();
    private final ScheduledFuture<?> deadline;
    private volatile boolean done = true;
    private volatile RuntimeException failure;
    private double errorRate;
    private int position = -1, folds = 5;
    private double[][] norm;
    public ServerThread(String ip) throws IOException { this(ip, Integer.getInteger("worker.timeoutMillis", 1800000)); }
    public ServerThread(String ip, int timeoutMillis) throws IOException { this(ip, WorkerProtocol.PORT, timeoutMillis); }
    public ServerThread(String ip, int port, int timeoutMillis) throws IOException {
        if (timeoutMillis <= 0) throw new IllegalArgumentException("Timeout must be positive");
        try {
            socket.connect(new InetSocketAddress(ip.trim(), port), Math.min(timeoutMillis, 3000));
            socket.setSoTimeout(timeoutMillis);
        } catch (IOException | RuntimeException e) { socket.close(); throw e; }
        // Bound blocked writes as well as reads.
        deadline = DEADLINES.schedule(() -> {
            try { socket.close(); } catch (IOException ignored) { }
        }, timeoutMillis, TimeUnit.MILLISECONDS);
    }
    public void setData(double[][] data, int candidate) { norm = data; position = candidate; }
    public void setFolds(int value) { folds = value; }
    public void setDone(boolean value) { done = value; }
    public int getPosition() { return position; }
    public boolean getIsDone() { return done; }
    public double getErrorRate() { if (failure != null) throw failure; return errorRate; }
    public void run() {
        done = false; failure = null;
        try {
            sendArray();
            errorRate = WorkerProtocol.result(new DataInputStream(socket.getInputStream()), job);
        } catch (Exception e) { failure = new IllegalStateException("Remote evaluation failed: " + e.getMessage(), e); }
        finally { closeSocket(); done = true; }
    }
    public void sendArray() {
        try {
            WorkerProtocol.validate(norm, folds);
            DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            WorkerProtocol.header(out, WorkerProtocol.EVALUATE, job);
            out.writeInt(norm.length); out.writeInt(norm[0].length); out.writeInt(folds);
            for (double[] row : norm) for (double value : row) out.writeDouble(value);
            out.flush();
        } catch (IOException e) {
            throw new IllegalStateException("Matrix transfer failed: " + e, e);
        }
    }
    public boolean checkConnection() {
        try {
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            WorkerProtocol.header(out, WorkerProtocol.PING, job); out.flush();
            return WorkerProtocol.resultValue(new DataInputStream(socket.getInputStream()), job) > 0;
        } catch (IOException e) {
            System.err.println("BWP1 ping failed for " + socket.getRemoteSocketAddress() + ": " + e);
            return false;
        }
        finally { closeSocket(); }
    }
    public int getRemoteProcessorCount() throws IOException {
        try {
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            WorkerProtocol.header(out, WorkerProtocol.PING, job); out.flush();
            double reported = WorkerProtocol.resultValue(new DataInputStream(socket.getInputStream()), job);
            if (reported < 1 || reported > Integer.MAX_VALUE || reported != Math.rint(reported))
                throw new IOException("Worker returned an invalid processor count: " + reported);
            return (int) reported;
        } finally {
            closeSocket();
        }
    }
    public void closeSocket() {
        deadline.cancel(false);
        try { socket.close(); } catch (IOException ignored) { }
    }
}
