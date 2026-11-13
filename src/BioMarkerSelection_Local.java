import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import weka.classifiers.Evaluation;
import weka.classifiers.trees.RandomForest;
import weka.core.Instances;
import weka.core.converters.ArffLoader;
import weka.core.converters.CSVLoader;
import weka.core.converters.ArffSaver;
import weka.filters.unsupervised.attribute.NumericToNominal;

public class BioMarkerSelection_Local {
	
	private double[][] last;
	private String[] ID;
	private int[] testPatients, trainPatients;
	private double[] crossValidate;
	private Antigens antList;
	private int delete =0;
	private static final AtomicLong count = new AtomicLong();
	private static final int SYSTEM_PROCESSORS = CpuCapacity.systemProcessorCount();
	private static final int FOLD_THREAD_LIMIT = CpuCapacity.threadLimit(SYSTEM_PROCESSORS, 80);
	private static final int FOLD_THREAD_COUNT = Math.max(1,
			Math.min(FOLD_THREAD_LIMIT, Integer.getInteger("evaluation.foldThreads", FOLD_THREAD_LIMIT)));
	private static final ThreadPoolExecutor FOLD_EXECUTOR = new ThreadPoolExecutor(
			FOLD_THREAD_COUNT, FOLD_THREAD_COUNT, 0L, TimeUnit.MILLISECONDS,
			new LinkedBlockingQueue<Runnable>(), new java.util.concurrent.ThreadFactory() {
				private final AtomicLong threadNumber = new AtomicLong();
				public Thread newThread(Runnable task) {
					Thread thread = new Thread(task, "cross-validation-fold-" + threadNumber.incrementAndGet());
					thread.setDaemon(true);
					return thread;
				}
			});

	public static int getFoldThreadCount() { return FOLD_THREAD_COUNT; }
	public static int getSystemProcessorCount() { return SYSTEM_PROCESSORS; }
	public static int getActiveFoldThreadCount() { return FOLD_EXECUTOR.getActiveCount(); }
	public static int getQueuedFoldTaskCount() { return FOLD_EXECUTOR.getQueue().size(); }
	
	
	public BioMarkerSelection_Local(String[] idList, int[] test, int[] train)
	{
		//Requires String[] of ID for rows of antigens
		ID = idList;
		testPatients = test;
		trainPatients = train;
		
	}
	
	public BioMarkerSelection_Local()
	{
		//Empty constructor to work on remote systems
	}

	public void setID(String[] idList)
	{
		ID = idList;
	}
	
	public void setTestPat(int[] patients)
	{
		testPatients = patients;
	}
	
	public void setTrainPat(int[] patients)
	{
		trainPatients = patients;
	}
	
