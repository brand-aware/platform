import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Vector;

import weka.classifiers.Evaluation;
import weka.classifiers.trees.RandomForest;
import weka.core.Instances;
import weka.core.converters.ArffLoader;
import weka.core.converters.CSVLoader;
import weka.core.converters.ArffSaver;
import weka.filters.unsupervised.attribute.NumericToNominal;
import weka.filters.unsupervised.instance.RemovePercentage;

public class BioMarkerSelection {
	private final java.util.List<Removal> removalHistory = new java.util.ArrayList<Removal>();

    private static final class Removal {
        final int order;
        final String label;
        final double errorRate;

        Removal(int order, String label, double errorRate) {
            this.order = order;
            this.label = label;
            this.errorRate = errorRate;
        }
    }

    private static final class WorkerLane {
        final String ip;
        int firstCandidate = -1;
        WorkerLane(String ip) { this.ip = ip; }
    }
	double[][] last;
    private final BioMarkerSelection_Local evaluator = new BioMarkerSelection_Local();
	String[] ID;
	int[] test_pat, train_pat;
	Vector<ServerThread> ServerList;
	double[] crossValidate;
	public BioMarkerSelection(String[] tmp, int[] test, int[] train)
	{
		//Requires String[] of ID for rows of antigens
		ID = tmp;
		test_pat=test;
		train_pat=train;
	}
	
