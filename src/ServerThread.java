import java.io.*;
import java.net.*;
import java.util.*;

public class ServerThread extends Thread {
	
    protected DatagramSocket socket = null;
    
    protected BufferedReader in = null;
    protected boolean moreQuotes = true;
    InetAddress address;
    DatagramPacket packet;
    byte[] buf = new byte[256];
    double errorRate;
    int position=-1;
    boolean done =true;
    double[][] Norm;
    public ServerThread( String Ip) throws IOException {
    	
    	address = InetAddress.getByName(Ip);
    	socket = new DatagramSocket();
	
    }
    public void setData(double[][] n, int p)
    {
    	Norm=n;
    	position=p;
    }
    public void setDone(boolean d)
    {
    	done=d;
    }
    public int getPosition()
    {
    	return position;
    }
    public boolean getIsDone()
    {
    	return done;
    }
    public double getErrorRate()
    {
    	return errorRate;
    }
    
    public void run() 
    {
    	sendArray();
    	
    	try{
	    buf=new byte[256];
	    packet = new DatagramPacket(buf, buf.length);
	    socket.receive(packet);
	    
	    String received = new String(packet.getData(), 0, packet.getLength());
	    System.out.println("ErrorRate: "+received);
	    errorRate = Double.parseDouble(received);
	    done=true;
		} catch (IOException e) {
	        e.printStackTrace();

		}
    	 
    }
    public void sendArray()
    {
    	System.out.println("here");
    	String dimensions= " ";
    	
    	double[] data = new double[Norm[0].length];
    	buf=new byte[256];
    	int patients = Norm.length;
    	int antigens = Norm[0].length;
    	dimensions = Integer.toString(patients) + ", " + Integer.toString(antigens);
    	buf = dimensions.getBytes();
    	packet = new DatagramPacket(buf, buf.length, address, 6003);
    	try{
	    socket.send(packet);
	    Thread.sleep(250);
    	}
	    catch(Exception e)
		{
			e.printStackTrace();
		}
    	for(int i=0; i<Norm.length; i++)
    	{
    		String dString = new String(" ");
    		for(int j=0;j<data.length; j++)
			{
				data[i]=Norm[i][j];
				dString=dString + Double.toString(data[i])+",";				
			}
    		
    		try{
    			buf=new byte[19000];
    			System.out.println(dString);
    			buf =dString.getBytes();
    		    packet = new DatagramPacket(buf, buf.length, address, 6003);
    		    socket.send(packet);
    		    Thread.sleep(250);
    		    //System.out.println(dString);

    		}
    		catch(Exception e)
    		{
    			e.printStackTrace();
    		}
    	}
    	
    }
    public boolean checkConnection()
    {
    	packet = new DatagramPacket(buf, buf.length, address, 6003);
        try
        {
	        socket.send(packet);
	        
	        // get response
	        packet = new DatagramPacket(buf, buf.length);
	        
	        socket.receive(packet);
	        System.out.println("Connection to Client Successful.");
	        return true;
        }
        catch(Exception e)
        {
        	System.out.println("Connection Error: " +e);
        	return false;
        }
    }
    
    public void closeSocket()
    {
    	socket.close();
    }

}
