/**
	 * Created 06-26-08
	 * Mike Drummond and Corey Lander
	 * Written for Brigham and Women's Hospital
	 */

public class Normalization {
	//Normalization class 
	
	int deleted = 0;
	double[] train, test;
	DataHandler cy3, cy5;
	BioMarkerSelection BS;
	
	String[] idList;
	
	Normalization()
	{
		BS = new BioMarkerSelection();
	}
	
	public double[][][] predict(DataHandler cy3_tmp, DataHandler cy5_tmp, boolean missing, String positiveCtrl, String negativeCtrl)
	{
		//data declarations
		//String list = new String("sig_81_genes_oldanalysis.txt");
		cy3 = cy3_tmp;
		cy5 = cy5_tmp;
		idList = cy3.getID();
		//training and test id currently set manually, to be added later
		//user specification of id fields
		//int [] testID = {101, 128, 136, 151, 32, 34, 43, 45, 58, 89};
		//int[] trainID = {41,47,50,56,74, 76, 79,87,93,115};
		
		//print data
		System.out.println("GOT HERE!!!!!");
		//System.out.println("CY3~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
		if(missing)
			cy3.readDATSheets("output_filled.txt");
		//cy3.printData();
		//System.out.println("CY5~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
		if(missing)
			cy5.readDATSheets("output_filled2.txt");
		//cy5.printData();
		
		//Finding and deleting positive and negative controls
		//controls currently manually set, to be added later - ability for user selection
		//Positive and Negative labels to find
		String positivelabel = positiveCtrl;
		String negativelabel = negativeCtrl;
		deleteControls(cy3, cy5, positivelabel, negativelabel);
		
		//deleting antigens containing missing data for training and test set
		//user will be allowed to select to remove missing values or replace with BPCA
		DeleteEmpty(FindEmpty(cy3,cy5), cy3, cy5);
		
		idList = cy3.getID();
		
		//Storage for result of IRN function for entire data set
		double[][][] normalized_data = new double[cy3.getPatients() / 2][cy3.getBioMarkers()][2];
		
		//double alpha = .01;
		//String fileTrain = new String("heatmap_train_sel.ps");
		//int B=3, fold=5, seed=666666;
	
				
		// Run IRN on all the data to get normalized results
		System.out.println("cy3.getPatients(): " + (cy3.getPatients()));
		int x;
		for(x = 0; x < cy3.getPatients(); x += 2)
		{
			System.out.println("NORMALIZED data for patient starting in column: " + x);
			normalized_data[x / 2] = IRN(cy3, cy5, x);
			//BS.printMatrix(normalized_test[x]);
		}
		
		//print data
		//System.out.println(" ~~~  PRINTING NORMALIZATION - data set");
		//BS.printMatrix(BS.threeDtoTwoD(normalized_data));		
		
		//After normalizing the data a log ratios of the data must be taken from both sets
		
		// Implement selected selection algorithm (passed from Driver/Swing class)
		//BioMarkerSelection B = new BioMarkerSelection(cy3.getID(), testID, trainID);
		//B.pairedTTest(res_test);
				
		System.out.println("normalized_data.length: " + normalized_data.length);
		System.out.println("normalized_data[0].length: " + normalized_data[0].length);
		System.out.println("normalized_data[0][0].length: " + normalized_data[0][0].length);
		
		//T-test
		System.out.println("Paired T-Test -- function");
		double[] t_test_res = BS.pairedTTest(logRatio(normalized_data));
		int position = 1;
		for(int d=0; d<t_test_res.length; d++)
		{
			if(d<idList.length)
			{
				if(t_test_res[d]<.1)
				{
					System.out.print(position + ". "+idList[d] + ", ");
					position++;
					System.out.println(t_test_res[d]);
				}
			}
		}
		
		return logRatio(normalized_data);
		
	} 
	