	public BioMarkerSelection()
	{
		//Empty constructor to work on remote systems
	}
	public void setID(String[] tmp)
	{
		ID=tmp;
	}
	public void setTestPat(int[] pat)
	{
		test_pat=pat;
	}
	public void setTrainPat(int[] pat)
	{
		train_pat=pat;
	}
    public void startThreads(String[] ips) {
        if (ips == null || ips.length == 0) throw new IllegalArgumentException("No worker addresses");
        java.util.Set<java.net.InetAddress> addresses = new java.util.HashSet<java.net.InetAddress>();
        for (String ip : ips) {
            if (ip == null || ip.trim().isEmpty()) throw new IllegalArgumentException("Empty worker address");
            try {
                String address = ip.trim();
                java.net.InetAddress resolved = java.net.InetAddress.getByName(address);
                if (resolved.isAnyLocalAddress())
                    throw new IllegalArgumentException("Worker address " + address
                            + " is a wildcard bind address; enter that machine's IPv4 address instead");
                if (!addresses.add(resolved))
                    throw new IllegalArgumentException("Duplicate worker address: " + ip);
                // CPU discovery uses a short BWP1 ping after address validation.
                System.out.println("Configured worker: " + address);
            } catch (IOException e) { throw new IllegalStateException("Cannot resolve worker: " + ip, e); }
        }
    }
    private int[] discoverWorkerLaneCounts(String[] ips) {
        int[] laneCounts = new int[ips.length];
        int localProcessors = CpuCapacity.systemProcessorCount();
        for (int index = 0; index < ips.length; index++) {
            String address = ips[index].trim();
            int processors = localProcessors;
            try {
                processors = new ServerThread(address, 5000).getRemoteProcessorCount();
            } catch (IOException | RuntimeException failure) {
                System.err.println("Could not read processor count from worker " + address + ": "
                        + failure.getMessage() + "; using controller count " + localProcessors);
            }
            int cap = CpuCapacity.threadLimit(processors, 80);
            int configured = Integer.getInteger("distributed.lanesPerWorker", cap);
            laneCounts[index] = Math.max(1, Math.min(cap, configured));
            System.out.println("Worker " + address + " reports " + processors
                    + " logical processors; using up to " + laneCounts[index] + " lanes (80% cap)");
        }
        return laneCounts;
    }
    public void backwardsElimination(double[][][] data, final String[] ips) {
        if (data == null || data.length == 0 || data[0] == null || data[0].length < 2)
            throw new IllegalArgumentException("At least two antigens are required");
        if (ID == null || ID.length != data[0].length)
            throw new IllegalArgumentException("Antigen IDs must match matrix columns");
        validateFiniteInput(data);
        startThreads(ips);
        ensureLocalWorkers(ips);
        removalHistory.clear();
        final int[] workerLaneCounts = discoverWorkerLaneCounts(ips);
        final java.util.List<java.util.List<WorkerLane>> lanesByWorker = new java.util.ArrayList<java.util.List<WorkerLane>>();
        int laneCount = 0;
        for (int worker = 0; worker < ips.length; worker++) {
            java.util.List<WorkerLane> workerLanes = new java.util.ArrayList<WorkerLane>();
            for (int lane = 0; lane < workerLaneCounts[worker]; lane++)
                workerLanes.add(new WorkerLane(ips[worker].trim()));
            lanesByWorker.add(workerLanes);
            laneCount += workerLaneCounts[worker];
        }
        final java.util.List<WorkerLane> lanes = new java.util.ArrayList<WorkerLane>();
        for (java.util.List<WorkerLane> workerLanes : lanesByWorker) lanes.addAll(workerLanes);
        final int activeLaneCount = laneCount;
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(laneCount);
        int eliminationIteration = 0;
        try {
            while (data[0].length > 1) {
                int remainingAntigens = data[0].length;
                if (eliminationIteration == 0 || eliminationIteration % 5 == 0 || remainingAntigens == 2) {
                    System.out.println("Elimination progress: " + remainingAntigens
                            + " antigens remain; " + (remainingAntigens - 1)
                            + " still to remove (removal " + (eliminationIteration + 1) + ")");
                }
                final double[][][] current = data;
                final double[] errors = new double[current[0].length];
                crossValidate = errors;
                java.util.Arrays.fill(errors, Double.NaN);
                final java.util.concurrent.ConcurrentLinkedQueue<Integer> pending = new java.util.concurrent.ConcurrentLinkedQueue<Integer>();
                final int initiallyAssigned = Math.min(activeLaneCount, errors.length);
                for (int candidate = initiallyAssigned; candidate < errors.length; candidate++) pending.add(candidate);
                final java.util.concurrent.atomic.AtomicInteger remaining = new java.util.concurrent.atomic.AtomicInteger(errors.length);
                int[] assignedPerWorker = new int[ips.length];
                for (int candidate = 0; candidate < initiallyAssigned; candidate++) {
                    int selectedWorker = -1;
                    for (int worker = 0; worker < ips.length; worker++) {
                        if (assignedPerWorker[worker] >= workerLaneCounts[worker]) continue;
                        if (selectedWorker < 0
                                || (long) assignedPerWorker[worker] * workerLaneCounts[selectedWorker]
                                < (long) assignedPerWorker[selectedWorker] * workerLaneCounts[worker])
                            selectedWorker = worker;
                    }
                    if (selectedWorker < 0) throw new IllegalStateException("No free worker lane for candidate assignment");
                    lanesByWorker.get(selectedWorker).get(assignedPerWorker[selectedWorker]++).firstCandidate = candidate;
                }
                System.out.println("Starting iteration with " + errors.length + " candidates across "
                        + ips.length + " configured workers (" + activeLaneCount + " lanes total; "
                        + BioMarkerSelection_Local.getFoldThreadCount() + " local fold threads; controller has "
                        + BioMarkerSelection_Local.getSystemProcessorCount() + " logical processors)");
                java.util.List<java.util.concurrent.Callable<Void>> tasks = new java.util.ArrayList<java.util.concurrent.Callable<Void>>();
                // Multiple lanes per worker let each machine evaluate independent candidates concurrently.
                for (WorkerLane lane : lanes) {
                    final String ip = lane.ip;
                    final int firstCandidate = lane.firstCandidate;
                    tasks.add(new java.util.concurrent.Callable<Void>() {
                        public Void call() throws InterruptedException {
                            System.out.println("Worker lane started: " + ip + "; first candidate "
                                    + (firstCandidate >= 0 ? firstCandidate : "from queue"));
                            Integer assigned = firstCandidate >= 0 ? firstCandidate : null;
                            while (remaining.get() > 0) {
                                if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                                Integer candidate = assigned;
                                assigned = null;
                                if (candidate == null) candidate = pending.poll();
                                if (candidate == null) {
                                    Thread.sleep(50);
                                    continue;
                                }
                                try {
                                    double[][] matrix = threeDtoTwoD(removeCandidate(current, candidate));
                                    double error = evaluateCandidate(ip, matrix, candidate);
                                    if (!Double.isFinite(error) || error < 0 || error > 1)
                                        throw new IOException("Invalid error rate: " + error);
                                    errors[candidate] = error;
                                    remaining.decrementAndGet();
                                } catch (IOException | RuntimeException failure) {
                                    // Keep this candidate outstanding; any available worker can retry it.
                                    pending.add(candidate);
                                    System.err.println("Worker " + ip + " failed candidate " + candidate
                                            + "; retry pending: " + failure.getMessage());
                                    Thread.sleep(1000);
                                }
                            }
                            return null;
                        }
                    });
                }
                // Iteration barrier: every candidate must have a successful error rate
                // before selecting an antigen or constructing the next iteration.
                for (java.util.concurrent.Future<Void> result : executor.invokeAll(tasks)) result.get();
                int best = findBest(crossValidate);
                data = removeCandidate(current, best);
                removalHistory.add(new Removal(removalHistory.size() + 1, ID[best], errors[best]));
                ID = removeID(ID, best);
                eliminationIteration++;
            }
            reportRemovals();
            System.out.println("---------------------------Start of Antigen List---------------------------");
            for (Removal removal : removalHistory) {
                System.out.println("Removal: " + removal.order + " Antigen: " + removal.label
                        + " Error: " + removal.errorRate);
            }
            for (String antigen : ID) {
                System.out.println("Remaining Antigen: " + antigen);
            }
            System.out.println("----------------------------End of Antigen List----------------------------");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Distributed elimination interrupted", e);
        } catch (java.util.concurrent.ExecutionException e) {
            throw new IllegalStateException("Distributed evaluation failed", e.getCause());
        } finally { executor.shutdownNow(); }
    }
    private void reportRemovals() {
        java.nio.file.Path output = java.nio.file.Paths.get("biomarker-removal-history.csv").toAbsolutePath();
        try (java.io.BufferedWriter writer = java.nio.file.Files.newBufferedWriter(
                output, java.nio.charset.StandardCharsets.UTF_8)) {
            writer.write("removal_order,label,error_rate");
            writer.newLine();
            for (Removal removal : removalHistory) {
                writer.write(removal.order + ",\"" + removal.label.replace("\"", "\"\"")
                        + "\"," + removal.errorRate);
                writer.newLine();
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot write biomarker removal history to " + output, e);
        }
        java.util.List<Removal> ranked = new java.util.ArrayList<Removal>(removalHistory);
        // Stable sorting keeps removal order for equal error rates.
        java.util.Collections.sort(ranked, new java.util.Comparator<Removal>() {
            public int compare(Removal left, Removal right) {
                return Double.compare(right.errorRate, left.errorRate);
            }
        });
        System.out.println("Top " + Math.min(10, ranked.size()) + " removal error rates (highest first):");
        for (int i = 0; i < Math.min(10, ranked.size()); i++) {
            Removal removal = ranked.get(i);
            System.out.println((i + 1) + ". " + removal.label + ": " + removal.errorRate
                    + " (removal " + removal.order + ")");
        }
        System.out.println("Ordered biomarker removal history: " + output);
    }
    protected double evaluateCandidate(String ip, double[][] matrix, int candidate) throws IOException {
        ServerThread job = new ServerThread(ip);
        try {
            job.setData(matrix, candidate);
            job.run();
            return job.getErrorRate();
        } finally { job.closeSocket(); }
    }
    private void ensureLocalWorkers(String[] ips) {
        for (String ip : ips) {
            try {
                if (java.net.InetAddress.getByName(ip.trim()).isLoopbackAddress())
                    LocalWorkerProcess.ensureRunning(ip.trim());
            } catch (IOException e) {
                throw new IllegalStateException("Cannot start local worker at " + ip, e);
            }
        }
    }
    private void validateFiniteInput(double[][][] data) {
        int channels = data[0][0] == null ? 0 : data[0][0].length;
        if (channels == 0) throw new IllegalArgumentException("Normalized input has no measurement channels");
        for (int patient = 0; patient < data.length; patient++) {
            if (data[patient] == null || data[patient].length != ID.length)
                throw new IllegalArgumentException("Normalized input has an invalid antigen count for patient "
                        + (patient + 1));
            for (int antigen = 0; antigen < ID.length; antigen++) {
                if (data[patient][antigen] == null || data[patient][antigen].length != channels)
                    throw new IllegalArgumentException("Normalized input has an invalid channel count for antigen "
                            + ID[antigen] + ", patient " + (patient + 1));
                for (int channel = 0; channel < channels; channel++) {
                    if (!Double.isFinite(data[patient][antigen][channel]))
                    throw new IllegalArgumentException("Non-finite normalized value for antigen row "
                                + (antigen + 1) + " (label: " + ID[antigen] + "), patient "
                                + (patient + 1) + ", channel " + (channel + 1)
                                + "; check for zero or invalid input measurements");
                }
            }
        }
    }
    private double[][][] removeCandidate(double[][][] data, int candidate) {
        double[][][] result = new double[data.length][data[0].length - 1][];
        for (int patient = 0; patient < data.length; patient++)
            for (int marker = 0; marker < data[0].length; marker++)
                if (marker != candidate)
                    result[patient][marker < candidate ? marker : marker - 1] = data[patient][marker].clone();
        return result;
    }
	public int findBest(double[] list)
	{
		//return the position of the best value in the set
		System.out.println("findBest --  function  --  best to be removed from list of size: "+list.length);
		int best=0;
		for(int x=0; x<list.length; x++)
			if(list[x] > list[best])
				best=x;
		
		return best;
	}

	public double[][][] removeRow(double[][][] temp, String[] idList, double x)
	{
		System.out.println("removeRow -- function");
		System.out.println("temp.length: "+temp.length+", temp[0].length: "+temp[0].length+", temp[0][0].length: "+temp[0][0].length+", idList.length: "+idList.length);
		double[][][] tmp = new double[temp.length][temp[0].length-1][temp[0][0].length];
		int removed =0;
		for(int a=0; a<temp.length; a++)
		{
			for(int b=0; b<temp[0].length; b++)
			{
				if(b < x)
					for(int c=0; c<temp[0][0].length; c++)
						tmp[a][b][c] = temp[a][b][c];
						
				else if(b > x)
					for(int d=0; d<temp[0][0].length; d++)
						tmp[a][b-1][d] = temp[a][b][d];
				else
					removed=b;
					
			}
		}
		ID = removeID(idList, removed);
		return tmp;
	}
	public String[] removeID(String[] list, int x)
	{
		System.out.println("removeID  -- function");
		//store new string in shorter, temp id list
		String[] temp = new String[list.length-1];
		
		for(int i=0; i<list.length; i++)
		{
			if(i == x)
				System.out.println("Removing "+ list[x]+ " from antigen list");
			else if (i<x)
				temp[i] = list[i];
			else
				temp[i-1] = list[i];
		}
		return temp;
	}
	
    public double[][][] removeRow_keepLast(double[][][] data, double position) {
        int marker = (int) position;
        if (position != marker || marker < 0 || marker >= data[0].length)
            throw new IllegalArgumentException("Invalid antigen position");
        last = new double[data.length][];
        for (int patient = 0; patient < data.length; patient++) last[patient] = data[patient][marker].clone();
        return removeCandidate(data, marker);
    }
    public double[][][] addRow(double[][][] data, double[][] row, double position) {
        int marker = (int) position;
        if (position != marker || marker < 0 || marker > data[0].length || row.length != data.length)
            throw new IllegalArgumentException("Invalid antigen position or patient count");
        double[][][] result = new double[data.length][data[0].length + 1][];
        for (int patient = 0; patient < data.length; patient++)
            for (int column = 0; column < result[patient].length; column++)
                result[patient][column] = (column == marker ? row[patient] :
                    data[patient][column < marker ? column : column - 1]).clone();
        return result;
    }
	public double[][] threeDtoTwoD(double[][][] old)
	{
		double[][] temp = new double[old.length * old[0][0].length][old[0].length];
		int pos=0;
		
		for(int x=0; x<old.length; x++)
		{
			for(int z=0; z<old[0][0].length; z++)
			{
				for(int y=0; y<old[0].length; y++)
				{
					temp[pos][y] = old[x][y][z];
				}
				pos++;
			}
		}
		return temp;
	}
	
    public double crossValidate(double[][] data, int fold) {
        return evaluator.crossValidate(data, fold);
    }
	public double RandomForestTest(File Data) throws Exception
	{
		System.out.println("RandomForestTest  --  function");
		CSVLoader loader = new CSVLoader();
		loader.setSource(Data);
		
	    
        Instances data = loader.getDataSet(); 
       
        ArffSaver saver = new ArffSaver();
        
        try
        {
        saver.setInstances(data);
       
        saver.setFile(new File("data.arff"));
        saver.writeBatch(); 
       
        }
        catch(Exception e)
        {
        	System.out.println("error: " + e);
        }
        
        File trainA = new File("data.arff");
        
        ArffLoader arff = new ArffLoader();
        arff.setSource(trainA);
        Instances nomi= arff.getDataSet();
        Instances trainSet;
        Instances testSet;
        nomi.setClassIndex(nomi.numAttributes() - 1);
        NumericToNominal dat = new NumericToNominal();
        RemovePercentage per = new RemovePercentage();
        
        dat.setInputFormat(nomi);
        per.setInputFormat(nomi);
        
        nomi=NumericToNominal.useFilter(nomi, dat);
        per.setPercentage(80);
        trainSet=per.useFilter(nomi, per);
        per.setInvertSelection(true);
        per.setPercentage(20);
        testSet=per.useFilter(nomi, per);
        
        trainSet.classAttribute();
        trainSet.setClassIndex(nomi.numAttributes() - 1);
        testSet.classAttribute();
        testSet.setClassIndex(nomi.numAttributes() - 1);
        
        System.out.println("Something weird probably:  "+trainSet.classAttribute());
       
        RandomForest tree=new RandomForest();
		tree.setNumTrees(5);
		tree.setSeed(666666);
		tree.setDebug(true);
		//String[] options = weka.core.Utils.splitOptions("-P .8");
		//Enumeration temp;
		//temp = tree.listOptions();
		
		//tree.setOptions(options);
		
		tree.buildClassifier(trainSet);
		
		double oob = tree.measureOutOfBagError();
		
		System.out.println("OOB: " + tree.measureOutOfBagError());
		
		
		Evaluation evaluation1 = new Evaluation(trainSet);
		evaluation1.evaluateModel(tree, testSet);
		System.out.println("Error Rate: " + evaluation1.errorRate());
		double err = evaluation1.errorRate();
		return err;
	}
	
	public double[][] createRandomGroups(double[][] data)
	{
		System.out.println("createRandomGroups  --  function");
		//temporary data storage for new data grouping
		double[][] temp = new double[data.length][data[0].length];
		
		//create random list of patient ids (/2 to compensate for 2 mixes)
		System.out.println("data.length = "+data.length);
		int[] order = new int[data.length/2];
		order = randomizePatients(order);
		for(int x=0; x<data[0].length; x++)
		{
			for(int y=0; y<order.length; y++)
			{
				temp[y*2][x]=data[order[y]*2][x];
				temp[(y*2)+1][x]=data[(order[y]*2)+1][x];
			}
		}
		
		System.out.println("temp.length: "+temp.length+"temp[0].length: "+temp[0].length);
		for(int a=0; a<temp.length; a++)
		{
			System.out.print(temp[a][0]+", ");
		}
		
		return temp;
	}
	
	public int[] randomizePatients(int[] pat)
	{
		System.out.println("randomizePatients  --  function");
		//return a random list for patient ids;
		int[] left = new int[pat.length];
		
		//create a new order of patients to return and a counter to loop through the list;
		int[] newPat = new int[pat.length];
		int counter = 0;
		
		for(int a=0; a<left.length; a++)
			left[a]=0;
		while(anyLeft(left) && counter<newPat.length)
		{
			int x = (int)(Math.random()*pat.length);
			if(x<pat.length)
			{
				if(left[x] == 0)
				{
					newPat[counter] = x;
					left[x]++;
				counter++;
				}
			}
			x=0;
		}
		System.out.println("patient order list: ");
		for(int x=0; x<newPat.length; x++)
		{
			System.out.print(newPat[x]+", ");
		}
		
		return newPat;
	}
	public boolean anyLeft(int[] left)
	{
		//System.out.println("anyLeft  --  function");
		for(int a=0; a<left.length; a++)
			if(left[a] == 0)
				return true;
		return false;
	}
	public double[][] transpose(double[][] data)
	{
		System.out.println("transpose --  function");
		//creates a temporary double[][], the inverse dimensions of data
		double[][] temp = new double[data[0].length][data.length];
		
		//loop though and store data in new array
		for(int x=0; x<data.length; x++)
		{
			for(int y=0; y<data[0].length; y++)
			{
				temp[y][x]=data[x][y];
			}
		}
		System.out.println("transpose dimensions: ");
		System.out.println("temp.length: "+temp.length+", temp[0].length: "+temp[0].length);
		
		return temp;
	}
	public void printMatrix(double[][] data)
	{
		System.out.println("printMatrix  --  function");
		System.out.println("data.length: "+data.length+", data[0].length: "+data[0].length);
		for(int y=0; y<data[0].length; y++)
		{
			System.out.print(y + ". ");
			
			for(int x=0; x<data.length; x++)
			{
				System.out.print(data[x][y]+", ");
			}
			System.out.println();
		}
	}
    public double crossValidDefault(double[][] data, int fold) {
        return evaluator.crossValidDefault(data, fold);
    }

	/*
	public void createGroups(int start, int stop, double[][] data, int fold)
	{
		System.out.println("createGroups  --  function");
		System.out.println("data.length: "+data.length+", data[0].length: "+data[0].length);
		System.out.println("Start: "+start+", Stop: "+stop);
		int size = data.length;
		int size2 = data[0].length;
		
		FileOutputStream out_test, out_train; // declare a file output object 
		PrintStream p_test, p_train; // declare a print stream object 
		try { // Create a new file output stream 
			out_test = new FileOutputStream("test_temp.csv"); // Connect print stream to the output stream 
			out_train = new FileOutputStream("train_temp.csv"); // Connect print stream to the output stream
			p_test = new PrintStream( out_test );
			p_train = new PrintStream( out_train );
			
			//create header for test file
			for(int z=0; z<size; z++)
			{
					if(z==size-1)
					{
						p_test.print(z);
					}
					else
					{
						p_test.print(z+",");
					}
			}
			p_test.println();
			
			//create header for training file
			for(int z2=0; z2<size; z2++)
			{
					if(z2==size-1)
					{
						p_train.print(z2);
					}
					else
					{
						p_train.print(z2+",");
					}
			}
			p_train.println(",CLASS");
			
			for(int x=0; x<size2; x++)
			{
				if(x >= start && x <= stop)
				{
					p_test.print(data[0][x]);
					for(int y=0; y<size-1; y++)
					{
						//print data matrix delimited by commas
						p_test.print(", ");
						p_test.print(data[y+1][x]); 
					}
					p_test.println();
				}
				else
				{
					p_train.print(data[0][x]);
					for(int y=0; y<data.length-1; y++)
					{
						//print data matrix delimited by commas
						p_train.print(',');
						p_train.print(data[y+1][x]); 
					}
					if(x%2 == 0)
						p_train.println(",PC");
					else
						p_train.println(",BPH");
				}
			}
			
			//close printout
			p_test.close();
			p_train.close();
		} 
		//catch all exceptions
		catch (Exception e) 
		{ 
			System.err.println ("Error writing to file");
			throw new IllegalStateException("Evaluation failed", e);
		}		
	}
	*/
	
	public void prepCSV(double[][] data, int fold)
	{
		System.out.println("prepCSV  --  function");
		System.out.println("data.length: "+data.length+", data[0].length: "+data[0].length);
		int size = data.length;
		int size2 = data[0].length;
		
		FileOutputStream out; // declare a file output object 
		PrintStream p; // declare a print stream object 
		try { // Create a new file output stream 
			out = new FileOutputStream("data_temp.csv"); // Connect print stream to the output stream 
			p = new PrintStream( out );
			
			//create header for temp data file
			for(int z=0; z<size; z++)
			{
					if(z==size-1)
					{
						p.print(z);
					}
					else
					{
						p.print(z+",");
					}
			}
			p.println(",CLASS");
			
			for(int x=0; x<size2; x++)
			{
				p.print(data[0][x]);
				for(int y=0; y<data.length-1; y++)
				{
					//print data matrix delimited by commas
					p.print(',');
					p.print(data[y+1][x]); 
				}
				if(x> size2-fold)
				{
					//do nothing
				}
				else
				{
					if(x%2 == 0)
						p.println(",PC");
					else
						p.println(",BPH");
				}
			}
			
			//close printout
			p.close();
		} 
		//catch all exceptions
		catch (Exception e) 
		{ 
			System.err.println ("Error writing to file");
			throw new IllegalStateException("Evaluation failed", e);
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
				System.out.println("d[x2] " + d[x2]+" res[x2][w][0] " + res[x2][w][0] + " res[x2][w][1] " +res[x2][w][1]);
			} 
			
			System.out.println("sum " + sum);
			System.out.println("RES.Length: "+res.length + "List Size: " + listSize);
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
