import java.io.*;
public class MultiThread extends Thread{
		
	MultiThread() {
	}
	//We need to create a new constructor that initializes 
	//all the values needed in crossvalid.default
	//then also run the thread
	MultiThread(String threadName) {
		super(threadName); // Initialize thread.
		System.out.println(this);
		start();
	}
	public void run() {
		//This is where we implement crossvalid.default
		String foo = new String();
		while(true)
		{
			foo += "Hey this is a thread test!";
		}	
	}		
}
