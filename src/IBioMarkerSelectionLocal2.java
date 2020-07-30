import java.io.File;

public interface IBioMarkerSelectionLocal2 {
	
	public void setID(String[] tmp);
	
	public void setTestPat(int[] pat);
	
	public void setTrainPat(int[] pat);
	
	public void backwardsElimination(double[][][] data);
	
	public int findBest(double[] list);
	
	public double[][][] removeRow(double[][][] temp, String[] idList, double x);
	
	public String[] removeID(String[] list, int x);
	
	public double[][][] removeRow_keepLast(double[][][] temp, double x);
	
	public double[][][] addRow(double[][][] temp, double[] row, double position);
	
	public double[][] threeDtoTwoD(double[][][] old);
	
	public double crossValidate(double[][] data, int fold);
	
	public double RandomForestTest(File Data) throws Exception;
	
	public double[][] createRandomGroups(double[][] data);
	
	public int[] randomizePatients(int[] pat);
	
	public boolean anyLeft(int[] left);
	
	public double[][] transpose(double[][] data);
	
	public void printMatrix(double[][] data);
	
	public double crossValidDefault(double[][] data, int fold);
	
	public void prepCSV(double[][] data, int fold);

}
