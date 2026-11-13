import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public class RunCommand {

    public RunCommand() {
    }
    
    public void go() {
        try {
			System.out.println("Trying to fill missing values");
			File workingDirectory = new File(System.getProperty("user.dir"));
			File jar = findJar(workingDirectory);

			fill(jar, workingDirectory, "output.dat", "output_filled.txt");
			fill(jar, workingDirectory, "output2.dat", "output_filled2.txt");
			repairInvalidImputations(new File(workingDirectory, "output.dat"),
					new File(workingDirectory, "output_filled.txt"));
			repairInvalidImputations(new File(workingDirectory, "output2.dat"),
					new File(workingDirectory, "output_filled2.txt"));
			System.out.println("Missing values filled in");
        }
        catch (Exception e) {
			throw new IllegalStateException("Unable to fill missing values with JBPCAfill.jar", e);
        }
    }

	private static void fill(File jar, File workingDirectory, String inputName,
			String outputName) throws IOException, InterruptedException {
		File input = new File(workingDirectory, inputName);
		if (!input.isFile()) {
			throw new IOException("Input file does not exist: " + input.getAbsolutePath());
		}

		File output = new File(workingDirectory, outputName);
		if (!containsMissingValues(input)) {
			Files.copy(input.toPath(), output.toPath(), StandardCopyOption.REPLACE_EXISTING);
			System.out.println("JBPCAfill: no missing values in " + inputName
					+ "; copied data without imputation");
			return;
		}
		long previousModified = output.isFile() ? output.lastModified() : -1L;
		String javaExecutable = new File(new File(System.getProperty("java.home"), "bin"),
				isWindows() ? "java.exe" : "java").getAbsolutePath();

		List<String> command = new ArrayList<String>();
		command.add(javaExecutable);
		command.add("-cp");
		command.add(jar.getAbsolutePath());
		command.add("JBPCAfill");
		command.add(input.getAbsolutePath());
		command.add(output.getAbsolutePath());

		Process process = new ProcessBuilder(command)
				.directory(workingDirectory)
				.redirectErrorStream(true)
				.start();
		BufferedReader processOutput = new BufferedReader(
				new InputStreamReader(process.getInputStream()));
		try {
			String line;
			while ((line = processOutput.readLine()) != null) {
				System.out.println("JBPCAfill: " + line);
			}
		} finally {
			processOutput.close();
		}

		int exitCode = process.waitFor();
		if (exitCode != 0) {
			throw new IOException("JBPCAfill exited with code " + exitCode
					+ " while processing " + input.getAbsolutePath());
		}
		if (!output.isFile() || output.lastModified() == previousModified) {
			throw new IOException("JBPCAfill did not create a new output file: "
					+ output.getAbsolutePath());
		}
	}

	private static void repairInvalidImputations(File input, File output) throws IOException {
		double[][] original = readMatrix(input);
		double[][] filled = readMatrix(output);
		if (original.length != filled.length)
			throw new IOException("JBPCAfill changed row count in " + output.getAbsolutePath());
		for (int row = 0; row < original.length; row++) {
			if (original[row].length != filled[row].length)
				throw new IOException("JBPCAfill changed column count at row " + (row + 1)
						+ " in " + output.getAbsolutePath());
		}

		double[][] fallback = new NonnegativeKNNImputer().impute(original);
		int replaced = 0;
		for (int row = 0; row < original.length; row++) {
			for (int column = 0; column < original[row].length; column++) {
				if (original[row][column] != 999.0) {
					filled[row][column] = original[row][column];
				} else if (!Double.isFinite(filled[row][column]) || filled[row][column] <= 0
						|| filled[row][column] == 999.0) {
					filled[row][column] = fallback[row][column];
					replaced++;
				}
				if (!Double.isFinite(filled[row][column]) || filled[row][column] <= 0
						|| filled[row][column] == 999.0)
					throw new IOException("Nonpositive or invalid intensity remains at row " + (row + 1)
							+ ", column " + (column + 1) + " in " + output.getAbsolutePath());
			}
		}
		writeMatrix(output, filled);
		if (replaced > 0)
			System.out.println("JBPCAfill produced " + replaced
					+ " invalid imputation(s); replaced them with nonnegative KNN estimates.");
	}

	private static double[][] readMatrix(File file) throws IOException {
		List<double[]> rows = new ArrayList<double[]>();
		try (BufferedReader reader = new BufferedReader(new java.io.FileReader(file))) {
			String line;
			int columns = -1;
			int rowNumber = 0;
			while ((line = reader.readLine()) != null) {
				if (line.trim().isEmpty()) continue;
				String[] cells = line.trim().split("\\s+");
				if (columns < 0) columns = cells.length;
				if (cells.length != columns)
					throw new IOException("Ragged imputation matrix at row " + (rowNumber + 1)
							+ " in " + file.getAbsolutePath());
				double[] values = new double[columns];
				for (int column = 0; column < columns; column++) {
					try { values[column] = Double.parseDouble(cells[column]); }
					catch (NumberFormatException e) {
						throw new IOException("Invalid value at row " + (rowNumber + 1) + ", column "
								+ (column + 1) + " in " + file.getAbsolutePath(), e);
					}
				}
				rows.add(values);
				rowNumber++;
			}
		}
		if (rows.isEmpty()) throw new IOException("Imputation matrix is empty: " + file.getAbsolutePath());
		return rows.toArray(new double[rows.size()][]);
	}

	private static void writeMatrix(File file, double[][] matrix) throws IOException {
		try (java.io.BufferedWriter writer = Files.newBufferedWriter(file.toPath())) {
			for (double[] row : matrix) {
				for (int column = 0; column < row.length; column++) {
					if (column > 0) writer.write('\t');
					writer.write(Double.toString(row[column]));
				}
				writer.newLine();
			}
		}
	}

	private static boolean containsMissingValues(File input) throws IOException {
		BufferedReader reader = new BufferedReader(new java.io.FileReader(input));
		try {
			String line;
			while ((line = reader.readLine()) != null) {
				String[] values = line.split("\\s+");
				for (String value : values) {
					if (value.equals("999") || value.equals("999.0")) {
						return true;
					}
				}
			}
			return false;
		} finally {
			reader.close();
		}
	}

	private static File findJar(File workingDirectory) throws IOException, URISyntaxException {
		File jar = new File(workingDirectory, "lib/JBPCAfill.jar");
		if (jar.isFile()) {
			return jar;
		}

		File classLocation = new File(RunCommand.class.getProtectionDomain()
				.getCodeSource().getLocation().toURI());
		File projectDirectory = classLocation.isDirectory()
				? classLocation.getParentFile() : classLocation.getParentFile().getParentFile();
		jar = new File(projectDirectory, "lib/JBPCAfill.jar");
		if (jar.isFile()) {
			return jar;
		}
		throw new IOException("Could not find lib/JBPCAfill.jar from "
				+ workingDirectory.getAbsolutePath());
	}

	private static boolean isWindows() {
		return System.getProperty("os.name").toLowerCase().contains("win");
	}
}

