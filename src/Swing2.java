import javax.swing.*;
import javax.swing.JFrame.*;

import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.*;
import java.io.*;
import java.util.*;


public class Swing2 extends JFrame implements ActionListener{
	JLabel text;
	JTextField computers;
	JButton submit;
	
	int clusterSize;
	Container content;
	
	double[][][] normalized_data;
	String[] idList;
	
	Dimension screenSize =Toolkit.getDefaultToolkit().getScreenSize();
	
	
	Swing2(double[][][] normalized, String[] list)
	{
		normalized_data = normalized;
		idList = list;
		
		content = getContentPane();
		
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		content.setBackground(Color.white);
	    
	    content.setLayout(null);
	    content.setBounds(screenSize.width/2, screenSize.height/2, 415, 200);
	    setResizable(false);
	    
	    //Display instructions for page
	    text = new JLabel("Enter the number of computers to be used in your cluster: ");
	    text.setBounds(25, 0, 415, 50);
	    content.add(text);
	    
	    //Create text field to enter the number of computers to work with
	    computers = new JTextField("1");
	    computers.setEditable(true);
	    computers.addActionListener(this);
	    computers.setBounds(25, 60, 50, 30);
	    content.add(computers);
	    
	    //Create Button to Submit number of clustered computers
	    submit = new JButton("Submit");
	    submit.setBounds(25, 120, 105, 30);
	    submit.addActionListener(this);
	    content.add(submit);
	    
	    this.setBounds(screenSize.width/2, screenSize.height/2, 415, 200);
		setDefaultCloseOperation(EXIT_ON_CLOSE);
	    setVisible(true);
	}
	
	public void actionPerformed(ActionEvent e)
	{
		if(e.getSource() == computers)
		{
			if(computers == null || Integer.parseInt(computers.getText())<= 0)
			{
				JOptionPane.showMessageDialog(null, "Warning, you have chosen to run the Backwards Elimination algorithm on only your local machine.  This process may take up to a month...");
				BioMarkerSelection_Local BMS = new BioMarkerSelection_Local();
				BMS.setID(idList);
				BMS.backwardsElimination(normalized_data);
			}
			else
			{
				clusterSize=Integer.parseInt(computers.getText());
				setVisible(false);
				Swing3 swing3 = new Swing3(clusterSize, normalized_data, idList);
			}
		}
		else if(e.getSource() == submit)
		{
			if(computers == null || Integer.parseInt(computers.getText())<= 1)
			{
				JOptionPane.showMessageDialog(null, "Warning, you have chosen to run the Backwards Elimination algorithm on only your local machine.  This process may take up to a month...");
				BioMarkerSelection_Local BMS = new BioMarkerSelection_Local();
				BMS.setID(idList);
				BMS.backwardsElimination(normalized_data);			
			}
			else
			{
				clusterSize=Integer.parseInt(computers.getText());
				setVisible(false);
				//testing the new page
				Swing3 swing3 = new Swing3(clusterSize, normalized_data, idList);
				//Swing4 swing4 = new Swing4();
			}
		}
		else
		{
			JOptionPane.showMessageDialog(null, "Action not interpreted correctly....");
		}
	}
}