	public void backwardsElimination(double[][][] data)
	{
		
		int removePosition = 0;
		int size = data[0].length - 1;
		antList = new Antigens(size);
		int workerCount = Math.max(1, Math.min(16, Integer.getInteger("local.workerThreads", 16)));
		ExecutorService executor = Executors.newFixedThreadPool(workerCount);
		System.out.println("Local elimination using " + workerCount + " worker threads");
		
		antList.print();
		try {
			for(int x = 0; x < size; x++)
		  {
				int remainingAntigens = data[0].length;
				if (x == 0 || x % 5 == 0 || x == size - 1) {
					System.out.println("Elimination progress: " + remainingAntigens
							+ " antigens remain; " + Math.max(0, remainingAntigens - 1)
							+ " still to remove (removal " + (x + 1) + " of " + size + ")");
				}
			crossValidate = new double[data[0].length];
			final double[][][] iterationData = data;
			List<Callable<Double>> tasks = new ArrayList<Callable<Double>>();
			for(int y = 0; y < iterationData[0].length; y++) {
				final int candidate = y;
				tasks.add(new Callable<Double>() {
					public Double call() {
						double[][][] candidateData = removeRowCopy(iterationData, candidate);
						double[][] formattedData = threeDtoTwoD(candidateData);
						return crossValidate(formattedData, 5);
					}
				});
			}
			try {
				List<Future<Double>> results = executor.invokeAll(tasks);
				for(int y = 0; y < results.size(); y++) {
					crossValidate[y] = results.get(y).get();
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException("Local elimination was interrupted", e);
			} catch (ExecutionException e) {
				throw new IllegalStateException("A local elimination worker failed", e.getCause());
			}
					
			//calculate best candidate for removal in the crossValidate error set
			removePosition = findBest(crossValidate);
			//remove one antigen from the list
			
			data = removeRow(data, removePosition,crossValidate[removePosition]);
			//test print 
			for(int i = 0; i<crossValidate.length; i++)
			{
				System.out.println("\n\n\n\n\nPosition: " + (i + 1) + " error: "+crossValidate[i]);
			}
			antList.print();
			
		  }
		  antList.print();
		} finally {
			executor.shutdownNow();
		}
	}

	private double[][][] removeRowCopy(double[][][] previous, int position)
	{
		double[][][] result = new double[previous.length][previous[0].length - 1][previous[0][0].length];
		for(int patient = 0; patient < previous.length; patient++) {
			for(int marker = 0; marker < previous[0].length; marker++) {
				if(marker != position) {
					int destination = marker < position ? marker : marker - 1;
					System.arraycopy(previous[patient][marker], 0, result[patient][destination], 0,
						previous[patient][marker].length);
				}
			}
		}
		return result;
	}
	
	public int findBest(double[] list)
	{
		//return the position of the best value in the set

		double best = 10000000;
		int bestPosition = 0;
		
		for(int x = 0; x < list.length; x++)
		{	
			if(list[x] < best)
			{
				best = list[x];
				bestPosition = x;
			}
		}
		
		return bestPosition;
	}

	public double[][][] removeRow(double[][][] oldRow,  int position, double error)
	{
		
		double[][][] newRow = new double[oldRow.length][oldRow[0].length - 1][oldRow[0][0].length];
		int removed =0;

		for(int b = 0; b < oldRow[0].length; b++)
		{
			if(b < position){
				for(int c = 0; c < oldRow.length; c++){
					newRow[c][b] = oldRow[c][b];
				}
			}	
			else if(b > position){
				for(int d = 0; d < oldRow.length; d++){
					newRow[d][b - 1] = oldRow[d][b];
				}
			}
			else
			{
				removed = b;
			}
		}
		
		antList.add(new Antigens(error, position, ID[position]));
		removeID(position);
		
		return newRow;
	}
	
	public void removeID(int position)
	{
		
		//store new string in shorter, temp id list
		String[] newList = new String[ID.length - 1];
		
		for(int i = 0; i < ID.length; i++)
		{
			if (i < position){
				newList[i] = ID[i];
			}
			else if (i > position){
				newList[i - 1] = ID[i];
			}
		}
		ID = newList;
		
	}
	
	public double[][][] removeRowKeepLast(double[][][] previous, double position)
	{
		last = new double[previous.length][previous[0][0].length];
		double[][][] newData = new double[previous.length][previous[0].length - 1][previous[0][0].length];
		for(int b = 0; b < previous[0].length; b++)
		{
			if(b < position){
				for(int c = 0; c < previous.length; c++){
					newData[c][b] = previous[c][b];
				}
			}
			else if(b > position){
				for(int d = 0; d < previous.length; d++){
					newData[d][b - 1] = previous[d][b];
				}
			}
			else if(b == position){
				for(int e = 0; e < previous.length; e++){
					last[e] = previous[e][b];
				}
			}
			else
				System.out.println("removeRow_keepLast function BROKEN....");
		}

		return newData;
	}
	
	public double[][][] addRow(double[][][] previous, double[][] row, double position)
	{

		double[][][] newData = new double[previous.length][previous[0].length + 1][previous[0][0].length];

		for(int b = 0; b < newData[0].length; b++)
		{
			if(b < position){
				for(int c = 0; c < previous.length; c++){
					newData[c][b] = previous[c][b];
				}
			}
			else if(b > position){
				for(int d = 0; d < previous.length; d++){
					newData[d][b] = previous[d][b - 1];
				}
			}
			else{
				for(int e = 0; e < previous.length; e++){
					newData[e][b] = row[e];
				}
			}
		}

		return newData;
	}
	
	public double[][] threeDtoTwoD(double[][][] old)
	{
		//System.out.println("threeDtoTwoD  --  function");
		//System.out.println("old.length: "+old.length+", old[0].length: "+old[0].length+", old[0][0].length: "+old[0][0].length);
		double[][] formatted = new double[old.length * old[0][0].length][old[0].length];
		int position = 0;
		
		for(int x = 0; x < old.length; x++)
		{
			for(int z = 0; z < old[0][0].length; z++)
			{
				for(int y = 0; y < old[0].length; y++)
				{
					formatted[position][y] = old[x][y][z];
				}
				position++;
			}
		}
		//System.out.println("temp.length: "+temp.length+", temp[0].length: "+temp[0].length);

		return formatted;
	}
	
	public double crossValidate(double[][] data, int fold)
	{
		return crossValidDefault(data, fold);
	}
	
	public double RandomForest(File trainA, int testStart, int testStop)
	{
		//System.out.println("RandomForestTest  --  function");
		
		ArffLoader arff = new ArffLoader();
        try {
			arff.setSource(trainA);
		} catch (IOException e) {
			throw new IllegalStateException("Random forest evaluation failed", e);
		}
        
        Instances nomi = null;
		try {
			nomi = arff.getDataSet();
		} catch (IOException e) {
			throw new IllegalStateException("Random forest evaluation failed", e);
		}
     
        //System.out.println("Instances: " + nomi.numInstances() + " Attributes: "+nomi.numAttributes());
        nomi.setClassIndex(nomi.numAttributes() - 1);
        NumericToNominal dat = new NumericToNominal();
        // Keep antigen measurements numeric; only the class label is nominal.
        dat.setAttributeIndices("last");
        
        try {
			dat.setInputFormat(nomi);
		} catch (Exception e) {
			throw new IllegalStateException("Random forest evaluation failed", e);
		}
        
        try {
			nomi = NumericToNominal.useFilter(nomi, dat);
		} catch (Exception e) {
			throw new IllegalStateException("Random forest evaluation failed", e);
		}
        
        if (testStart < 0 || testStop > nomi.numInstances() || testStart >= testStop) {
            throw new IllegalArgumentException("Invalid test fold bounds: " + testStart + ".." + testStop);
        }
        Instances trainSet = new Instances(nomi, nomi.numInstances() - (testStop - testStart));
        Instances testSet = new Instances(nomi, testStop - testStart);
        for (int i = 0; i < nomi.numInstances(); i++) {
            if (i >= testStart && i < testStop) testSet.add(nomi.instance(i));
            else trainSet.add(nomi.instance(i));
        }
        
        trainSet.classAttribute();
        trainSet.setClassIndex(nomi.numAttributes() - 1);
        testSet.classAttribute();
        testSet.setClassIndex(nomi.numAttributes() - 1);
        
        RandomForest tree = new RandomForest();
		//tree.setNumTrees(500);
		tree.setSeed(123456);
		//tree.setDebug(true);
		try {
			tree.buildClassifier(trainSet);
		} catch (Exception e) {
			throw new IllegalStateException("Random forest evaluation failed", e);
		}
		
		Evaluation evaluation1 = null;
		try {
			evaluation1 = new Evaluation(trainSet);
		} catch (Exception e) {
			throw new IllegalStateException("Random forest evaluation failed", e);
		}
		try {
			evaluation1.evaluateModel(tree, testSet);
		} catch (Exception e) {
			throw new IllegalStateException("Random forest evaluation failed", e);
		}
		double[][] confusion = evaluation1.confusionMatrix();
		StringBuilder details = new StringBuilder();
		details.append("Fold samples [").append(testStart).append(',').append(testStop)
				.append(") predictions (actual -> predicted):");
		for (int i = 0; i < testSet.numInstances(); i++) {
			weka.core.Instance instance = testSet.instance(i);
			double prediction;
			try {
				prediction = tree.classifyInstance(instance);
			} catch (Exception e) {
				throw new IllegalStateException("Could not produce fold prediction", e);
			}
			details.append(" ").append(testStart + i).append(": ")
					.append(classValueName(testSet, instance.classValue())).append(" -> ")
					.append(classValueName(testSet, prediction)).append(';');
		}
		details.append(" confusion matrix (actual rows, predicted columns):");
		for (double[] row : confusion) {
			details.append(" [");
			for (int column = 0; column < row.length; column++) {
				if (column > 0) details.append(',');
				details.append(row[column]);
			}
			details.append(']');
		}
		synchronized (System.out) {
			System.out.println(details.toString());
		}
		return evaluation1.errorRate();
	}

	private String classValueName(Instances data, double value) {
		if (Double.isNaN(value)) return "?";
		int index = (int) value;
		if (index < 0 || index >= data.classAttribute().numValues()) return Double.toString(value);
		return data.classAttribute().value(index);
	}
	
	public double[][] createRandomGroups(double[][] data)
	{
		//temporary data storage for new data grouping
		double[][] temp = new double[data.length][data[0].length];
		
		//create random list of patient ids (/2 to compensate for 2 mixes)
		//System.out.println("data.length = "+data.length);
		int[] order = new int[data.length / 2];
		order = randomizePatients(order);
		for(int x = 0; x < data[0].length; x++)
		{
			for(int y = 0; y < order.length; y++)
			{
				temp[y * 2][x] = data[order[y] * 2][x];
				temp[(y * 2) + 1][x] = data[(order[y] * 2) + 1][x];
			}
		}
		
		
		return temp;
	}
	
	public int[] randomizePatients(int[] patients)
	{
		//return a random list for patient ids;
		int[] left = new int[patients.length];
		
		//create a new order of patients to return and a counter to loop through the list;
		int[] newPatients = new int[patients.length];
		int counter = 0;
		
		for(int a = 0; a < left.length; a++){
			left[a] = 0;
		}
		while(anyLeft(left) && counter < newPatients.length)
		{
			int x = (int)(Math.random() * patients.length);
			if(x < patients.length)
			{
				if(left[x] == 0)
				{
					newPatients[counter] = x;
					left[x]++;
				counter++;
				}
			}
			x=0;
		}
		
		return newPatients;
	}
	
	public boolean anyLeft(int[] left)
	{
		//System.out.println("anyLeft  --  function");
		for(int a = 0; a < left.length; a++){
			if(left[a] == 0){
				return true;
			}
		}
		return false;
	}
	
	public double[][] transpose(double[][] data)
	{
		//creates a temporary double[][], the inverse dimensions of data
		double[][] temp = new double[data[0].length][data.length];
		
		//loop though and store data in new array
		for(int x = 0; x < data.length; x++)
		{
			for(int y = 0; y < data[0].length; y++)
			{
				temp[y][x] = data[x][y];
			}
		}
		
		return temp;
	}
	
	public void printMatrix(double[][] data, PrintStream object)
	{
		object.println("printMatrix  --  function");
		object.println("data.length: " + data.length + ", data[0].length: " + data[0].length);
		for(int y = 0; y < data[0].length; y++)
		{
			object.print(y + ". ");
			
			for(int x = 0; x < data.length; x++)
			{
				object.print(data[x][y] + ", ");
			}
			object.println();
		}
	}
	
	public double crossValidDefault(double[][] data, int fold)
	{
		final double[][] transposed = transpose(data);
		if (fold < 2 || transposed.length == 0 || transposed[0].length % 2 != 0
				|| fold > transposed[0].length / 2) {
			throw new IllegalArgumentException("Fold count must be between 2 and the number of paired samples");
		}
		final int pairCount = transposed[0].length / 2;
		List<Callable<Double>> tasks = new ArrayList<Callable<Double>>();
		for (int foldIndex = 0; foldIndex < fold; foldIndex++) {
			final int currentFold = foldIndex;
			final int start = 2 * (currentFold * pairCount / fold);
			final int stop = 2 * ((currentFold + 1) * pairCount / fold);
			final long fileIndex = count.incrementAndGet();
			tasks.add(new Callable<Double>() {
				public Double call() {
					try {
						prepCSV(start, stop, transposed, fold, fileIndex);
						return RandomForest(createNewArff(new File("data_temp" + fileIndex + ".csv"), fileIndex), start, stop);
					} catch (Exception e) {
						throw new IllegalStateException("Cross-validation fold " + fileIndex + " failed", e);
					} finally {
						TemporaryDataFiles.cleanupFold(fileIndex);
					}
				}
			});
		}

		double errorRate = 0;
		try {
			for (Future<Double> result : FOLD_EXECUTOR.invokeAll(tasks)) errorRate += result.get();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Cross-validation was interrupted", e);
		} catch (ExecutionException e) {
			throw new IllegalStateException("CSV or cross-validation operation failed", e.getCause());
		}
		return errorRate / fold;
	}
	
	public File createNewArff(File Data, long fileIndex) throws IOException
	{
		
		CSVLoader loader = new CSVLoader();
		
		//File ror = new File("data.arff");
		
		
        Instances data;
        // Own the stream so parse failures also release the Windows file handle.
        try (FileInputStream input = new FileInputStream(Data)) {
            loader.setSource(input);
            data = loader.getDataSet();
        } 
       
        ArffSaver saver = new ArffSaver();
        
		String fileName = "data" + fileIndex + ".arff";
        
        try
        {
	        saver.setInstances(data);       
	        saver.setFile(new File(fileName));
	        saver.writeBatch(); 
	        
        
        }
        catch(Exception e)
        {
        	//System.out.println("error: " + e + "\n");
        	throw new IOException("Unable to save ARFF file " + fileName, e);
        }
       
    	File trainA =new File(fileName);
    	
	        
    	
    	
        return trainA;
	}
	
	public void delete(int fold)
	{
		
            String FileDelete = "data" + (0) + ".arff";
            File deleteOld = new File(FileDelete);
            deleteOld.delete();
            
        	FileDelete= "data" + (4) +".arff";
        	deleteOld = new File(FileDelete);
        	deleteOld.delete();
        	FileDelete= "data_temp.csv";
        	deleteOld = new File(FileDelete);
        	deleteOld.delete();
        	FileDelete= "data" + (3) + ".arff";
        	deleteOld = new File(FileDelete);
        	deleteOld.delete();
        	FileDelete= "data" + (2) + ".arff";
        	deleteOld = new File(FileDelete);
        	deleteOld.delete();
        	FileDelete= "data" + (1) + ".arff";
        	deleteOld = new File(FileDelete);
        	deleteOld.delete();
	}
	
	public void prepCSV(int start, int stop, double[][] data, int fold, long index)
	{
		if (data == null || data.length == 0 || data[0] == null) {
			throw new IllegalArgumentException("Cannot write an empty evaluation matrix");
		}
		final int featureCount = data.length;
		final int sampleCount = data[0].length;
		for (int feature = 0; feature < featureCount; feature++) {
			if (data[feature] == null || data[feature].length != sampleCount) {
				throw new IllegalArgumentException("Evaluation matrix is not rectangular at feature " + feature);
			}
		}
		try (PrintStream csv = new PrintStream(new FileOutputStream("data_temp" + index + ".csv"))) {
			for (int feature = 0; feature < featureCount; feature++) {
				if (feature > 0) csv.print(',');
				csv.print(feature);
			}
			csv.println(",CLASS");

			for (int sample = 0; sample < sampleCount; sample++) {
				for (int feature = 0; feature < featureCount; feature++) {
					if (feature > 0) csv.print(',');
					csv.print(Double.toString(data[feature][sample]));
				}
				csv.println(sample % 2 == 0 ? ",1" : ",2");
			}
			if (csv.checkError()) throw new IOException("CSV output failed for fold " + index);
		} catch (IOException e) {
			throw new IllegalStateException("Unable to write CSV for cross-validation fold " + index, e);
		}
	}
	
	//Paired T-Test type of selection (simplest comparison of data)
	public double[] pairedTTest(double[][][] res) 
	{ 
		System.out.println("pairedTTest  --  function");
		/* 
		 	* # t-test for each gene 
			x-- x[mem==1] = [log(pca_{11}/bph_{11}),..., log(pca_{1n}/bph_{1n})] (mix 1) 
			x[mem==2] = [log(bph_{21}/pc_{21}),..., log(bph_{2n}/pc_{2n})] (mix 2)  
		  
			# gammai=[log(pca_{1i}/bph_{1i})+log(pca_{2i}/bph_{2i})]/2 
			# log(pca_{1i}/bph_{1i})~N(mu_d, sigma_d^2) 
			# log(pca_{2i}/bph_{2i})~N(mu_d, sigma_d^2) 
			# gammai~N(mu_d, sigma_d^2/2) 
		 */
		int listSize = res.length; 
		double sum=0, s=0, sd=0, dMean=0; 
		//double[][][] res = new double[data[0].length][data.length][2]; 
		double[] d = new double[listSize]; 
		double[] total = new double[res[0].length];
		System.out.println("res.length = "+res.length);
		System.out.println("res.length[0] = "+res[0].length);
		System.out.println("res.length[0][0] = "+res[0][0].length);
		for (int w=0; w< res[0].length; w++) 
		{ 
			sum=0; s=0; sd=0; dMean=0; 
			/*
			for(int x=0; x<listSize; x++) 
			{ 
				res[x][w][0] = Math.log(data[x][w][1] / data[x][w][0]); 
				res[x][w][1] = Math.log(data[x][w][3] / data[x][w][2]); 
			} 
			*/
			for(int x2=0; x2<listSize; x2++) 
			{ 
				sum= sum + (res[x2][w][0] - res[x2][w][1]); 
				d[x2] = res[x2][w][0] - res[x2][w][1]; 
				//System.out.println("d[x2] " + d[x2]+" res[x2][w][0] " + res[x2][w][0] + " res[x2][w][1] " +res[x2][w][1]);
			} 
			
			//System.out.println("sum " + sum);
			//System.out.println("RES.Length: "+res.length + "List Size: " + listSize);
			dMean = sum/res.length; 
			//System.out.println("dMean " + dMean);
			for(int x3=0; x3<listSize; x3++) 
				s= s +(Math.pow((d[x3] - dMean), 2));
			//System.out.println("s " + s);
			//double temp = (1.0/(res[0].length-1));
			sd = Math.sqrt((s/(res.length-1))); 
			//System.out.println("calc "+temp );
			//System.out.println("sd " + sd);
			total[w] = Math.abs(dMean / sd);
		}
		
		/*for(int z=0; z<total.length; z++)
		{
			System.out.println("Antigen: "+ID[z]+"with p value: " + z + " = " + total[z]);
			
		}*/
		return total;
	}	
}
