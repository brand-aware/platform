import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;

import weka.classifiers.Evaluation;
import weka.classifiers.trees.RandomForest;
import weka.core.Instances;
import weka.core.converters.ArffLoader;
import weka.core.converters.CSVLoader;
import weka.core.converters.ArffSaver;
import weka.filters.unsupervised.attribute.NumericToNominal;
import weka.filters.unsupervised.instance.RemovePercentage;

public class BioMarkerSelection_Local {
	
	private double[][] last;
	private String[] ID;
	private int[] testPatients, trainPatients;
	private double[] crossValidate;
	private Antigens antList;
	private int delete =0;
	private long count=0;
	private MultiThread[] threadList;
	
	private Properties properties;
	
	//Debug
	private FileOutputStream fStreamBackwardElim;
	private FileOutputStream fStreamCrossvalid;
	private FileOutputStream fStreamCrossvalidDefault;
	private FileOutputStream fStreamRandomForest;
	private PrintStream pStreamBackwardElim;
	private PrintStream pStreamCrossvalid;
	private PrintStream pStreamCrossvalidDefault;
	private PrintStream pStreamRandomForest;
	
	public BioMarkerSelection_Local(String[] idList, int[] test, int[] train)
	{
		//Requires String[] of ID for rows of antigens
		ID = idList;
		testPatients = test;
		trainPatients = train;
		
		//TODO: move to main
		properties = new Properties();
	}
	
	public BioMarkerSelection_Local()
	{
		//Empty constructor to work on remote systems
		 try {
			 fStreamBackwardElim = new FileOutputStream(new File("BackwardsElimination.txt"));
			 fStreamCrossvalid = new FileOutputStream(new File("Crossvalid.txt"));			 
			 fStreamCrossvalidDefault = new FileOutputStream(new File("CrossvalidDefault.txt"));
			 fStreamRandomForest = new FileOutputStream(new File("RandomForest.txt"));
			 pStreamBackwardElim= new PrintStream(fStreamBackwardElim);
			 pStreamCrossvalid= new PrintStream(fStreamCrossvalid);
			 pStreamCrossvalidDefault= new PrintStream(fStreamCrossvalidDefault);
			 pStreamRandomForest= new PrintStream(fStreamRandomForest);
			 
		 } catch (FileNotFoundException e) {
			System.err.println(e);
			e.printStackTrace();
		}
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
		if(properties.getDebug()){
			pStreamBackwardElim.println("BackwardsElimination");
		}
		
		int removePosition = 0;
		double[][] formattedData;
		int size = data[0].length - 1;
		antList = new Antigens(size);
		
		antList.print();
		for(int x = 0; x < size; x++)
		{
			if(x == 0){
				printMatrix(threeDtoTwoD(data), pStreamBackwardElim);
			}
			crossValidate = new double[data[0].length];
			for(int y = 0; y < data[0].length; y++)
			{
				//cross validation of data
				data = removeRowKeepLast(data, y);
				
				//Print out temporarialy removed row
				System.out.print(x + "." + y +": temp removed row: ");
				for(int z = 0; z < last.length; z++){
					System.out.print(last[z] + ", ");
				}
				System.out.println();

				//Flatten data =p
				formattedData = threeDtoTwoD(data);
				
				printMatrix(formattedData, pStreamBackwardElim);
				printMatrix(formattedData, pStreamCrossvalid);
				crossValidate[y] = crossValidate(formattedData, 5);
				
				pStreamCrossvalid.println("Position: "+ y +" Error: " + crossValidate[y]);
				
				data = addRow(data, last, y);
				
				printMatrix(threeDtoTwoD(data), pStreamBackwardElim);
				
			}
					
			//calculate best candidate for removal in the crossValidate error set
			removePosition = findBest(crossValidate);
			pStreamBackwardElim.println("remove position: " + removePosition);
			pStreamCrossvalid.println("remove position: " + removePosition);
			pStreamCrossvalidDefault.println("remove position: " + removePosition);
			pStreamRandomForest.println("remove position: " + removePosition);
			//remove one antigen from the list
			
			data = removeRow(data, removePosition,crossValidate[removePosition]);
			printMatrix(threeDtoTwoD(data), pStreamBackwardElim);
			//test print 
			for(int i = 0; i<crossValidate.length; i++)
			{
				System.out.println("\n\n\n\n\nPosition: " + (i + 1) + " error: "+crossValidate[i]);
				pStreamBackwardElim.println("\n\n\n\n\nPosition: " + (i + 1) + " error: "+crossValidate[i]);
				pStreamCrossvalid.println("\n\n\n\n\nPosition: " + (i + 1) + " error: "+crossValidate[i]);
				pStreamCrossvalidDefault.println("\n\n\n\n\nPosition: " + (i + 1) + " error: "+crossValidate[i]);
				pStreamRandomForest.println("\n\n\n\n\nPosition: " + (i + 1) + " error: "+crossValidate[i]);
			}
			antList.print();
			
		}
		antList.print();
	}
	
