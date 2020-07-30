import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Vector;


public class Antigens {
	private double error;
	private int position;
	private Vector<Antigens> Antigen;
	private String ID;
	private String outfile = "statistics.txt";
	private int iteration = 1;
	
	public Antigens(int size){ 
		Antigen = new Vector<Antigens>(size);
	}
	
	public Antigens(double e, int p,String Id){
		error =e;
		position=p;
		ID=Id;
	}
	
	public void add(Antigens anti){
		Antigen.add(anti);
	}
	
	public double getError(){
		return error;
	}
	
	public int getPosition(){
		return position;
	}
	
	public String getId(){
		return ID;
	}
	
	public void print(){
		System.out.println("---------------------------Start of Antigen List---------------------------\n");
		for(int i=0; i<Antigen.size(); i++)
		{
			System.out.println("Index: "+(Antigen.size()-i)+" Antigen: "+Antigen.get(i).getId()+" Error: "+Antigen.get(i).getError());
		}
		
		printToFile();
	}
	
	private void printToFile(){
		File file = new File(outfile);
		BufferedWriter writer;
		if(!file.exists()){
			try {
				file.createNewFile();
				writer = new BufferedWriter(new FileWriter(file));
				writer.append("<" + iteration + ">\n");
				for(int x = 0; x < Antigen.size(); x++){
					writer.append("<id>\n");
					writer.append(Antigen.get(x).getId());
					writer.append("<score>\n");
					writer.append(Antigen.get(x).getError() + "");
					writer.append("<position>\n");
					writer.append(Antigen.get(x).getPosition() + "");
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		iteration++;
	}
}
