import java.awt.Color;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;
import java.io.*;
import java.awt.*;
import javax.swing.*;
import javax.imageio.*;
import javax.swing.JFrame;


public class Swing extends JFrame implements ActionListener
{	
	boolean missingVal=false;
	JButton openFile1, openFile2, openText1, openText2, missingValue, Submit, BackwardsElimination;
	JTextField patients, biomarkers, csv1_display, csv2_display, txt1_display, txt2_display;
	JFileChooser csv1, csv2,  txt1, txt2;
	JLabel Input1, Input2, Text1, Text2, pat, bio,  Data, missing;
	Container content = getContentPane();
	//JFrame content = new JFrame();
	String fileNamecy3, fileNamecy5,fileTextcy3,fileTextcy5;
	DataHandler cy3;// = new DataHandler(); 
	DataHandler cy5; //= new DataHandler();
	Normalization function = new Normalization();
	double[][][] normalized_data;
	String[] idList;
	Dimension screenSize =Toolkit.getDefaultToolkit().getScreenSize();
	
	private Image logo;
	
	Swing()
	{
		missingVal=false;
		//load settings saved from previous run of the program
		loadSettings();
		//loadImage();
		
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		content.setBackground(Color.white);
	    
	    content.setLayout(null);
	    content.setBounds(screenSize.width/2, screenSize.height/2, 765, 700);
	    setResizable(false);
	    
		Input1 = new JLabel("Choose a CSV file for your cy3 set of data: ");
		Input1.setBounds(25, 100, 340, 70);
		content.add(Input1);
		
		csv1_display.setBounds(370, 120, 215, 30);
		content.add(csv1_display);
		csv1 = new JFileChooser();
		openFile1 = new JButton("Browse" );
		openFile1.setBounds(625, 120, 105, 30);	
		content.add(openFile1);
		openFile1.addActionListener(this);
		

		Input2 = new JLabel("Choose a CSV file for your cy5 set of data: ");
		Input2.setBounds(25, 170, 340, 70);
		content.add(Input2);		
		
		csv2_display.setBounds(370, 190, 215, 30);
		content.add(csv2_display);
		csv2 = new JFileChooser();
		openFile2 = new JButton("Browse" );
		openFile2.setBounds(625, 190, 105, 30);	
		content.add(openFile2);
		openFile2.addActionListener(this);
		
		pat = new JLabel("Enter the number of patients: ");
		pat.setBounds(25, 250, 240, 70);
		content.add(pat);	
		
		//patients = new JTextField();
		patients.setBounds(370, 270, 105, 30);
		content.add(patients);
		
		bio = new JLabel("Enter the number of biomarkers: ");
		bio.setBounds(25, 320, 240, 70);
		content.add(bio);	
		
		//biomarkers = new JTextField();
		biomarkers.setBounds(370, 340, 105, 30);
		content.add(biomarkers);
		
		Text1 = new JLabel("Choose a txt file for your cy3 set of Data: \n");
		Text1.setBounds(25, 390, 340, 70);
		content.add(Text1);
		
		txt1_display.setBounds(370, 410, 215, 30);
		content.add(txt1_display);
		txt1 = new JFileChooser();
		openText1 = new JButton("Browse" );
		openText1.setBounds(625, 410, 105, 30);
		content.add(openText1);
		openText1.addActionListener(this);
		

		Text2 = new JLabel("Choose a txt file for your cy5 set of Data: ");
		Text2.setBounds(25, 460, 340, 70);
		content.add(Text2);		
		
		txt2_display.setBounds(370, 480, 215, 30);
		content.add(txt2_display);
		txt2 = new JFileChooser();
		openText2 = new JButton("Browse" );
		openText2.setBounds(625, 480, 105, 30);	
		content.add(openText2);
		openText2.addActionListener(this);
		
		missing = new JLabel("Would you like to replace missing values (BPCA)");
		missing.setBounds(25, 530, 295, 70);
		content.add(missing);
		missingValue = new JButton("Replace");
		missingValue.setBounds(318, 550, 105, 30);
		missingValue.addActionListener(this);
		content.add(missingValue);
		
		Submit = new JButton("Normalize" );
		Submit.setBounds(205, 615, 105, 30);
		content.add(Submit);
		Submit.addActionListener(this);
		
		BackwardsElimination = new JButton("Eliminate");
		BackwardsElimination.setBounds(430, 615, 105, 30);
		BackwardsElimination.addActionListener(this);
		content.add(BackwardsElimination);
		
		this.setBounds(screenSize.width/8, 20, 765, 700);
		setVisible(true);
		
	}
	public void actionPerformed(ActionEvent e) 
	{
		if (e.getSource() == openFile1) 
		{
			int returnVal = csv1.showOpenDialog(null);
			if (returnVal == JFileChooser.APPROVE_OPTION) 
			{
				File file = csv1.getSelectedFile();
				fileNamecy3 = file.getAbsolutePath();		
				csv1_display.setText(fileNamecy3);
			}
		}    
		else if(e.getSource() == openFile2)
		{
			int returnVal = csv2.showOpenDialog(null);
			if (returnVal == JFileChooser.APPROVE_OPTION) 
			{
				File file = csv2.getSelectedFile();
				fileNamecy5 = file.getAbsolutePath();
				csv2_display.setText(fileNamecy5);
								
			}
		}
		else if(e.getSource() == openText1)
		{
			int returnVal = txt1.showOpenDialog(null);
			if (returnVal == JFileChooser.APPROVE_OPTION) 
			{
				File file = txt1.getSelectedFile();
				fileTextcy3 = file.getAbsolutePath();
				txt1_display.setText(fileTextcy3);
								
			}
		}
		else if(e.getSource() == openText2)
		{
			int returnVal = txt2.showOpenDialog(null);
			if (returnVal == JFileChooser.APPROVE_OPTION) 
			{
				File file = txt2.getSelectedFile();
				fileTextcy5 = file.getAbsolutePath();
				txt2_display.setText(fileTextcy5);
							
			}
		}
		else if(e.getSource()==missingValue)
		{
			if(!missingVal)
			{
				if(fileNamecy3==null || fileTextcy3 ==null || fileNamecy5==null || fileTextcy5==null || patients==null || biomarkers ==null)
				{
					JOptionPane.showMessageDialog(null, "Error Please choose files for both data sets and both dimensions! \n"+
													"filenamecy3: "+fileNamecy3+", fileNamecy5: "+fileNamecy5+", fileTextcy3: "+fileTextcy3+
													", fileTextcy5: "+fileTextcy5+", biomarkers: "+biomarkers+", patients: "+patients);
				}
				else
				{
					int p=0, b=0;
					p=Integer.parseInt(patients.getText());
					System.out.println("patients: " + p);
					b=Integer.parseInt(biomarkers.getText());
					cy3 = new DataHandler(p, b);
					cy5 = new DataHandler(p, b);
					cy3.readCSVSheet(fileNamecy3);
					cy5.readCSVSheet(fileNamecy5);
					cy3.readTXTData(fileTextcy3);
					cy5.readTXTData(fileTextcy5);
					
					Save(p,b,fileNamecy3,fileNamecy5,fileTextcy3,fileTextcy5);
					
					MissingData MD = new MissingData(cy3, cy5);
					RunCommand cmd = new RunCommand();
					
					missingVal=true;
				}
			}
		}
		else if(e.getSource()==Submit)
		{
			Submit();
		}
		else if(e.getSource()==BackwardsElimination)
		{
			Backwards();
		}
	}
	public void Submit()
	{

		//double[][] temp= new double[396][50];// = function.replaceEmpty(cy3, cy5);
		if(fileNamecy3==null || fileTextcy3 ==null || fileNamecy5==null || fileTextcy5==null || patients==null || biomarkers ==null)
		{
			JOptionPane.showMessageDialog(null, "Error Please choose files for both data sets and both dimensions! \n"+
											"filenamecy3: "+fileNamecy3+", fileNamecy5: "+fileNamecy5+", fileTextcy3: "+fileTextcy3+
											", fileTextcy5: "+fileTextcy5+", biomarkers: "+biomarkers+", patients: "+patients);
		}
		else
		{
			if(!missingVal)
			{
				int p=0, b=0;
				p=Integer.parseInt(patients.getText());
				p=p*2;
				b=Integer.parseInt(biomarkers.getText());
				cy3 = new DataHandler(p, b);
				cy5 = new DataHandler(p, b);
				cy3.readCSVSheet(fileNamecy3);
				cy5.readCSVSheet(fileNamecy5);
				cy3.readTXTData(fileTextcy3);
				cy5.readTXTData(fileTextcy5);
			
				Save(p,b,fileNamecy3,fileNamecy5,fileTextcy3,fileTextcy5);
						
				normalized_data = function.predict(cy3, cy5, missingVal, "Marker; Cy3/Cy5 pre-labeled Albumin", "bkg control; Albumin, unlabeled");
				idList = function.getIDList();		
			}
			else
			{
				normalized_data = function.predict(cy3, cy5, missingVal, "Marker; Cy3/Cy5 pre-labeled Albumin", "bkg control; Albumin, unlabeled");
				idList = function.getIDList();
			}
		}
	}
	public void Backwards()
	{
		setVisible(false);
		Swing2 clusterSize = new Swing2(normalized_data, idList);
	}
	public void loadImage()
	{
		try
		{
			logo = ImageIO.read(new File("BioCode.jpg"));
		}
		catch (Exception e)
		{
			System.out.println("Error loading image: "+e);
		}
	}
	public void paint(Graphics g)
	{
		super.paint(g);
		g.drawImage(logo, 20, 40, this);
	}
	public void loadSettings()
	{
		try
		{
			//load settings from txt file settings.txt
			//store value and load value into text field display
			
			//Note: change reads to nextLine() to read in spaces
			File file = new File("settings.txt");
			boolean exists = file.exists();
			
			if(exists){
				Scanner settings = new Scanner(new FileReader(file));
				patients = new JTextField(settings.nextLine());
				biomarkers = new JTextField(settings.nextLine());
			
				fileNamecy3 = settings.nextLine();
				csv1_display = new JTextField(fileNamecy3);
				fileNamecy5 = settings.nextLine();
				csv2_display = new JTextField(fileNamecy5);
				fileTextcy3 = settings.nextLine();
				txt1_display = new JTextField(fileTextcy3);
				fileTextcy5 = settings.nextLine();
				txt2_display = new JTextField(fileTextcy5);
			
				//close file
				settings.close();
			}else{
				patients = new JTextField();
				biomarkers = new JTextField();
			
				csv1_display = new JTextField();
				csv2_display = new JTextField();
				txt1_display = new JTextField();
				txt2_display = new JTextField();
			}
			
			patients.setEditable(true);
			biomarkers.setEditable(true);
			csv1_display.setEditable(false);
			csv2_display.setEditable(false);
			txt1_display.setEditable(false);
			txt2_display.setEditable(false);
		}
		catch (Exception e)
		{
			//catch and print all exceptions
			System.out.println("Error loading settings file: "+e);
		}
	}
	public void Save(int pat, int bio, String f1, String f2, String f3, String f4)
	{
		FileOutputStream out; // declare a file output object 
		PrintStream p; // declare a print stream object 
		try { // Create a new file output stream  
			out = new FileOutputStream("settings.txt"); // Connect print stream to the output stream 
			p = new PrintStream( out );
			pat=pat/2;
			p.println(pat);
			p.println(bio);
			p.println(f1);
			p.println(f2);
			p.println(f3);
			p.println(f4);
			
			p.close(); 
		}
		//catch all exceptions
		catch (Exception e) 
		{ 
			System.err.println ("Error saving session values");
		}
	}
	
}
