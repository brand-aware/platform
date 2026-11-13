import javax.swing.*;
import javax.swing.JFrame.*;

import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.*;

public class Swing3 extends JFrame implements ActionListener{
	
	JScrollPane scroll;
	Container content = getContentPane();
	JLabel[] computer; 
	JTextField[] ips;
	JButton submit;
	int size;
	double[][][] normalized_data;
	String[] idList;
	JScrollPane jsp;
	
	Dimension screenSize =Toolkit.getDefaultToolkit().getScreenSize();
	
	public Swing3(int s, double[][][] normalize, String[] list)
	{
		super("KF");
		setVisible(true);

		normalized_data = normalize;
		idList = list;
		
		size=s;
		if(size*100 < 700)
		{
			content.setBounds(screenSize.width/2, 0, 445, ((size*100)+100));
			this.setBounds(screenSize.width/2, 0, 445, ((size*100)+100));
			initialize();
		}
		else
		{
			initialize();
			content.setBounds(screenSize.width/2, 0, 445, 800);
			this.setBounds(screenSize.width/2, 0, 445, 800);
			//form.setAutoscrolls(true);
			jsp = new JScrollPane(this);
			jsp.setPreferredSize(new Dimension(this.getWidth()+10,this.getHeight()+10));
			jsp.setBounds(screenSize.width/2, 0, this.WIDTH, this.HEIGHT);			
			jsp.setVisible(true);
		}
		//content.add(jsp);
		setDefaultCloseOperation(EXIT_ON_CLOSE);
	}
	public void initialize()
	{
		ips = new JTextField[size];
		computer = new JLabel[size];
		content.setLayout(null);
		/*
		if(size > 7)
			form.setBounds(this.getWidth()/2, 0, 415, 800);
		else
			form.setBounds(this.getWidth()/2, 0, 415, ((size*100)+100));
		*/
		for(int x=0; x<size; x++)
		{
			//System.out.println("initializing: "+x);
			computer[x] = new JLabel("Computer "+(x+1)+"'s ip address: ");
			computer[x].setBounds(25, x*100, 270, 100);
			content.add(computer[x]);
			ips[x] = new JTextField();
			ips[x].setEditable(true);
			ips[x].setBounds(280, (x*100)+30, 105, 30);
			content.add(ips[x]);
	
		}
			submit = new JButton("Submit");
			submit.addActionListener(this);
			submit.setBounds(25, size*100, 105, 30);
			content.add(submit);
	}
	public void actionPerformed(ActionEvent e)
	{
		if(e.getSource() == submit)
		{
			if(anyEmpty())
			{
				JOptionPane.showMessageDialog(null, "Please enter ip addresses for all computers to be used");
			}
			else
			{
				//Create new cluster controller created with list of ips
				System.out.println("starting algorithm");
				final String[] ip = new String[size];
				for(int x=0; x<size; x++)
					ip[x]=ips[x].getText().trim();
				//pass ip to controller constructor
				final BioMarkerSelection BMS = new BioMarkerSelection();
				BMS.setID(idList);
				submit.setEnabled(false);
                    new SwingWorker<Void, Void>() {
                        protected Void doInBackground() {
                            BMS.backwardsElimination(normalized_data, ip);
                            return null;
                        }
                        protected void done() {
                            submit.setEnabled(true);
                            try {
                                get();
                                JOptionPane.showMessageDialog(Swing3.this, "Distributed elimination complete");
                            } catch (Exception failure) {
                                JOptionPane.showMessageDialog(Swing3.this, describeFailure(failure),
                                        "Distributed elimination failed", JOptionPane.ERROR_MESSAGE);
                            }
                        }
                    }.execute();
			}
		}
	}
    public boolean anyEmpty() {
        for (JTextField field : ips)
            if (field.getText().trim().isEmpty()) return true;
        return false;
    }

    private String describeFailure(Throwable failure) {
        StringBuilder message = new StringBuilder();
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            String detail = cause.getMessage();
            if (detail == null || detail.trim().isEmpty()) detail = cause.getClass().getSimpleName();
            if (message.length() == 0 || !message.toString().endsWith(detail)) {
                if (message.length() > 0) message.append("\nCaused by: ");
                message.append(detail);
            }
        }
        return message.toString();
    }
}
