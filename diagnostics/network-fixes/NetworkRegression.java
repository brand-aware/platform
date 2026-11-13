import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Standalone regression suite; optional host/port tests an already running worker. */
public class NetworkRegression {
    static void require(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    static double[][] matrix() {
        double[][] data = new double[10][3];
        for (int i = 0; i < data.length; i++)
            for (int j = 0; j < data[i].length; j++) data[i][j] = (i % 2 == 0 ? 1 : 10) + i * .01 + j * .1;
        return data;
    }
    static double remote(String host, int port, double[][] data) throws Exception {
        ServerThread job = new ServerThread(host, port, 10000);
        job.setData(data, 0); job.run();
        require(job.getIsDone(), "job completion");
        return job.getErrorRate();
    }
    public static void main(String[] args) throws Exception {
        System.out.println("Runtime: " + System.getProperty("os.name") + " Java " + System.getProperty("java.version"));
        Process process = null;
        boolean jarMode = args.length == 2 && "--jar".equals(args[0]);
        String host = !jarMode && args.length > 0 ? args[0] : "127.0.0.1";
        int port;
        if (!jarMode && args.length > 1) port = Integer.parseInt(args[1]);
        else {
            String javaExecutable = Paths.get(System.getProperty("java.home"), "bin", System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString();
            String[] entries = System.getProperty("java.class.path").split(java.util.regex.Pattern.quote(File.pathSeparator));
            for (int i = 0; i < entries.length; i++) entries[i] = Paths.get(entries[i]).toAbsolutePath().toString();
            Path work = Files.createTempDirectory("tcp-worker-test-");
            ProcessBuilder worker = jarMode
                    ? new ProcessBuilder(javaExecutable, "-jar", Paths.get(args[1]).toAbsolutePath().toString(), "0", "127.0.0.1")
                    : new ProcessBuilder(javaExecutable, "-Djava.awt.headless=true", "-cp", String.join(File.pathSeparator, entries),
                            "WorkerClient", "0", "127.0.0.1");
            process = worker.directory(work.toFile()).redirectErrorStream(true).start();
            BufferedReader output = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String ready = output.readLine();
            require(ready != null && ready.startsWith("Worker listening"), "worker startup: " + ready);
            System.out.println(ready);
            port = Integer.parseInt(ready.split(":")[1].split(" ")[0]);
            Thread drain = new Thread(() -> { try { while (output.readLine() != null) { } } catch (IOException ignored) { } });
            drain.setDaemon(true); drain.start();
        }
        try {
            require(new ServerThread(host, port, 3000).checkConnection(), "TCP handshake");
            double expected = new BioMarkerSelection_Local().crossValidate(matrix(), 5);
            for (int i = 0; i < 3; i++) require(remote(host, port, matrix()) == expected, "local/remote parity");
            // Invalid dimensions must return an explicit error, then allow another job.
            try (Socket socket = new Socket(host, port)) {
                socket.setSoTimeout(3000);
                DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                WorkerProtocol.header(out, WorkerProtocol.EVALUATE, 77);
                out.writeInt(Integer.MAX_VALUE); out.writeInt(Integer.MAX_VALUE); out.writeInt(5); out.flush();
                try { WorkerProtocol.result(new DataInputStream(socket.getInputStream()), 77); throw new AssertionError("accepted dimensions"); }
                catch (IOException expectedFailure) { require(expectedFailure.getMessage().contains("Worker failed"), "explicit failure"); }
            }
            // Truncated request must not kill the worker.
            try (Socket socket = new Socket(host, port)) { socket.getOutputStream().write(1); }
            require(new ServerThread(host, port, 3000).checkConnection(), "worker recovery");
            require(remote(host, port, matrix()) == expected, "recovery parity");
            System.out.println("PASS: real worker handshake, repeated Weka evaluation/parity, malformed and truncated request recovery");
        } finally {
            if (process != null) { process.destroy(); if (!process.waitFor(5, TimeUnit.SECONDS)) process.destroyForcibly(); }
        }
        mockTests();
    }
    static void mockTests() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (ServerSocket listener = new ServerSocket(0)) {
            listener.setSoTimeout(3000);
            Future<?> peer = executor.submit(() -> {
                try {
                    for (int mode = 0; mode < 4; mode++) {
                        try (Socket socket = listener.accept()) {
                            socket.setSoTimeout(3000);
                            DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
                            require(in.readInt() == WorkerProtocol.MAGIC && in.readByte() == WorkerProtocol.EVALUATE, "request header");
                            long id = in.readLong();
                            int rows = in.readInt(), columns = in.readInt(); in.readInt();
                            for (int i = 0; i < rows; i++) for (int j = 0; j < columns; j++) require(in.readDouble() == i + j, "large matrix integrity");
                            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                            if (mode == 0) WorkerProtocol.success(out, id, .25);
                            if (mode == 1) WorkerProtocol.success(out, id + 1, .25);
                            if (mode == 2) WorkerProtocol.success(out, id, Double.NaN);
                            if (mode == 3) { try { while (in.read() != -1) { } } catch (IOException expected) { } }
                        }
                    }
                } catch (Exception e) { throw new RuntimeException(e); }
            });
            double[][] large = new double[10][9000];
            for (int i = 0; i < large.length; i++) for (int j = 0; j < large[i].length; j++) large[i][j] = i + j;
            for (int mode = 0; mode < 4; mode++) {
                ServerThread job = new ServerThread("127.0.0.1", listener.getLocalPort(), mode == 3 ? 1000 : 5000);
                job.setData(large, 0); job.run();
                if (mode == 0) require(job.getErrorRate() == .25, "large transfer");
                else { try { job.getErrorRate(); throw new AssertionError("invalid result accepted"); } catch (IllegalStateException expected) { } }
            }
            peer.get(5, TimeUnit.SECONDS);
        } finally { executor.shutdownNow(); }
        System.out.println("PASS: rows over UDP size limit, job-ID validation, nonfinite response rejection, deadline");
    }
}

