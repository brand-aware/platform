import java.io.*;
import java.util.concurrent.atomic.AtomicLong;
public class MultiThread extends Thread{
	
	BioMarkerSelection_Local selection;
	
	MultiThread() {
	}
	//We need to create a new constructor that initializes 
	//all the values needed in crossvalid.default
	//then also run the thread
	MultiThread(String threadName, BioMarkerSelection_Local BMS) {
		super(threadName); // Initialize thread.
		System.out.println(this);
		selection = BMS;
		start();
	}
	public double run(int fold, double[][] data, AtomicLong count) {
		//This is where we implement crossvalid.default
		double errorRate = 0;
		double[] error = new double[fold];
		
		double[][] trans_data = selection.transpose(data);
		int pairCount = trans_data[0].length / 2;
		if (fold < 2 || trans_data[0].length % 2 != 0 || fold > pairCount) {
			throw new IllegalArgumentException("Fold count must be between 2 and the number of paired samples");
		}
		int start, stop;
		for(int x = 0; x < fold; x++) 
		{ 
			long fileIndex = count.incrementAndGet();
			start = 2 * (x * pairCount / fold);
			stop = 2 * ((x + 1) * pairCount / fold);
			//System.out.println("start "+start+" stop "+ stop + "Intervale: " +interval);
			selection.prepCSV(start, stop, trans_data, fold, fileIndex);

			try 
			{ 
			
				error[x] = selection.RandomForest(selection.createNewArff(new File("data_temp" + fileIndex + ".csv"), fileIndex), start, stop);
			
			} 
			catch (Exception e) 
			{ 
				throw new IllegalStateException("Cross-validation fold " + fileIndex + " failed", e); 
			} 
			finally {
				TemporaryDataFiles.cleanupFold(fileIndex);
			}

		} 
		
		//sum error returned from random forest 
		for(int y = 0; y < fold; y++) 
		{ 
			errorRate += error[y]; 
			
		} 
		//get mean error 
		//delete(fold);
		
		return (errorRate / fold);
	}		
}
