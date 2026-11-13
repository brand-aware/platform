import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Deterministic controller scheduling checks; network transport is tested separately. */
public class SchedulingRegression {
    static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    static double[][][] data() { return new double[5][5][2]; }
    public static void main(String[] args) throws Exception {
        CountDownLatch slowStarted = new CountDownLatch(1);
        CountDownLatch fastFinished = new CountDownLatch(1);
        CountDownLatch releaseSlow = new CountDownLatch(1);
        Set<String> active = Collections.synchronizedSet(new HashSet<String>());
        Set<String> seen = Collections.synchronizedSet(new HashSet<String>());
        AtomicInteger iterations = new AtomicInteger();
        BioMarkerSelection controller = new BioMarkerSelection() {
            public void startThreads(String[] ips) { }
            protected double evaluateCandidate(String ip, double[][] matrix, int candidate) throws IOException {
                require(active.add(ip), "overlapping requests to " + ip);
                try {
                    int markers = matrix[0].length + 1;
                    require(seen.add(markers + ":" + candidate), "duplicate candidate");
                    if (markers == 5) {
                        if (candidate == 0) {
                            slowStarted.countDown();
                            require(releaseSlow.await(10, TimeUnit.SECONDS), "slow worker release timed out");
                        } else {
                            require(slowStarted.await(5, TimeUnit.SECONDS), "slow job did not start");
                            if (candidate == 4) fastFinished.countDown();
                        }
                    } else require(releaseSlow.getCount() == 0, "next iteration started before all replies");
                    return candidate / 10.0;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException(e);
                } finally { active.remove(ip); }
            }
            public int findBest(double[] errors) {
                require(releaseSlow.getCount() == 0, "selection ran before slow reply");
                for (int i = 0; i < errors.length; i++) require(errors[i] == i / 10.0, "result mapped to wrong candidate");
                iterations.incrementAndGet();
                return super.findBest(errors);
            }
        };
        controller.setID(new String[] {"a", "b", "c", "d", "e"});
        ExecutorService execution = Executors.newSingleThreadExecutor();
        Future<?> run = execution.submit(() -> controller.backwardsElimination(data(), new String[] {"slow", "fast"}));
        try {
            require(fastFinished.await(5, TimeUnit.SECONDS), "fast worker waited for batch instead of taking more jobs");
            require(iterations.get() == 0 && !run.isDone(), "iteration barrier missing");
            releaseSlow.countDown();
            run.get(10, TimeUnit.SECONDS);
            require(iterations.get() == 4 && seen.size() == 14, "missing iteration/candidate");
            require(controller.ID.length == 1 && "a".equals(controller.ID[0]), "incorrect elimination order");
        } finally {
            releaseSlow.countDown();
            execution.shutdownNow();
        }
        AtomicInteger attempts = new AtomicInteger();
        AtomicInteger successful = new AtomicInteger();
        BioMarkerSelection retrying = new BioMarkerSelection() {
            public void startThreads(String[] ips) { }
            protected double evaluateCandidate(String ip, double[][] matrix, int candidate) throws IOException {
                if (matrix[0].length == 4 && candidate == 1) {
                    int attempt = attempts.incrementAndGet();
                    if (attempt == 1) throw new IOException("simulated connection failure");
                    if (attempt == 2) throw new IllegalStateException("simulated timeout");
                    if (attempt == 3) return Double.NaN;
                }
                successful.incrementAndGet();
                return .2;
            }
            public int findBest(double[] errors) {
                require(attempts.get() == 4, "advanced before retry succeeded");
                for (double error : errors) require(error == .2, "missing error rate");
                return 0;
            }
        };
        retrying.setID(new String[] {"a", "b", "c", "d", "e"});
        retrying.backwardsElimination(data(), new String[] {"one", "two"});
        require(successful.get() == 14, "successful candidates were repeated or omitted");

        CountDownLatch failedOnce = new CountDownLatch(1);
        AtomicInteger reassigned = new AtomicInteger();
        BioMarkerSelection failover = new BioMarkerSelection() {
            public void startThreads(String[] ips) { }
            protected double evaluateCandidate(String ip, double[][] matrix, int candidate) throws IOException {
                if ("offline".equals(ip)) {
                    failedOnce.countDown();
                    throw new java.net.SocketTimeoutException("offline worker");
                }
                try { require(failedOnce.await(5, TimeUnit.SECONDS), "offline worker not exercised"); }
                catch (InterruptedException e) { throw new IOException(e); }
                reassigned.incrementAndGet();
                return .2;
            }
        };
        failover.setID(new String[] {"a", "b", "c", "d", "e"});
        failover.backwardsElimination(data(), new String[] {"offline", "healthy"});
        require(reassigned.get() == 14, "healthy worker did not finish all candidates");

        CountDownLatch repeated = new CountDownLatch(3);
        BioMarkerSelection unavailable = new BioMarkerSelection() {
            public void startThreads(String[] ips) { }
            protected double evaluateCandidate(String ip, double[][] matrix, int candidate) throws IOException {
                repeated.countDown();
                throw new java.net.SocketTimeoutException("still unavailable");
            }
            public int findBest(double[] errors) { throw new AssertionError("advanced without results"); }
        };
        unavailable.setID(new String[] {"a", "b", "c", "d", "e"});
        ExecutorService blocked = Executors.newSingleThreadExecutor();
        Future<?> waiting = blocked.submit(() -> unavailable.backwardsElimination(data(), new String[] {"offline"}));
        try {
            require(repeated.await(5, TimeUnit.SECONDS), "did not keep retrying");
            require(!waiting.isDone() && unavailable.ID.length == 5, "missing results did not block iteration");
        } finally {
            waiting.cancel(true);
            blocked.shutdownNow();
            require(blocked.awaitTermination(5, TimeUnit.SECONDS), "retry wait could not be cancelled");
        }
        System.out.println("PASS: immediate worker reuse, iteration barrier, candidate mapping, retries, failover, and cancellation");
    }
}