	public int findBest(double[] list)
	{
		//return the position of the best value in the set
		pStreamBackwardElim.println("findBest --  function  --  best to be removed from list of size: "+list.length);
		pStreamCrossvalid.println("findBest --  function  --  best to be removed from list of size: "+list.length);
		pStreamCrossvalidDefault.println("findBest --  function  --  best to be removed from list of size: "+list.length);
		pStreamRandomForest.println("findBest --  function  --  best to be removed from list of size: "+list.length);

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
		
		pStreamBackwardElim.println("removeRow -- function" + "\n\n\nX: " + position);
		pStreamBackwardElim.println("temp.length: " + oldRow.length+", temp[0].length: " + oldRow[0].length+", temp[0][0].length: " + oldRow[0][0].length + ", idList.length: " + ID.length);
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
				pStreamBackwardElim.println("Index: " + b + " Removing " + ID[b]);
				removed = b;
			}
		}
		
		antList.add(new Antigens(error, position, ID[position]));
		removeID(position);
		
		return newRow;
	}
	
	public void removeID(int position)
	{
		
		pStreamBackwardElim.println("removeID  -- function");
		//store new string in shorter, temp id list
		String[] newList = new String[ID.length - 1];
		
		for(int i = 0; i < ID.length; i++)
		{
			if(i == position){
				pStreamBackwardElim.println("Removing " + ID[position] + " from antigen list");
			}
			else if (i < position){
				newList[i] = ID[i];
			}
			else{
				newList[i - 1] = ID[i];
			}
		}
		ID = newList;
		
	}
	
	public double[][][] removeRowKeepLast(double[][][] previous, double position)
	{
		pStreamBackwardElim.println("removeRow_keepLast -- function");
		pStreamCrossvalid.println("removeRow_keepLast -- function");
		pStreamBackwardElim.println("old data size - previous.length: " + previous.length + ", previous[0].length" + previous[0].length + ", previous[0][0].length: " + previous[0][0].length);
		pStreamCrossvalid.println("old data size - previous.length: " + previous.length + ", previous[0].length" + previous[0].length + ", previous[0][0].length: " + previous[0][0].length);
		last = new double[previous[0].length][2];
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

		pStreamBackwardElim.println("new data size - newData.length: " + newData.length + ", newData[0].length" + newData[0].length + ", newData[0][0].length: " + newData[0][0].length);
		pStreamCrossvalid.println("new data size - newData.length: " + newData.length + ", newData[0].length" + newData[0].length + ", newData[0][0].length: " + newData[0][0].length);
		return newData;
	}
	
	public double[][][] addRow(double[][][] previous, double[][] row, double position)
	{
		pStreamBackwardElim.println("addRow -- function");
		pStreamBackwardElim.println("old data size - previous.length: " + previous.length + ", previous[0].length" + previous[0].length + ", previous[0][0].length: " + previous[0][0].length);

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

		pStreamBackwardElim.println("new data size - newData.length: " + newData.length + ", newData[0].length" + newData[0].length + ", newData[0][0].length: " + newData[0][0].length);
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
		pStreamCrossvalid.println("crossValidate -- function");
		//function takes in a data set of type double[][]
		//function returns the mean error returned by cross validation
		
		//B is an arbritrary number of times to make 5 groupings of the patients
		int B=1;
		//int B=1;  //testing purposes
		double meanError = 0;
		
		double[] error = new double[B];
		
		for(int a = 0; a < B; a++)
		{
			
			error[a] = crossValidDefault(data, fold);
			pStreamCrossvalid.println("Position: " + a + " Error : " + error[a]); 
			
		}
		//sum up error array
		for(int x = 0; x < error.length; x++)
		{
			meanError += error[x];
		}
		//take mean of sum
		
		meanError = meanError / error.length;
		pStreamCrossvalid.println("MeanError: " + meanError);
		return meanError;
	}
	
	public double RandomForest(File trainA)
	{
		//System.out.println("RandomForestTest  --  function");
		
		ArffLoader arff = new ArffLoader();
        try {
			arff.setSource(trainA);
		} catch (IOException e) {
			e.printStackTrace();
		}
        
        Instances nomi = null;
		try {
			nomi = arff.getDataSet();
		} catch (IOException e) {
			e.printStackTrace();
		}
     
        Instances trainSet = null;
        Instances testSet = null;
        //System.out.println("Instances: " + nomi.numInstances() + " Attributes: "+nomi.numAttributes());
        nomi.setClassIndex(nomi.numAttributes() - 1);
        NumericToNominal dat = new NumericToNominal();
        RemovePercentage per = new RemovePercentage();
        
        try {
			dat.setInputFormat(nomi);
		} catch (Exception e) {
			e.printStackTrace();
		}
        
        try {
			nomi = NumericToNominal.useFilter(nomi, dat);
		} catch (Exception e) {
			e.printStackTrace();
		}
        
        per = new RemovePercentage();
        try {
			per.setInputFormat(nomi);
		} catch (Exception e) {
			e.printStackTrace();
		}
        per.setInvertSelection(true);
        per.setPercentage(80);
        try {
			trainSet=per.useFilter(nomi, per);
		} catch (Exception e) {
			e.printStackTrace();
		}
        per = new RemovePercentage();
        try {
			per.setInputFormat(nomi);
		} catch (Exception e) {
			e.printStackTrace();
		}
        
        per.setPercentage(80);
        try {
			testSet=per.useFilter(nomi, per);
		} catch (Exception e) {
			e.printStackTrace();
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
			e.printStackTrace();
		}
		
		double oob = tree.measureOutOfBagError();		
		
		Evaluation evaluation1 = null;
		try {
			evaluation1 = new Evaluation(trainSet);
		} catch (Exception e) {
			e.printStackTrace();
		}
		try {
			evaluation1.evaluateModel(tree, testSet);
		} catch (Exception e) {
			e.printStackTrace();
		}
		double error = 0.0;
		double[][] confusionMatrix = evaluation1.confusionMatrix(); 
		pStreamRandomForest.println("Confusion Matrix: ");
		for(int i = 0; i < confusionMatrix.length; i++)
		{
			for(int j = 0; j < confusionMatrix[0].length; j++)
			{
				pStreamRandomForest.print(confusionMatrix[i][j] + " ");
			}
			pStreamRandomForest.println("\n\n\n\n");
		}
		error = (confusionMatrix[0][1] + confusionMatrix[1][0]);
		pStreamRandomForest.println("Number of Errors: " + error);
				
		return error;
	}
	
	public double[][] createRandomGroups(double[][] data)
	{
		pStreamCrossvalid.println("createRandomGroups  --  function");
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
		
		pStreamCrossvalid.println("temp.length: " + temp.length + "temp[0].length: " + temp[0].length);
		for(int a = 0; a < temp.length; a++)
		{
			pStreamCrossvalid.print(temp[a][0] + ", ");
		}
		
		return temp;
	}
	
	public int[] randomizePatients(int[] patients)
	{
		pStreamCrossvalid.println("randomizePatients  --  function");
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
		pStreamCrossvalid.println("patient order list: ");
		for(int x = 0; x < newPatients.length; x++)
		{
			pStreamCrossvalid.print(newPatients[x] + ", ");
		}
		pStreamCrossvalid.println();
		
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
		pStreamCrossvalidDefault.println("transpose --  function");
		//creates a temporary double[][], the inverse dimensions of data
		double[][] temp = new double[data[0].length][data.length];
		
		//loop though and store data in new array
		for(int x = 0; x < data.length; x++)
		{
			for(int y = 0; y < data[0].length; y++)
			{
				temp[y][x] = data[x][y];
				pStreamCrossvalidDefault.print(temp[y][x]);
			}
			pStreamCrossvalidDefault.println();
		}
		pStreamCrossvalidDefault.println("transpose dimensions: ");
		pStreamCrossvalidDefault.println("temp.length: " + temp.length + ", temp[0].length: " + temp[0].length);
		
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
		
		pStreamCrossvalidDefault.println("crossValidDefault  --  function");
		pStreamCrossvalidDefault.println("data.length: "+data.length+", data[0].length: "+data[0].length);
		double errorRate = 0;
		double[] error = new double[fold];
		
		double[][] trans_data = transpose(data);
		int start, stop, interval=(data.length / fold);
		for(int x = 0; x < fold; x++) 
		{ 
			count++;
			start = x * interval; 
			
			stop = start + interval; 
			//System.out.println("start "+start+" stop "+ stop + "Intervale: " +interval);
			if(x != fold - 1)  
				prepCSV(start, stop, trans_data, fold,count); 
			else 
				prepCSV(start, data.length - 1, trans_data, fold,count); 

			try 
			{ 
			
				error[x] = RandomForest(createNewArff(new File("data_temp" + count + ".csv"), x, fold));
			
			} 
			catch (Exception e) 
			{ 
				System.err.println(e); 
			} 

		} 
		
		//sum error returned from random forest 
		for(int y = 0; y < fold; y++) 
		{ 
			errorRate += error[y]; 
			
		} 
		pStreamCrossvalidDefault.println("Total Error: " + (errorRate / (data.length / 2)));
		//get mean error 
		//delete(fold);
		
		return (errorRate / (data.length / 2));
	}
	
	public File createNewArff(File Data, int i,int fold) throws IOException
	{
		
		CSVLoader loader = new CSVLoader();
		loader.setSource(Data);
		//File ror = new File("data.arff");
		
		
        Instances data = loader.getDataSet(); 
       
        ArffSaver saver = new ArffSaver();
        
        String fileName = "data" + count + ".arff";
        
        try
        {
	        saver.setInstances(data);       
	        saver.setFile(new File(fileName));
	        saver.writeBatch(); 
	        
        
        }
        catch(Exception e)
        {
        	//System.out.println("error: " + e + "\n");
        	//e.printStackTrace();
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
		//System.out.println("prepCSV  --  function");
		//System.out.println("data.length: "+data.length+", data[0].length: "+data[0].length);
		int size = data.length;
		int size2 = data[0].length;
		
		FileOutputStream out; // declare a file output object 
		PrintStream p; // declare a print stream object 
		try { // Create a new file output stream 
			out = new FileOutputStream("data_temp" + index + ".csv"); // Connect print stream to the output stream 
			p = new PrintStream(out);
			
			//create header for temp data file
			for(int z = 0; z < size; z++)
			{
					if(z == size - 1)
					{
						p.print(z);
					}
					else
					{
						p.print(z + ",");
					}
			}
			p.println(",CLASS");
			
			for(int x = 0; x < size2; x++) 
			{ 
			p.print(data[0][x]); 
			for(int y = 0; y < data.length - 1; y++) 
			{ 
			//print data matrix delimited by commas 
				p.print(','); 
				p.print(data[y + 1][x]);  
			} 
			if(x >= start && x <= stop) 
			{ 
				if(x%2 == 0) 
					p.println(",1"); 
				else 
					p.println(",2"); 
			} 
			else 
			{ 
				if(x%2 == 0) 
					p.println(",1"); 
				else 
					p.println(",2"); 
			} 
		}
			//close printout
			p.close();
		} 
		//catch all exceptions
		catch (Exception e) 
		{ 
			System.err.println ("Error writing to file");
			System.err.println(e);
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