/**
	 * Created 06-26-08
	 * Mike Drummond and Corey Lander
	 * Written for Brigham and Women's Hospital
	 */

public class Normalization2 {
	//Normalization class 
	
	int deleted = 0;
	double[] train, test;
	DataHandler cy3, cy5;
	BioMarkerSelection BS;
	
	String[] idList;
	
	Normalization2()
	{
		BS = new BioMarkerSelection();
	}
	
	public double[][][] predict(DataHandler cy3_tmp, DataHandler cy5_tmp, boolean missing)
	{
		//data declarations
		String list = new String("sig_81_genes_oldanalysis.txt");
		cy3 = cy3_tmp;
		cy5 = cy5_tmp;
		idList = cy3.getID();
		//training and test id currently set manually, to be added later
		//user specification of id fields
		int [] testID = {101, 128, 136, 151, 32, 34, 43, 45, 58, 89};
		int[] trainID = {41,47,50,56,74, 76, 79,87,93,115};
		
		//print data
		System.out.println("GOT HERE!!!!!");
		System.out.println("CY3~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
		if(missing)
			cy3.readDATSheets("output_filled.txt");
		cy3.printData();
		System.out.println("CY5~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
		if(missing)
			cy5.readDATSheets("output_filled2.txt");
		cy5.printData();
		
		//Finding and deleting positive and negative controls
		//controls currently manually set, to be added later - ability for user selection
		//Positive and Negative labels to find
		String poslabel =  new String("Marker; Cy3/Cy5 pre-labeled Albumin");
		String neglabel = new String("bkg control; Albumin, unlabeled");
		deleteControls(cy3, cy5, poslabel, neglabel);
		
		//deleting antigens containing missing data for training and test set
		//user will be allowed to select to remove missing values or replace with BPCA
		DeleteEmpty(FindEmpty(cy3,cy5), cy3, cy5);
		
		idList = cy3.getID();
		
		//Storage for result of IRN function for test data set
		double[][][] normalized_test = new double[testID.length][cy3.getData().length][2];
		
		//Storage for result of IRN function for training data set
		double[][][] normalized_train = new double[trainID.length][cy3.getData().length][2];
		
		//double alpha = .01;
		//boolean adjFlag=true, quiet=false;
		//String fileTrain = new String("heatmap_train_sel.ps");
		//int B=3, fold=5, seed=666666;
		
		int testIDLength=testID.length;
		int trainIDLength=trainID.length;
				
		// Run IRN on all the data to get normalized results		
		for(int x=0; x<testIDLength; x++)
		{
			//System.out.println("NORMALIZED TEST: set for patient in spot: "+x);
			normalized_test[x] = IRN(cy3, cy5, testID[x]);
			//BS.printMatrix(normalized_test[x]);
		}
		System.out.println("END OF TEST SET");
		System.out.println();
		System.out.println();
		System.out.println();
		System.out.println();
		System.out.println();
		System.out.println();
		
		for(int y=0; y<trainIDLength; y++)
		{
			//System.out.println("NORMALIZED TRAIN: set for patient in spot: "+y);
			normalized_train[y] = IRN(cy3, cy5, trainID[y]);
			//BS.printMatrix(normalized_train[y]);
		}
		
		//print data
		System.out.println(" ~~~  TRAINING - data set");
		BS.printMatrix(BS.threeDtoTwoD(normalized_train));
		System.out.println(" ~~~ TEST - data set");
		BS.printMatrix(BS.threeDtoTwoD(normalized_test));
		
		
		//After normalizing the data a log ratios of the data must be taken from both sets
		
		//Log ratio of test set
		//res_test[w][x][0] = Mix1
		//res_test[w][x][1] = Mix2
		
		double[][][] res_test = new double[normalized_test.length][normalized_test[0].length][2]; 
		
		//System.out.println("&&&&&&&&&&&&&&&&");
		//System.out.println("&&&&&&&&&&&&&&&&");
		//System.out.println("&&&&&&&&&&&&&&&&");
		//System.out.println("&&&&&&&&&&&&&&&&");
		for (int w=0; w< normalized_test.length; w++) 
		{  
			for(int x=0; x<normalized_test[0].length; x++) 
			{ 
				res_test[w][x][0] = Math.log(normalized_test[w][x][0] / normalized_test[w][x][1]); 
				//System.out.print(normalized_test[w][x][0] / normalized_test[w][x][1]);
				res_test[w][x][1] = Math.log(normalized_test[w][x][2] / normalized_test[w][x][3]);
				//System.out.print(normalized_test[w][x][2] / normalized_test[w][x][3]);
			} 
			//System.out.println();
		}
				
		System.out.println("After normalization, res_test.length = " + res_test.length);
		System.out.println("After normalization, res_test[0].length = " + res_test[0].length);
		System.out.println("After normalization, res_test[0][0].length = " + res_test[0][0].length);
		
		//Log ratio of training set
		//res_train[w][x][0] = Mix1
		//res_train[w][x][1] = Mix2
		
		
		System.out.println();
		System.out.println();
		System.out.println("Taking the log ratio of normalized data");
		System.out.println();
		System.out.println();
		System.out.println();
		System.out.println();
		System.out.println();
		System.out.println();
		
		double[][][] res_train = new double[normalized_train.length][normalized_train[0].length][2]; 
		
		for (int w2=0; w2< normalized_train.length; w2++) 
		{  
			for(int x2=0; x2<normalized_train[0].length; x2++) 
			{ 
				res_train[w2][x2][0] = Math.log(normalized_train[w2][x2][0] / normalized_train[w2][x2][1]); 
				//System.out.print(normalized_train[w2][x2][0] / normalized_train[w2][x2][1]);
				res_train[w2][x2][1] = Math.log(normalized_train[w2][x2][2] / normalized_train[w2][x2][3]); 
				//System.out.print(normalized_train[w2][x2][2] / normalized_train[w2][x2][3]);
			} 
			//System.out.println();
		}
		System.out.println("After normalization, res_train.length = " + res_train.length);
		System.out.println("After normalization, res_train[0].length = " + res_train[0].length);
		System.out.println("After normalization, res_train[0][0].length = " + res_train[0][0].length);
		
		//print data
		/*System.out.println(" ~~~  TEST - data set");
		BS.printMatrix(BS.threeDtoTwoD(res_test));
		System.out.println(" ~~~ TRAINING - data set");
		BS.printMatrix(BS.threeDtoTwoD(res_train));
		*/
		
		// Implement selected selection algorithm (passed from Driver/Swing class)
		//BioMarkerSelection B = new BioMarkerSelection(cy3.getID(), testID, trainID);
		//B.pairedTTest(res_test);
		
		double[][][] temp = combineTestandTrain(res_test, res_train);
		
		System.out.println("temp.length: "+temp.length);
		System.out.println("temp[0].length: "+temp[0].length);
		System.out.println("temp[0][0].length: "+temp[0][0].length);
		
		//T-test
		System.out.println("Paired T-Test -- function");
		double[] t_test_res = BS.pairedTTest(temp);
		int pos=1;
		for(int d=0; d<t_test_res.length; d++)
		{
			if(d<idList.length)
			{
				if(t_test_res[d]<.1)
				{
					System.out.print(pos+". "+idList[d]+", ");
					pos++;
					System.out.println(t_test_res[d]);
				}
			}
		}
		
		return temp;
		
	} 
	public double[][] IRN (DataHandler cy3, DataHandler cy5, int patID)
	{
		System.out.println("Entering IRN");
		System.out.println("cy3.dimensions: bio: "+cy3.getData().length+ ", pat: "+cy3.getData()[0].length);
		System.out.println("cy5.dimensions: bio: "+cy5.getData().length+ ", pat: "+cy5.getData()[0].length);

		/*
		 * # Internal range normalization to reduce dye effect
			averaging replicates and ratio based normalization

			dat.cy3.complete must have the following format: mix1_1 mix2_1 mix1_2 mix2_2 mix1_3 mix2_3
			dat.cy5.complete must have the following format: mix1_1 mix2_1 mix1_2 mix2_2 mix1_3 mix2_3
			where mix1_2 means the second sample in the mix1; mix2_3 means the 3rd sample in mix2.

			notes?
		 * #> dat.cy3.patient[1:2,]
			#  pc.cy3.mix1  bph.cy3.mix2
			#  X41_mix1 X41_mix2
			#1   5025.5   2902.5
			#2   7977.5   5959.0
			#> dat.cy5.patient[1:2,]
			#  bph.cy5.mix1 pc.cy5.mix2
			#  X41_mix1 X41_mix2
			#1     1140   2993.5
			#2     3348   5624.5
			#
			#  dat.cy3.patient: pc.cy3.mix1, bph.cy3.mix2
			#  dat.cy5.patient: bph.cy5.mix1, pc.cy5.mix2
		 */
		System.out.println("Normalizing patient: "+patID);
		System.out.println("cy3.getHeader(): "+cy3.getHeader());
		int cy3Mix1Pos = findString(cy3.getHeader(), patID+"_mix1");
		System.out.println(patID+"_mix1");
		double[] cy3_M1 = returnCol(cy3.getData(), cy3Mix1Pos);
		//for(int a=0; a<cy3_M1.length; a++)
		//	System.out.println("cy3_M1 - a: "+a+" , "+cy3_M1[a]);
		System.out.println("cy3_M1 - 0: "+cy3_M1[0]);
		int cy3Mix2Pos = findString(cy3.getHeader(), patID+"_mix2");
		double[] cy3_M2 = returnCol(cy3.getData(), cy3Mix2Pos);
		//for(int b=0; b<cy3_M2.length; b++)
		//	System.out.println("cy3_M2 - b: "+b+" , "+cy3_M2[b]);
		System.out.println("cy3_M2 - 0: "+cy3_M2[0]);
		
		int cy5Mix1Pos = findString(cy5.getHeader(), patID+"_mix1");
		System.out.println(patID+"_mix2");
		double[] cy5_M1 = returnCol(cy5.getData(), cy5Mix1Pos);
		//for(int c=0; c<cy5_M1.length; c++)
		//	System.out.println("cy5_M1 - c: "+c+" , "+cy5_M1[c]);
		System.out.println("cy5_M1 - 0: "+cy5_M1[0]);
		
		int cy5Mix2Pos = findString(cy5.getHeader(), patID+"_mix2");
		double[] cy5_M2 = returnCol(cy5.getData(), cy5Mix2Pos);
		//for(int d=0; d<cy5_M2.length; d++)
		//	System.out.println("cy5_M2 - d: "+d+" , "+cy5_M2[d]);
		System.out.println("cy5_M2 - 0: "+cy5_M2[0]);
		
		/*
		 * # F653 -- cy5  F532-cy3 
			# IRN (Internal ratio normalization)
			# 
			# method 1 based on Section 3 at
			# http://www.wcb.ed.ac.uk/COIL/PDFs/www_axon_com_genomics_Guide_to_Microarray_Analysis_040114.pdf
			# step 1. calculate the mean of the ratio of every feature
			# (let us say that it is 1.21).
			#    r.i<-cy5.i/cy3.i
			#    r.m<-mean(r.i)=1.21
			# step 2. take the square root of this number (=sqrt(1.21)=1.1)
			#    r.m.sqrt<-sqrt(r.m)=1.1
			# step 3. multiply all numerator wavelength (mean Ratio r.i)vaues (e.g., F635 Median) by
			# 1/1.1=0.91. (cy5.i * .91)
			#    cy5.i.2<-cy5.i/r.m.sqrt
			# step 4. multiply all denominator wavelength values (e.g. F532 median) 
			# by 1.1 (cy3.i * 1.1)
			#    cy3.i.2<-cy3.i*r.m.sqrt
			# Step 5. the new global ratio value is now 1.21x(0.91/1.1)=1.0
			#   mean(cy5.i.2/cy3.i.2)=mean((cy5.i/cy3.i.2)/r.m)=mean(r.i/r.m)
			#   =mean(r.i)/r.m=r.m/r.m=1
			# 
			# cy5 bph / cy 3 pc for mix1
			# na.rm=TRUE indicates NA values should be stripped before the
			# computation proceeds
		 */
		
		//Declare variable for storage during normalization calculations
		double sum; 
		double meanRatio1, meanRatio2, ratioSqrt1, ratioSqrt2;
		double[][] results = new double[cy3_M1.length][4];
	
		//IRN method1 for Mix1
		//step 1
		sum = 0;
		for(int x=0; x<cy3_M1.length; x++)
		{
			if(cy3_M1[x] != Double.NaN)
			{
				sum = sum + (cy5_M1[x]/cy3_M1[x]);
				//System.out.print("Sum " + sum);
			}
		}
		
		//step 2
		meanRatio1 = sum/cy3_M1.length;
		System.out.println("meanRatio1 -- cy3_M1  : "+meanRatio1);
	
		ratioSqrt1 = Math.sqrt(meanRatio1);
		
		//step 3
		
		//System.out.println("cy3_M1   ~~~~~~   ratio Square root");
		sum=0;
		for(int y=0; y<cy3_M1.length; y++)
		{
			results[y][0] = cy5_M1[y]/ratioSqrt1;
			//System.out.print(", "+results[y][0]);
		}
		
		//step 4
		sum=0;
		for(int z=0; z<cy3_M1.length; z++) 
		{
			results [z][1]= cy3_M1[z]*ratioSqrt1;
			//System.out.print(results[z][1] + ", ");
		}
		//System.out.println();
	
		//IRN method1 for Mix2
		//step 1
		sum = 0;
		for(int x2=0; x2<cy3_M2.length; x2++)
			sum = sum + (cy5_M2[x2]/cy3_M2[x2]);
		
		//step 2
		meanRatio2 = sum/cy3_M2.length;
		System.out.println("MeanRatio2: cy3_M2: "+meanRatio2+", ");
		
		ratioSqrt2 = Math.sqrt(meanRatio2);
		//System.out.println("sum = "+sum);
		//System.out.println("ratioSqrt2 = "+ ratioSqrt2);
		//System.out.println("meanRatio2 = "+ meanRatio2);
		
		//step 3
		//System.out.println("cy5_M2    ~~~~~   ratio square root");
		sum=0;
		for(int y2=0; y2<cy3_M2.length; y2++)
		{
			results[y2][2] = cy5_M2[y2]/ratioSqrt2;
			//System.out.print(", "+results[y2][2]);
		}
		
		//step 4
		sum=0;
		for(int z2=0; z2<cy3_M2.length; z2++)
		{
			results [z2][3]= cy3_M2[z2]*ratioSqrt2;
		}	
		/*//step 5?
		double[][] fin = new double[results.length][2];
  		for(int a=0; a<results.length; a++)
  		{
  			fin[a][0]=results[a][0]/results[a][1];
  			fin[a][1]=results[a][2]/results[a][3];
  		}
 
  		for(int i=0; i<fin.length; i++)
		{
			for(int j=0; j<fin[0].length; j++)
			{
				System.out.print(fin[i][j]+ ", ");
			}
			System.out.println();
		}*/
	
	
		return results;	
		//return fin;
	}
	
	private double[][][] combineTestandTrain(double[][][] test, double[][][] train)
	{
		System.out.println("combineTestandTrain  --  function");
		System.out.println("test.length: "+test.length+", test[0].length: "+test[0].length+", test[0][0].length: "+test[0][0].length);
		System.out.println("train.length: "+train.length+", train[0].length: "+train[0].length+", train[0][0].length: "+train[0][0].length);

		double[][][] temp = new double[test.length + train.length][test[0].length][test[0][0].length];
		
		int both=test.length + train.length;
		for(int x=0; x<both; x++)
		{
			if(x < test.length)
			{
				for(int y=0; y<test[0].length; y++)
				{
					for(int z=0; z<test[0][0].length; z++)
					{
						temp[x][y][z] = test[x][y][z];
					}
				}
			}
			else
			{
				for(int y2=0; y2<train[0].length; y2++)
				{
					for(int z2=0; z2<train[0][0].length; z2++)
					{
						temp[x][y2][z2] = train[x-test.length][y2][z2];
					}
				}
			}
		}		
		return temp;
	}
	
	public int[][] findControls(String[] cy3Id, String[] cy5Id, String poslabel, String neglabel){
		// 2-dimensional array structured as follows
		int[][] posControls= new int[2][2];
		
		for(int i = 0; i<cy3Id.length; i++)
		{
			if(cy3Id[i].compareTo(poslabel) == 0)
				posControls[0][0] = i;
			if(cy3Id[i].compareTo(neglabel) == 0)
				posControls[0][1] = i;
		}
		for(int i = 0; i<cy5Id.length; i++)
		{
			if(cy5Id[i].compareTo(poslabel) == 0)
				posControls[1][0] = i;
			if(cy5Id[i].compareTo(neglabel) == 0)
				posControls[1][1] = i;
		}
		return posControls;
	}
	
	public String[] getIDList()
	{
		return idList;
	}
	
	//Finds and deletes controls
	public void deleteControls(DataHandler cy3, DataHandler cy5, String poslabel, String neglabel)
	{
		int[][] controls= new int[2][2];
		String[] cy3ID = cy3.getID();
		String[] cy5ID = cy5.getID();
		
		//find position of controls to be deleted
		controls= findControls(cy3ID, cy5ID, poslabel, neglabel);
		
		//temporary storage for new, shorter id list
		String[] temp = new String[cy3ID.length-1];
		String[] temp2 = new String[cy5ID.length-1];
		//new list position
		int position =0;
		
		for(int i = 0; i<cy3ID.length; i++)
		{
			if(i==controls[0][0])
				System.out.println("Found control "+cy3ID[i]+" at "+i);
			else if(i==controls[0][1])
				System.out.println("Found control "+cy3ID[i]+" at "+i);
			else
			{
				temp[position] = cy3ID[i];
				position++;
			}
		}
		//reset position
		position=0;
		for(int j = 0; j<cy5ID.length; j++)
		{
			if(j==controls[0][0])
				System.out.println("Found control "+cy5ID[j]+" at "+j);
			else if(j==controls[0][1])
				System.out.println("Found control "+cy5ID[j]+" at "+j);
			else
			{
				temp2[position] = cy5ID[j];
				position++;
			}
		}
		//set new id array back into DataHandler objects
		cy3.setID(temp);
		cy5.setID(temp2);
		
		deleteControlsData(cy3, cy5, controls);
		
	}
	
	//When controls are deleted the corresponding row of data should be removed from
	//the data in the DataHandler
	public void deleteControlsData(DataHandler cy3, DataHandler cy5, int[][] controls)
	{
		double[][] cy3data = cy3.getData();
		double[][] cy5data = cy5.getData();
		
		//temporary storage for new, shorter id list
		double[][] temp = new double[cy3data.length-2][cy3data[0].length];
		double[][] temp2 = new double[cy5data.length-2][cy5data[0].length];
		
		//new list position
		int position =0;
		
		for(int i = 0; i<cy3data.length; i++)
		{
			if(i==controls[0][0])
			{
				System.out.println("Found data control "+cy3data[i]+" at "+i);
				cy3.setBioMarkers(cy3.getBioMarkers()-1);
			}
			else if(i==controls[0][1])
			{
				System.out.println("Found data control "+cy3data[i]+" at "+i);
				cy3.setBioMarkers(cy3.getBioMarkers()-1);
			}
			else
			{
				for(int j=0; j<cy3data[0].length; j++)
				{
					temp[position][j] = cy3data[i][j];
				}
				position++;
			}
		}
		//reset position
		position=0;
		for(int i2 = 0; i2<cy5data.length; i2++)
		{
			if(i2==controls[0][0])
			{
				System.out.println("Found data control at "+i2);
				cy5.setBioMarkers(cy5.getBioMarkers()-1);
			}
			else if(i2==controls[0][1])
			{
				System.out.println("Found data control at "+i2);
				cy5.setBioMarkers(cy5.getBioMarkers()-1);
			}
			else
			{
				for(int j2=0; j2<cy5data[0].length; j2++)
				{
					temp2[position][j2] = cy5data[i2][j2];
				}
				position++;
			}
		}
		//set new data array back into DataHandler objects
		cy3.setData(temp);
		cy5.setData(temp2);
	}
	public int[] FindEmpty(DataHandler cy3, DataHandler cy5)
	{
		int count[]= new int[cy3.getBioMarkers()];
		int index=0;
		
		
		for(int i=0; i<cy3.getData().length; i++)
		{			
			for(int j=0; j<cy3.getData()[0].length; j++)
			{
				if (cy3.getData()[i][j]==-2|| cy5.getData()[i][j]==-2)//||cy3.getData()[i][j]==-1.0||cy5.getData()[i][j]==-1.0)//||(cy3.getData()[i][j]==0||cy5.getData()[i][j]==0))
				{
					if(count[index]>0)
					{
						count[index]++;					
					}
					else
					{
						count[index]++;
						deleted++;
					}					
				}	
				
			}					
			index++;
		}
		return count;
	}
	public void DeleteEmpty(int[] count, DataHandler cy3, DataHandler cy5)
	{
		double[][] cy3Deleted = new double[cy3.getData().length-deleted][cy3.getPatients()];
		double[][] cy5Deleted = new double[cy5.getData().length-deleted][cy5.getPatients()];
	
		String[] tempID = new String[cy3Deleted.length];
		String[] tempID2 = new String[cy5Deleted.length];
		
		System.out.println("Deleted = " + deleted);
		System.out.println("tempID.length = "+tempID.length);
		System.out.println("cy3Deleted.length = "+cy3Deleted.length + ", cy5Deleted[0].length = "+cy5Deleted[0].length);
		System.out.println("count.length = "+count.length);
	
		int position=0;
		for(int k=0; k<count.length; k++)			
		{
			//&& position<tempID.length may make the new data one too small
			if(count[k]==0 && position<tempID.length)
			{
				tempID[position] = cy3.getID()[k];
				tempID2[position] = cy5.getID()[k];
				for(int j=0; j<cy3.getData()[0].length; j++)
				{
					cy3Deleted[position][j]=cy3.getData()[k][j];
					cy5Deleted[position][j]=cy5.getData()[k][j];
				}
				position++;
			}
		}
		//Set new data in DataHandler objects
		cy3.setData(cy3Deleted);
		cy5.setData(cy5Deleted);
		//Set new IDs in DataHandler objects
		cy3.setID(tempID);
		cy5.setID(tempID2);
		
		cy3.printDataHeader();
		cy3.printData();
		
		//Set new biomarker length in DataHandler objects
		cy3.setBioMarkers(cy3.getBioMarkers()-deleted);
		cy5.setBioMarkers(cy5.getBioMarkers()-deleted);
	}

	public int findString(String[] names, String pat)
	{
		//# iterate through array names looking for matches
		for(int x=0; x<names.length; x++)
			if(names[x].matches(pat))
				return x;
		return -1;
	}
	public double[] returnCol(double[][] data, int pos)
	{
		double[] tmp = new double[data.length];
		for(int x=0; x<data.length; x++)
		{
			tmp[x]=data[x][pos];
		}
		return tmp;
	}
}