	public double[][][] logRatio(double[][][] data)
	{
		System.out.println("Entering logRatio --  function");
		System.out.println("data.length: " + data.length + ", data[0].length: " + data[0].length + ", data[0][0].length: " + data[0][0].length);
		//Log ratio of test set
		//res_test[w][x][0] = Mix1
		//res_test[w][x][1] = Mix2
		
		double[][][] res = new double[data.length][data[0].length][2]; 
		
		for (int w = 0; w < data.length; w++) 
		{
			//System.out.println("logRatio data for w: "+w+":");
			for(int x = 0; x < data[0].length; x++) 
			{
				//System.out.print("data for x: "+x+": ");
				res[w][x][0] = Math.log(data[w][x][0] / data[w][x][1]); 
				//System.out.print("data[w]("+w+")[x]("+x+")[0]: "+data[w][x][0]+" / data[w][x][1]: "+data[w][x][1]+" = "+data[w][x][0] / data[w][x][1] + ", ");
				res[w][x][1] = Math.log(data[w][x][3] / data[w][x][2]);
				//System.out.println("data[w]("+w+")[x]("+x+")[0]: "+data[w][x][0]+" / data[w][x][1]: "+data[w][x][1]+" = "+data[w][x][2] / data[w][x][3] + ", ");
			}
		}
		return res;
	}
	
