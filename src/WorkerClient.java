import java.io.*;
import java.net.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** Headless worker. Give each process its own writable working directory. */
public class WorkerClient {
    private final BioMarkerSelection_Local evaluator = new BioMarkerSelection_Local();
    private static final AtomicInteger ACTIVE_JOBS = new AtomicInteger();
    private static final AtomicLong COMPLETED_JOBS = new AtomicLong();
    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        if (args.length == 1 && ("--help".equals(args[0]) || "-h".equals(args[0]))) {
            System.out.println("Usage: java -jar worker-client.jar [port [bind-address]]");
            System.out.println("Defaults: TCP port 6003, bind 0.0.0.0. Run in a writable directory.");
            System.out.println("Waits for BWP1 jobs, returns error rates, and continues listening. Ctrl+C stops it.");
            return;
        }
        int port = args.length > 0 ? Integer.parseInt(args[0]) : WorkerProtocol.PORT;
        String bind = args.length > 1 ? args[1] : "0.0.0.0";
        WorkerClient worker = new WorkerClient();
        int workerThreadLimit = CpuCapacity.threadLimit(CpuCapacity.systemProcessorCount(), 80);
        int workerThreads = Math.max(1,
                Math.min(workerThreadLimit, Integer.getInteger("worker.concurrentJobs", workerThreadLimit)));
        final ExecutorService requests = Executors.newFixedThreadPool(workerThreads);
        ScheduledExecutorService metrics = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "worker-metrics");
            thread.setDaemon(true);
            return thread;
        });
        metrics.scheduleAtFixedRate(new Runnable() {
            public void run() {
                int activeJobs = ACTIVE_JOBS.get();
                if (activeJobs > 0) {
                    System.out.println("Worker metrics: activeJobs=" + activeJobs
                            + ", completedJobs=" + COMPLETED_JOBS.get()
                            + ", activeFolds=" + BioMarkerSelection_Local.getActiveFoldThreadCount()
                            + "/" + BioMarkerSelection_Local.getFoldThreadCount()
                            + ", queuedFolds=" + BioMarkerSelection_Local.getQueuedFoldTaskCount());
                }
            }
        }, 1, 1, TimeUnit.SECONDS);
        try (ServerSocket listener = new ServerSocket()) {
            listener.bind(new InetSocketAddress(bind, port));
            System.out.println("Worker listening on " + bind + ":" + listener.getLocalPort()
                    + " (TCP BWP1, " + System.getProperty("os.name") + "; up to "
                    + workerThreads + " concurrent jobs; "
                    + BioMarkerSelection_Local.getFoldThreadCount() + " fold threads; system has "
                    + BioMarkerSelection_Local.getSystemProcessorCount() + " logical processors)");
            while (!Thread.currentThread().isInterrupted()) {
                final Socket accepted = listener.accept();
                requests.execute(new Runnable() {
                    public void run() {
                        try (Socket socket = accepted) {
                            socket.setSoTimeout(30000);
                            worker.handle(socket);
                        } catch (IOException | RuntimeException e) {
                            System.err.println("Request failed: " + e);
                            e.printStackTrace(System.err);
                        }
                    }
                });
            }
        } finally {
            metrics.shutdownNow();
            requests.shutdownNow();
        }
    }
    public void handle(Socket socket) throws IOException {
        DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        if (in.readInt() != WorkerProtocol.MAGIC) throw new IOException("Unsupported protocol version");
        int command = in.readUnsignedByte();
        long job = in.readLong();
        try {
            if (command == WorkerProtocol.PING) {
                WorkerProtocol.success(out, job, CpuCapacity.systemProcessorCount());
                return;
            }
            if (command != WorkerProtocol.EVALUATE) throw new IOException("Unknown command");
            int rows = in.readInt(), columns = in.readInt(), folds = in.readInt();
            WorkerProtocol.dimensions(rows, columns, folds);
            double[][] matrix = new double[rows][columns];
            for (double[] row : matrix) for (int j = 0; j < columns; j++) row[j] = in.readDouble();
            WorkerProtocol.validate(matrix, folds);
            long started = System.nanoTime();
            ACTIVE_JOBS.incrementAndGet();
            try {
                double result = evaluator.crossValidate(matrix, folds);
                if (!Double.isFinite(result) || result < 0 || result > 1) throw new IOException("Invalid evaluator result: " + result);
                WorkerProtocol.success(out, job, result);
                COMPLETED_JOBS.incrementAndGet();
                System.out.println("Job " + job + " completed in "
                        + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
                        + " ms (error rate " + result + ")");
            } finally {
                ACTIVE_JOBS.decrementAndGet();
            }
        } catch (IOException | RuntimeException e) {
            System.err.println("Job " + job + " failed: " + e);
            e.printStackTrace(System.err);
            WorkerProtocol.failure(out, job, e.toString());
        }
    }
}
