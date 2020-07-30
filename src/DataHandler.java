import java.io.*;
import java.util.*;

public class DataHandler {
	
	private double[][] data;	//used to store an
	// header used to store header labels for columns in data file
	// id is the list of labels associated with row names
	private String[] header, id;  
	//patients is the column count
	//biomarkers is the row count
	int patients =0,biomarkers=0;
	
	//Constructor requires row and column count be entered
	public DataHandler(int p, int b){
		patients = p;
		//Assumes header line is included in row count
		biomarkers=b-1;
	}
	//When passed a file name in a string, readCSVSheet reads in the data and 
	//stores in in the double[][] data
	public void readCSVSheet(String fileName)
	{
		//create data and header objects to store data from file
		data = new double[biomarkers][patients];
		header = new String[patients];
		//double loop to initialize all values in data to -1
		for(int a=0; a<patients; a++)
		{
			for(int b=0; b<biomarkers; b++)
			{
				data[b][a]=-1;
				
			}
			header[a]=null;
		}
		//OPEN file here...
		try{
			//try to read in file passed in from param
			Scanner file = new Scanner(new FileReader(fileName));
			String tmp;
			int length, position=0;
			String[] row;
		
			//remove header line of csv file
			tmp = file.nextLine();
			header = tmp.split(",");
			
			//read in csv file and save in data[][]
			while(file.hasNext())
			{
				tmp = file.nextLine();
				row = tmp.split(",");
				length=row.length;
				for(int x=0; x<length; x++)
				{
					//Handle
					if(row[x].matches("NA"))
					{
						data[position][x]=-2;
					}
					else
					{
						double temp=Double.parseDouble(row[x]);
						data[position][x] = temp;
					}
				}	
				position++;
			}
		}
		catch(Exception e){
			//File not found exception
			System.out.println("File not found");
			System.out.println(e.getLocalizedMessage());
		}
	}
	
	public void readDATSheets(String name)
	{
		//create data and header objects to store data from file
		data = new double[biomarkers][patients];
		//double loop to initialize all values in data to -1
		for(int a=0; a<patients; a++)
		{
			for(int b=0; b<biomarkers; b++)
			{
				data[b][a]=-1;
			}
			header[a]=null;
		}
		//OPEN file here...
		try{
			//try to read in file passed in from param
			Scanner file = new Scanner(new FileReader(name));
			String tmp;
			int length, position=0;
			String[] row;
			
			//read in csv file and save in data[][]
			while(file.hasNext())
			{
				tmp = file.nextLine();
				row = tmp.split("\t");
				length=row.length;
				for(int x=0; x<length; x++)
				{
					//Handle
					if(row[x].matches("NA"))
					{
						data[position][x]=-2;
					}
					else
					{
						double temp=Double.parseDouble(row[x]);
						data[position][x] = temp;
					}
				}	
				position++;
			}
		}
		catch(Exception e){
			//File not found exception
			System.out.println("File not found");
			System.out.println(e.getLocalizedMessage());
		}
	}
	
	//read in text file of data labels
	public String[] readTXTData(String fileName)
	{
		try
		{
			id = new String[1000];
			for(int x=0; x<id.length; x++)
				id[x]="-1";
			int position = 0;
			Scanner file = new Scanner(new FileReader(fileName));
			while (file.hasNext())
			{
				id[position]=deleteQuotes(file.nextLine());
	
				position++;
			}
			return id;
		}
		catch(Exception e)
		{
			//Exception found while opening file or reading file
			System.out.println("file not found");
			System.out.println(e.getLocalizedMessage());
			return null;
		}
	}
	//return data
	public double[][] getData()
	{
		return data;
	}
	//return header
	public String[] getHeader()
	{
		return header;
	}
	//returns String[] of biomarker (row) labels
	public String[] getID()
	{
		return id;
	}
	//returns number of 
	public int getBioMarkers()
	{
		return biomarkers;
	}
	//return number of patients, or column count
	public int getPatients()
	{
		return patients;
	}
	//set biomarkers or row size
	public void setBioMarkers(int B)
	{
		biomarkers=B;
	}
	//set data requires double[][] -- used to change data in the object
	public void setData(double[][] array)
	{
		data =array;
	}
	public void setID(String[] temp)
	{
		id=temp;
	}
	//print data matrix
	public void printData()
	{
		int x=data.length;
		int y=data[0].length;
		boolean p=false;
		
		//double loop though all the data, printing out each cell
		for(int i=0; i<x; i++)
		{
			p=false;
			for(int j=0; j<y; j++)
			{
				//is cell contains a missing value, print out NA
				if(data[i][j]==-2 || data[1][j]==999.0)
				{
					System.out.print("NA, ");
					p=true;
				}
				else if(data[i][j] != -1)
				{
					System.out.print(data[i][j]+", ");
					p=true;
				}
			}
			if(p)
				System.out.println();
		}
	}
	//print header for each column
	public void printDataHeader()
	{
		for(int z=0; z<header.length; z++)
		{
			if(header[z]!=null)
			{
				System.out.print(header[z]+", ");
			}
		}
		System.out.println();
	}
	//loops though ID array and prints out stored row labels
	public void printID()
	{
		for(int z=0; z<id.length; z++)
		{
			if(!id[z].matches("-1") && !(id[z]==null))
				System.out.println(z + " " + id[z]);
		}
	}
	//Remove quotations " " from a string passed into the function
	//returns a string
	public String deleteQuotes(String old) 
	{ 
		String tmp=""; 
		for(int x=0; x<old.length(); x++) 
			if(old.charAt(x)!='"') 
				tmp+=old.charAt(x); 
		return tmp; 
	}
}