	public double[][] IRN (DataHandler cy3, DataHandler cy5, int p)
	{
		System.out.println("Entering IRN");
		System.out.println("cy3.dimensions: bio: " + cy3.getData().length + ", pat: " + cy3.getData()[0].length);
		System.out.println("cy5.dimensions: bio: " + cy5.getData().length + ", pat: " + cy5.getData()[0].length);
		
		int position = p;
		/*
		  # Internal range normalization to reduce dye effect
			averaging replicates and ratio based normalization

			dat.cy3.complete must have the following format: mix1_1 mix2_1 mix1_2 mix2_2 mix1_3 mix2_3
			dat.cy5.complete must have the following format: mix1_1 mix2_1 mix1_2 mix2_2 mix1_3 mix2_3
			where mix1_2 means the second sample in the mix1; mix2_3 means the 3rd sample in mix2.

		    dat.cy3.patient[1:2,]
			pc.cy3.mix1  bph.cy3.mix2
			X41_mix1 X41_mix2
			1   5025.5   2902.5
			2   7977.5   5959.0
			> dat.cy5.patient[1:2,]
			  bph.cy5.mix1 pc.cy5.mix2
			  X41_mix1 X41_mix2
			1     1140   2993.5
			2     3348   5624.5
			
			  dat.cy3.patient: pc.cy3.mix1, bph.cy3.mix2
			  dat.cy5.patient: bph.cy5.mix1, pc.cy5.mix2
		 */
		System.out.println("Normalizing patient: " + (position / 2));
		double[] cy3_M1 = returnCol(cy3.getData(), position);
		//for(int a=0; a<cy3_M1.length; a++)
		//	System.out.print("cy3_M1 - a: "+a+" , "+cy3_M1[a]);
		//System.out.println();

		double[] cy3_M2 = returnCol(cy3.getData(), (position + 1));
		//for(int b=0; b<cy3_M2.length; b++)
		//	System.out.print("cy3_M2 - b: "+b+" , "+cy3_M2[b]);
		//System.out.println();
		
		double[] cy5_M1 = returnCol(cy5.getData(), position);
		//for(int c=0; c<cy5_M1.length; c++)
		//	System.out.print("cy5_M1 - c: "+c+" , "+cy5_M1[c]);
		//System.out.println();
		
		double[] cy5_M2 = returnCol(cy5.getData(), (position + 1));
		//for(int d=0; d<cy5_M2.length; d++)
		//	System.out.print("cy5_M2 - d: "+d+" , "+cy5_M2[d]);
		//System.out.println();
		
		/*
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
		for(int x = 0; x < cy3_M1.length; x++)
		{
			if(cy3_M1[x] != Double.NaN)
			{
				sum = sum + (cy5_M1[x] / cy3_M1[x]);
				//System.out.print("Sum " + sum);
			}
		}
		
		//step 2
		meanRatio1 = sum / cy3_M1.length;
		System.out.println("meanRatio1 -- cy3_M1  : "+meanRatio1);
	
		ratioSqrt1 = Math.sqrt(meanRatio1);
		
		//step 3
		
		//System.out.println("cy3_M1   ~~~~~~   ratio Square root");
		sum = 0;
		for(int y = 0; y < cy3_M1.length; y++)
		{
			results[y][0] = cy5_M1[y] / ratioSqrt1;
			//System.out.print(", "+results[y][0]);
		}
		
		//step 4
		sum = 0;
		for(int z = 0; z < cy3_M1.length; z++) 
		{
			results [z][1] = cy3_M1[z] * ratioSqrt1;
			//System.out.print(results[z][1] + ", ");
		}
		//System.out.println();
	
		//IRN method1 for Mix2
		//step 1
		sum = 0;
		for(int x2 = 0; x2 < cy3_M2.length; x2++)
			sum = sum + (cy5_M2[x2] / cy3_M2[x2]);
		
		//step 2
		meanRatio2 = sum / cy3_M2.length;
		System.out.println("MeanRatio2: cy3_M2: " + meanRatio2 + ", ");
		
		ratioSqrt2 = Math.sqrt(meanRatio2);
		//System.out.println("sum = "+sum);
		//System.out.println("ratioSqrt2 = "+ ratioSqrt2);
		//System.out.println("meanRatio2 = "+ meanRatio2);
		
		//step 3
		//System.out.println("cy5_M2    ~~~~~   ratio square root");
		sum = 0;
		for(int y2 = 0; y2 < cy3_M2.length; y2++)
		{
			results[y2][2] = cy5_M2[y2] / ratioSqrt2;
			//System.out.print(", "+results[y2][2]);
		}
		
		//step 4
		sum = 0;
		for(int z2 = 0; z2 < cy3_M2.length; z2++)
		{
			results[z2][3] = cy3_M2[z2] * ratioSqrt2;
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
	
	public int[][] findControls(String[] cy3Id, String[] cy5Id, String positivelabel, String negativelabel){
		// 2-dimensional array structured as follows
		int[][] positiveControls = new int[2][2];
		for (int[] channel : positiveControls) {
			channel[0] = -1;
			channel[1] = -1;
		}
		
		for(int i = 0; i < cy3Id.length; i++)
		{
			if(cy3Id[i].compareTo(positivelabel) == 0)
				positiveControls[0][0] = i;
			if(cy3Id[i].compareTo(negativelabel) == 0)
				positiveControls[0][1] = i;
		}
		for(int i = 0; i < cy5Id.length; i++)
		{
			if(cy5Id[i].compareTo(positivelabel) == 0)
				positiveControls[1][0] = i;
			if(cy5Id[i].compareTo(negativelabel) == 0)
				positiveControls[1][1] = i;
		}
		return positiveControls;
	}
	
	public String[] getIDList()
	{
		return idList;
	}
	
	//Finds and deletes controls
	public void deleteControls(DataHandler cy3, DataHandler cy5, String poslabel, String neglabel)
	{
		int[][] controls = new int[2][2];
		String[] cy3ID = cy3.getID();
		String[] cy5ID = cy5.getID();
		if (cy3ID == null || cy5ID == null)
			throw new IllegalArgumentException("Load both ID lists before normalization");
		
		//find position of controls to be deleted
		controls = findControls(cy3ID, cy5ID, poslabel, neglabel);
		boolean anyControl = false;
		boolean allControls = true;
		for (int channel = 0; channel < 2; channel++) {
			for (int control = 0; control < 2; control++) {
				anyControl |= controls[channel][control] >= 0;
				allControls &= controls[channel][control] >= 0;
			}
		}
		if (!anyControl) {
			System.out.println("Named control labels are absent from the ID lists; retaining all data rows.");
			return;
		}
		if (!allControls)
			throw new IllegalArgumentException("Control labels must be present in both Cy3 and Cy5 ID lists: "
					+ poslabel + " and " + neglabel);
		if (controls[0][0] == controls[0][1] || controls[1][0] == controls[1][1])
			throw new IllegalArgumentException("Positive and negative control labels must identify different rows");
		if (controls[0][0] >= cy3.getData().length || controls[0][1] >= cy3.getData().length
				|| controls[1][0] >= cy5.getData().length || controls[1][1] >= cy5.getData().length)
			throw new IllegalArgumentException("Control ID rows do not align with the data rows");
		
		//temporary storage for new, shorter id list
		String[] temp = new String[cy3ID.length - 2];
		String[] temp2 = new String[cy5ID.length - 2];
		//new list position
		int position = 0;
		
		for(int i = 0; i < cy3ID.length; i++)
		{
			if(i == controls[0][0])
				System.out.println("Found control " + cy3ID[i] + " at " + i);
			else if(i == controls[0][1])
				System.out.println("Found control " + cy3ID[i] + " at " + i);
			else
			{
				temp[position] = cy3ID[i];
				position++;
			}
		}
		//reset position
		position = 0;
		for(int j = 0; j < cy5ID.length; j++)
		{
			if(j == controls[1][0])
				System.out.println("Found control " + cy5ID[j] + " at " + j);
			else if(j == controls[1][1])
				System.out.println("Found control " + cy5ID[j] + " at " + j);
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
		double[][] temp = new double[cy3data.length - 2][cy3data[0].length];
		double[][] temp2 = new double[cy5data.length - 2][cy5data[0].length];
		
		//new list position
		int position = 0;
		
		for(int i = 0; i < cy3data.length; i++)
		{
			if(i == controls[0][0])
			{
				System.out.println("Found data control " + cy3data[i] + " at " + i);
				cy3.setBioMarkers(cy3.getBioMarkers() - 1);
			}
			else if(i == controls[0][1])
			{
				System.out.println("Found data control " + cy3data[i] + " at " + i);
				cy3.setBioMarkers(cy3.getBioMarkers() - 1);
			}
			else
			{
				for(int j = 0; j < cy3data[0].length; j++)
				{
					temp[position][j] = cy3data[i][j];
				}
				position++;
			}
		}
		//reset position
		position = 0;
		for(int i2 = 0; i2 < cy5data.length; i2++)
		{
			if(i2 == controls[1][0])
			{
				System.out.println("Found data control at " + i2);
				cy5.setBioMarkers(cy5.getBioMarkers() - 1);
			}
			else if(i2 == controls[1][1])
			{
				System.out.println("Found data control at " + i2);
				cy5.setBioMarkers(cy5.getBioMarkers() - 1);
			}
			else
			{
				for(int j2 = 0; j2 < cy5data[0].length; j2++)
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
		int count[] = new int[cy3.getBioMarkers()];
		int index = 0;
		
		for(int i = 0; i < cy3.getData().length; i++)
		{			
			for(int j = 0; j < cy3.getData()[0].length; j++)
			{
				if (cy3.getData()[i][j] == -2 || cy5.getData()[i][j] == -2)//||cy3.getData()[i][j]==-1.0||cy5.getData()[i][j]==-1.0)//||(cy3.getData()[i][j]==0||cy5.getData()[i][j]==0))
				{
					if(count[index] > 0)
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
		System.out.println("tempID.length = " + tempID.length);
		System.out.println("cy3Deleted.length = " + cy3Deleted.length + ", cy5Deleted[0].length = " + cy5Deleted[0].length);
		System.out.println("count.length = "+count.length);
	
		int position = 0;
		for(int k = 0; k < count.length; k++)			
		{
			//&& position<tempID.length may make the new data one too small
			if(count[k] == 0 && position < tempID.length)
			{
				tempID[position] = cy3.getID()[k];
				tempID2[position] = cy5.getID()[k];
				for(int j = 0; j < cy3.getData()[0].length; j++)
				{
					cy3Deleted[position][j] = cy3.getData()[k][j];
					cy5Deleted[position][j] = cy5.getData()[k][j];
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
		cy3.setBioMarkers(cy3.getBioMarkers() - deleted);
		cy5.setBioMarkers(cy5.getBioMarkers() - deleted);
	}

	public double[] returnCol(double[][] data, int pos)
	{
		
		double[] tmp = new double[data.length];
		for(int x = 0; x < data.length; x++)
		{
			tmp[x] = data[x][pos];
		}
		return tmp;
	}
}
