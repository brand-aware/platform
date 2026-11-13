import java.io.*;
import java.util.*;

public class DataHandler {
	
	private double[][] data;	//used to store an
	// header used to store header labels for columns in data file
	// id is the list of labels associated with row names
	private String[] header, id;  
	//patients is the measurement-column count (two mix columns per patient)
	//biomarkers is the row count
	int patientCount = 0, patients = 0, biomarkers = 0;
	
	//Constructor requires row and column count be entered
	public DataHandler(int p, int b){
		patientCount = p;
		patients = p * 2;
		//Assumes header line is included in row count
		biomarkers=b-1;
	}
	//When passed a file name in a string, readCSVSheet reads in the data and 
	//stores in in the double[][] data
	public void readCSVSheet(String fileName)
	{
		readDelimitedSheet(fileName, true);
	}
	
	public void readDATSheets(String name)
	{
		readDelimitedSheet(name, false);
	}

	private void readDelimitedSheet(String fileName, boolean hasHeader)
	{
		List<String> lines = new ArrayList<String>();
		try {
			Scanner file = new Scanner(new FileReader(fileName));
			try {
				while (file.hasNextLine()) {
					String line = file.nextLine();
					if (!line.trim().isEmpty()) {
						lines.add(line);
					}
				}
			} finally {
				file.close();
			}
		} catch (IOException e) {
			throw new IllegalArgumentException("Could not read data file: " + fileName, e);
		}

		if (lines.size() <= (hasHeader ? 1 : 0)) {
			throw new IllegalArgumentException("Data file is empty: " + fileName);
		}

		String delimiter = detectDelimiter(lines.get(0), !hasHeader);
		int firstDataRow = hasHeader ? 1 : 0;
		String[] firstRow = split(lines.get(firstDataRow), delimiter);
		int columnCount = firstRow.length;
		if (hasHeader && patientCount > 0 && columnCount != patientCount * 2) {
			throw new IllegalArgumentException("Data has " + columnCount
					+ " measurement columns, but " + patientCount
					+ " patients require " + (patientCount * 2) + " columns (two mixes each): "
					+ fileName);
		}
		patients = columnCount;
		patientCount = patients / 2;
		biomarkers = lines.size() - firstDataRow;
		data = new double[biomarkers][patients];

		if (hasHeader) {
			header = split(lines.get(0), delimiter);
			if (header.length != patients) {
				throw new IllegalArgumentException("Header has " + header.length
						+ " columns but the data has " + patients + " in " + fileName);
			}
		} else if (header == null || header.length != patients) {
			header = new String[patients];
		}

		for (int rowIndex = 0; rowIndex < biomarkers; rowIndex++) {
			String[] row = split(lines.get(firstDataRow + rowIndex), delimiter);
			if (row.length != patients) {
				throw new IllegalArgumentException("Row " + (firstDataRow + rowIndex + 1)
						+ " has " + row.length + " columns; expected " + patients
						+ " in " + fileName);
			}
			for (int column = 0; column < patients; column++) {
				String value = deleteQuotes(row[column]).trim();
				if (value.isEmpty() || value.equalsIgnoreCase("NA")) {
					data[rowIndex][column] = -2;
				} else {
					try {
						data[rowIndex][column] = Double.parseDouble(value);
					} catch (NumberFormatException e) {
						throw new IllegalArgumentException("Invalid number '" + value + "' at row "
								+ (firstDataRow + rowIndex + 1) + ", column " + (column + 1)
								+ " in " + fileName, e);
					}
				}
			}
		}
	}

	private String detectDelimiter(String line, boolean allowWhitespace)
	{
		if (line.indexOf(';') >= 0) return ";";
		if (line.indexOf('\t') >= 0) return "\t";
		if (line.indexOf(',') >= 0) return ",";
		if (allowWhitespace && line.trim().matches(".*\\s+.*")) return "\\s+";
		throw new IllegalArgumentException("Data must be comma, semicolon, or tab delimited");
	}

	private String[] split(String line, String delimiter)
	{
		if (delimiter.equals("\\s+")) return line.trim().split(delimiter);
		return line.split(java.util.regex.Pattern.quote(delimiter), -1);
	}
	
	//read in text file of data labels
	public String[] readTXTData(String fileName)
	{
		List<String> labels = new ArrayList<String>();
		try (Scanner file = new Scanner(new FileReader(fileName))) {
			while (file.hasNextLine()) labels.add(deleteQuotes(file.nextLine()).trim());
			id = labels.toArray(new String[labels.size()]);
			return id;
		} catch (IOException e) {
			throw new IllegalArgumentException("Could not read ID list: " + fileName, e);
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
	// Return the logical patient count. Each patient has two measurement columns.
	public int getPatientCount()
	{
		return patientCount;
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
