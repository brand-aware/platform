import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;

import weka.classifiers.Evaluation;
import weka.classifiers.trees.RandomForest;
import weka.core.Instances;
import weka.core.converters.ArffLoader;
import weka.core.converters.CSVLoader;
import weka.core.converters.ArffSaver;
import weka.filters.unsupervised.attribute.NumericToNominal;
import weka.filters.unsupervised.instance.RemovePercentage;

public class BioMarkerSelection_Local2 implements IBioMarkerSelectionLocal2{
	double[] last;
	String[] ID;
	int[] test_pat, train_pat;
	double[] crossValidate;
	public BioMarkerSelection_Local2(String[] tmp, int[] test, int[] train)
	{
		//Requires String[] of ID for rows of antigens
		ID = tmp;
		test_pat=test;
		train_pat=train;
	}
	
	public BioMarkerSelection_Local2()
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
	
	public void backwardsElimination(double[][][] data)
	{
		System.out.println("BackwardsElimination");
		double remove_pos = 0;
		double[][] temp_data;
		
		for(int x=0; x<data.length; x++)
		{
			crossValidate = new double[data.length];
			for(int y=0; y<data.length; y++)
			{
				//cross validation of data
				data=removeRow_keepLast(data, y);
				//Flatten data =p
				temp_data = threeDtoTwoD(data);
								
				crossValidate[y] = crossValidate(temp_data, 5);
				
				//printMatrix(temp_data);
				
				data=addRow(data, last, y);
			}
					
			//calculate best candidate for removal in the crossValidate error set
			remove_pos=findBest(crossValidate);
			
			//remove one antigen from the list
			data = removeRow(data, ID, remove_pos);
		}	
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
		removeID(idList, removed);
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
	
	public double[][][] removeRow_keepLast(double[][][] temp, double x)
	{
		System.out.println("removeRow_keepLast -- function");
		System.out.println("old data size - temp.length: "+temp.length+", temp[0].length"+temp[0].length+", temp[0][0].length: "+temp[0][0].length);
		last = new double[temp[0].length];
		double[][][] tmp = new double[temp.length][temp[0].length-1][temp[0][0].length];
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
				else if(b == x)
					for(int e=0; e<temp[0][0].length; e++)
						last[b] = temp[a][b][e];	
				else
					System.out.println("removeRow_keepLast function BROKEN....");
			}
		}
		System.out.println("new data size - tmp.length: "+tmp.length+", tmp[0].length"+tmp[0].length+", tmp[0][0].length: "+tmp[0][0].length);
		return tmp;
	}
	public double[][][] addRow(double[][][] temp, double[] row, double position)
	{
		System.out.println("addRow -- function");
		System.out.println("old data size - temp.length: "+temp.length+", temp[0].length"+temp[0].length+", temp[0][0].length: "+temp[0][0].length);

		double[][][] tmp2 = new double[temp.length][temp[0].length+1][temp[0][0].length];
		for(int a=0; a<temp.length; a++)
		{
			for(int b=0; b<temp[0].length; b++)
			{
				if(b < position)
					for(int c=0; c<temp[0][0].length; c++)
						tmp2[a][b][c] = temp[a][b][c];
						
				else if(b > position)
					for(int d=0; d<temp[0][0].length; d++)
						tmp2[a][b][d] = temp[a][b][d];
				else
					for(int e=0; e<temp[0][0].length; e++)
						tmp2[a][b][e] = row[e];
			}
		}		
		System.out.println("new data size - tmp2.length: "+tmp2.length+", tmp2[0].length"+tmp2[0].length+", tmp2[0][0].length: "+tmp2[0][0].length);
		return tmp2;
	}
	
	public double[][] threeDtoTwoD(double[][][] old)
	{
		System.out.println("threeDtoTwoD  --  function");
		System.out.println("old.length: "+old.length+", old[0].length: "+old[0].length+", old[0][0].length: "+old[0][0].length);
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
		System.out.println("temp.length: "+temp.length+", temp[0].length: "+temp[0].length);

		return temp;
	}
	
	public double crossValidate(double[][] data, int fold)
	{
		System.out.println("crossValidate -- function");
		//function takes in a data set of type double[][]
		//function returns the mean error returned by cross validation
		
		//B is an arbritrary number of times to make 5 groupings of the patients
		//int B=500;
		int B=1;  //testing purposes
		double meanError=0;
		
		double[] error = new double[B];
		
		for(int a=0; a<B; a++)
		{
			System.out.println("crossvalid iteration: "+a);
			error[a] = crossValidDefault(createRandomGroups(data), fold);
		}
		//sum up error array
		for(int x=0; x<error.length; x++)
		{
			meanError+=error[x];
		}
		//take mean of sum
		meanError = meanError/error.length;
		return meanError;
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
	public double crossValidDefault(double[][] data, int fold)
	{
		System.out.println("crossValidDefault  --  function");
		System.out.println("data.length: "+data.length+", data[0].length: "+data[0].length);
		double errorRate=0;
		double[] error = new double[fold];
		
		double[][] trans_data = transpose(data);
		int start, stop;
		/*for(int x=0; x<fold; x++)
		{
			start = x * (data.length/fold);
			stop = start + (data.length/fold);
			if(x != fold-1)	
				createGroups(start, stop, trans_data, fold);
			else
				createGroups(start, data.length-1, trans_data, fold);
			
			try
			{
				error[x] = RandomForestTest(new File("test_temp.csv"), new File("train_temp.csv"));
			}
			catch (Exception e)
			{
				System.out.println(e);
			}
			
		}*/
		prepCSV(trans_data, fold);

		try
		{
			errorRate = RandomForestTest(new File("data_temp.csv"));
		}
		catch (Exception e)
		{
			System.out.println(e);
		}
		//sum error returned from random forest
		/*for(int y=0; y<fold; y++)
		{
			errorRate+=error[y];
		}
		//get mean error
		errorRate = errorRate/fold;*/
		return errorRate;
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
			System.out.println(e);
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
			
			int pos = size2 - (size2/fold);
			for(int x=0; x<size2; x++)
			{
				p.print(data[0][x]);
				for(int y=0; y<data.length-1; y++)
				{
					//print data matrix delimited by commas
					p.print(',');
					p.print(data[y+1][x]); 
				}
				if(x> pos)
				{
					p.println(",?");
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
			System.out.println(e);
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