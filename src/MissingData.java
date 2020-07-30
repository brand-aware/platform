import java.io.*;


/* Class Missing Data
 * created to output data that has been read into dataHandler class
 * into a specialized output file format
 * 
 * currently file format is a tab delimited spreadsheet
 * with 999.0 to represent all missing values in the dataset
 * format specified by http://hawaii.naist.jp/~shige-o/tools/JBPCAFill.html
 * creater of BPCA jar being used for missing value imputation
 */
public class MissingData {
	
	//used to store DataHandler object passed in from constructor
	private DataHandler handle1, handle2;
	//used to store data from DataHandler object
	private double[][] data1, data2;
	
		//Constructor takes in DataHandler object and saves the data in a double[][]
		public MissingData(DataHandler d1, DataHandler d2)
		{
			handle1=d1;
			handle2=d2;
			data1=handle1.getData();
			data2=handle2.getData();
			
			//Create data to be passed to BPCA functionality
			createDatafile();
		}
		public MissingData(){}
		
		//Output into working directory a new output.txt file
		public void createDatafile()
		{
			FileOutputStream out1, out2; // declare a file output object 
			PrintStream p1, p2; // declare a print stream object 
			try { // Create a new file output stream
				out1 = new FileOutputStream("output.dat"); // Connect print stream to the output stream 
				out2 = new FileOutputStream("output2.dat"); // Connect print stream to the output stream 

				p1 = new PrintStream( out1 );
				p2 = new PrintStream( out2 );
				
				for(int x=0; x<data1.length; x++)
				{
					if(data1[x][0] == -2)
						p1.print(999.0);
					else
						p1.print(data1[x][0]);
					if(data2[x][0] == -2)
						p2.print(999.0);
					else
						p2.print(data2[x][0]);
					//print data matrix delimited by tabs
					for(int y=0; y<(data1[0].length-1); y++)
					{
						if(data1[x][y+1] == -2)
						{
							p1.print('\t');
							p1.print(999.0);
						}
						else
						{
							p1.print('\t');
							p1.print(data1[x][y+1]);
						}
						if(data2[x][y+1] == -2)
						{
							p2.print('\t');
							p2.print(999.0);
						}
						else
						{
							p2.print('\t');
							p2.print(data2[x][y+1]);
						}
					}
					p1.println();
					p2.println();
				}
				p1.close(); 
				p2.close();
			} 
			//catch all exceptions
			catch (Exception e) 
			{ 
				System.err.println ("Error writing to file");
			}
		}
}