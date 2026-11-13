# protein_validation_platform
 bioinformatics project

## TCP worker (Windows or Linux)

### Standalone runnable JAR

Copy `dist/worker-client.jar` to the client machine. It bundles the worker,
its calculation classes, and Weka (including resources). No source tree, `lib`
folder, settings file, or input dataset is needed: the controller sends the matrix.
Install Java 8 or newer and run from a writable folder:

```bash
java -jar worker-client.jar
```

The headless worker waits on TCP port 6003, evaluates each incoming job, returns
the error rate to the controller, and keeps listening for further jobs. Stop it
with Ctrl+C. Use a separate working folder per process for temporary calculation
files. Optional arguments: `java -jar worker-client.jar 6003 0.0.0.0`.
Use `java -jar worker-client.jar --help` for usage. In Swing3, enter the client
machine's IP address. Both machines must use the TCP BWP1 protocol described below.
Java itself is not bundled.

Rebuild with a JDK 9+ on Linux/WSL: `bash scripts/build-worker.sh`.
Test the actual bundled JAR on Linux:
`bash scripts/test-linux.sh --jar ../../../dist/worker-client.jar`.

### Running from source

`Swing3` sends jobs to `WorkerClient` over TCP port 6003. Start the worker before
submitting its IP address in Swing3. Both ends must use this version; legacy UDP
workers are incompatible. Java 8 or newer and the bundled `lib/weka-3.7.0.jar`
are required. No graphical desktop is needed on the worker.

On Linux, from this project directory:

```bash
sudo apt update
sudo apt install default-jdk-headless
bash scripts/test-linux.sh
bash scripts/run-worker.sh
```

The launch script compiles the sources and uses `worker-data` for temporary files.
Set `WORKER_DIR` to a separate writable directory for each worker process. An
optional port and bind address can be supplied, e.g.
`bash scripts/run-worker.sh 6003 127.0.0.1`. The controller uses port 6003.
Allow inbound TCP 6003 from the controller on the worker machine. This protocol
has no authentication or encryption; use it on a trusted private network.

Windows compilation and worker launch (PowerShell):

```powershell
javac -cp 'lib/*' -d bin src/*.java
java '-Djava.awt.headless=true' -cp 'bin;lib/*' WorkerClient 6003
```

The worker calls the existing `BioMarkerSelection_Local.crossValidate` evaluator
and processes one job at a time. It preserves the evaluator's existing statistics
and paired-row/class-label conventions; the transport tests do not establish the
statistical validity of that evaluation method. Requests contain a fold count
(5 by default), an even number of rows, and finite values in a rectangular matrix.
The maximum matrix size is 10 million doubles and 100,000 rows.

The controller assigns each available worker another candidate as soon as that
worker returns its error rate, without waiting for other workers. Within each
backwards-elimination iteration, every candidate is evaluated once and results
are stored by candidate index. All results must return successfully before an
antigen is removed and the next iteration begins. Failed connections, timeouts,
worker errors, and invalid error rates put the candidate back in the shared queue
for retry by any available worker. The failing worker waits one second before
claiming more work. Retries continue without a fixed limit; unavailable workers
do not prevent healthy workers from completing outstanding candidates. If no
worker can finish a candidate, the iteration remains blocked until recovery or
cancellation. Successful results are retained; missing results are never replaced
by a default error rate.

On successful completion, the controller writes `biomarker-removal-history.csv`
in its working directory (launch from the project directory for project-local
output). Each UTF-8 CSV row contains the removal position, original label, and
the error rate evaluated with that label removed, in chronological removal order.
The file is replaced on each completed run. The final surviving label is not a
removal and is not included. The console prints up to 10 highest recorded removal
error rates in descending order, with labels and removal positions; ties retain
removal order. This follows the existing highest-error removal selection rule.
The console then prints the complete antigen list in removal order with each
removal's error rate, followed by the final surviving antigen.

Protocol BWP1 uses Java data streams (big-endian). Requests contain the magic
integer `0x42575031`, a command byte (1 ping, 2 evaluate), and a 64-bit job ID.
Evaluation adds three integers (rows, columns, folds), then row-major doubles.
Responses contain the magic integer, matching job ID, and a boolean success flag;
success adds a double, failure adds a `writeUTF` error message. Each connection
handles one request. Connection establishment has a 3-second timeout. Evaluation
has a 30-minute total deadline, configurable on the controller with
`-Dworker.timeoutMillis=1800000`.

## WSL testing on this machine

Verified on September 22, 2026 with Ubuntu under WSL and OpenJDK 25.0.4.1.
Linux compilation and the full TCP regression suite passed. A Windows Java
25.0.4 controller also passed against the Linux worker using Ubuntu's IP address.
Logs are in `diagnostics/network-fixes/linux-test.log` and
`diagnostics/network-fixes/windows-to-wsl-test.log`.

For a new machine, install Ubuntu in PowerShell:

```powershell
wsl --install -d Ubuntu
```

Complete Ubuntu's first-run setup, then run inside Ubuntu:

```bash
sudo apt update
sudo apt install default-jdk-headless
cd /mnt/c/Users/miked/eclipse-workspace/workspace/platform
bash scripts/test-linux.sh
```

This compiles under Linux, launches a headless worker in a separate JVM, compares
repeated remote Weka results to local evaluation, and tests malformed requests,
large matrices, response job IDs, and timeouts. Results are saved to
`diagnostics/network-fixes/linux-test.log`.

To test Windows-to-WSL execution, leave `bash scripts/run-worker.sh` running in
Ubuntu, then run from the project directory in PowerShell:

```powershell
javac -cp 'lib/*' -d diagnostics/network-fixes/classes src/*.java diagnostics/network-fixes/NetworkRegression.java
Set-Location diagnostics/network-fixes
java '-Djava.awt.headless=true' -cp 'classes;../../lib/*' NetworkRegression 127.0.0.1 6003
```

Use Ubuntu's IP from `wsl -d Ubuntu -- hostname -I` if localhost forwarding is
unavailable; this test used that IP. Both Linux and Windows-to-WSL tests passed
handshakes, repeated local/remote Weka result parity, and malformed/truncated
request recovery. The suites also passed mock-peer checks for large transfers,
response job IDs, nonfinite results, and deadlines. Weka printed optional JDBC
driver warnings, which did not prevent evaluation.

On this machine Ubuntu and the headless JDK are already installed. Testing used
`wsl -d Ubuntu -u root` without creating a personal Linux account.
