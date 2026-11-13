import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Starts a standalone local worker when the controller itself is configured as a worker. */
public final class LocalWorkerProcess {
    private static volatile Process ownedProcess;

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            public void run() {
                Process process = ownedProcess;
                if (process != null && process.isAlive()) process.destroy();
            }
        }, "local-worker-shutdown"));
    }

    private LocalWorkerProcess() { }

    public static synchronized void ensureRunning(String address) throws IOException {
        InetAddress bindAddress = InetAddress.getByName(address.trim());
        if (!bindAddress.isLoopbackAddress())
            throw new IllegalArgumentException("Local worker address must be a loopback address: " + address);

        IOException lastFailure = null;
        try {
            int processors = new ServerThread(address, 1000).getRemoteProcessorCount();
            System.out.println("Using existing local worker at " + address + " ("
                    + processors + " logical processors)");
            return;
        } catch (IOException failure) {
            lastFailure = failure;
        }

        if (ownedProcess != null && !ownedProcess.isAlive()) ownedProcess = null;
        File projectDirectory = findProjectDirectory();
        File workerJar = new File(projectDirectory, "dist/worker-client.jar");
        if (!workerJar.isFile())
            throw new IOException("Standalone local worker JAR not found: " + workerJar.getAbsolutePath(), lastFailure);

        Path workerDirectory = Paths.get(projectDirectory.getAbsolutePath(), "bin", "worker-local-data");
        Files.createDirectories(workerDirectory);
        File java = new File(new File(System.getProperty("java.home"), "bin"),
                System.getProperty("os.name", "").toLowerCase().contains("win") ? "java.exe" : "java");
        ProcessBuilder builder = new ProcessBuilder(java.getAbsolutePath(),
                "-Djava.awt.headless=true", "-jar", workerJar.getAbsolutePath(),
                Integer.toString(WorkerProtocol.PORT), bindAddress.getHostAddress());
        builder.directory(workerDirectory.toFile());
        builder.redirectErrorStream(true);
        builder.inheritIO();
        ownedProcess = builder.start();

        long deadline = System.currentTimeMillis() + 10000;
        while (System.currentTimeMillis() < deadline) {
            if (!ownedProcess.isAlive())
                throw new IOException("Local worker exited during startup; check its console output", lastFailure);
            try {
                int processors = new ServerThread(address, 1000).getRemoteProcessorCount();
                System.out.println("Started local worker at " + address + " ("
                        + processors + " logical processors)");
                return;
            } catch (IOException failure) {
                lastFailure = failure;
                try { Thread.sleep(200); }
                catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while starting the local worker", interrupted);
                }
            }
        }
        if (ownedProcess.isAlive()) ownedProcess.destroy();
        throw new IOException("Local worker did not answer BWP1 on " + address + ":" + WorkerProtocol.PORT,
                lastFailure);
    }

    private static File findProjectDirectory() throws IOException {
        File workingDirectory = new File(System.getProperty("user.dir"));
        if (new File(workingDirectory, "dist/worker-client.jar").isFile()) return workingDirectory;
        try {
            File location = new File(LocalWorkerProcess.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            File directory = location.isDirectory() ? location : location.getParentFile();
            File project = directory.getParentFile();
            if (project != null && new File(project, "dist/worker-client.jar").isFile()) return project;
        } catch (URISyntaxException e) {
            throw new IOException("Could not locate the project for the local worker", e);
        }
        throw new IOException("Could not locate dist/worker-client.jar from "
                + workingDirectory.getAbsolutePath());
    }
}
