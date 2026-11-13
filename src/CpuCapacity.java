import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/** Reads the machine's logical CPU count and derives a conservative worker cap. */
public final class CpuCapacity {
    private CpuCapacity() { }

    public static int systemProcessorCount() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            int count = parsePositive(System.getenv("NUMBER_OF_PROCESSORS"));
            if (count > 0) return count;
        } else if (os.contains("linux")) {
            int count = linuxProcessorCount();
            if (count > 0) return count;
        }
        return Math.max(1, Runtime.getRuntime().availableProcessors());
    }

    public static int threadLimit(int processors, int utilizationPercent) {
        if (processors < 1) processors = 1;
        if (utilizationPercent < 1 || utilizationPercent > 100)
            throw new IllegalArgumentException("CPU utilization cap must be between 1 and 100 percent");
        return Math.max(1, (processors * utilizationPercent) / 100);
    }

    private static int parsePositive(String value) {
        if (value == null) return -1;
        try {
            int count = Integer.parseInt(value.trim());
            return count > 0 ? count : -1;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static int linuxProcessorCount() {
        int count = 0;
        try (BufferedReader reader = Files.newBufferedReader(Paths.get("/proc/cpuinfo"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("processor") && line.indexOf(':') >= 0) count++;
            }
        } catch (IOException ignored) {
            return -1;
        }
        return count;
    }
}
