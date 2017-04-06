package org.jlab.calib.services;

import java.awt.Container;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;

public class TofPrevConfigPanel extends JPanel
implements ActionListener {

	TOFCalibrationEngine engine;
	JFileChooser fc = new JFileChooser();
	JTextField fileDisp = new JTextField(20); 
	JTextField runText = new JTextField(5);
	
	public TofPrevConfigPanel(TOFCalibrationEngine engineIn) {

		engine = engineIn;

		this.setLayout(new GridBagLayout());
		GridBagConstraints c = new GridBagConstraints();

		JRadioButton defaultRad = new JRadioButton("DEFAULT");
		JRadioButton fileRad = new JRadioButton("FILE");
		JRadioButton dbRad = new JRadioButton("DB");
		defaultRad.setSelected(true);
		ButtonGroup lrRadGroup = new ButtonGroup();
		lrRadGroup.add(defaultRad);
		lrRadGroup.add(fileRad);
		lrRadGroup.add(dbRad);
		defaultRad.addActionListener(this);
		fileRad.addActionListener(this);
		dbRad.addActionListener(this);

		JPanel drPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		drPanel.add(defaultRad);
		c.anchor = c.LINE_START;
		c.gridx = 0;
		c.gridy = 0;
		this.add(drPanel,c);
		c.gridx = 1;
		c.gridy = 0;
		this.add(new JPanel(),c);
		JPanel frPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		frPanel.add(fileRad);
		c.gridx = 0;
		c.gridy = 1;
		this.add(frPanel,c);
		JPanel filePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		//fileDisp.setEnabled(false);
		fileDisp.setEditable(false);
		fileDisp.setText("None selected");
		filePanel.add(new JLabel("Selected file: "));
		filePanel.add(fileDisp);
		JButton fileButton = new JButton("Select File");
		fileButton.addActionListener(this);
		filePanel.add(fileButton,c);
		c.gridx = 1;
		c.gridy = 1;
		this.add(filePanel,c);

		JPanel dbrPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		dbrPanel.add(dbRad);
		c.gridx = 0;
		c.gridy = 2;
		this.add(dbrPanel,c);
		JPanel runPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		JLabel runLabel = new JLabel("Run number:");
		runPanel.add(runLabel);
		runPanel.add(runText);
		c.gridx = 1;
		c.gridy = 2;
		this.add(runPanel,c);

		JPanel okPanel = new JPanel();
		JButton okButton = new JButton("OK");
		okPanel.add(okButton);
		c.anchor = c.LINE_END;
		c.gridx = 1;
		c.gridy = 3;
		this.add(okPanel,c);

		this.setBorder(BorderFactory.createTitledBorder(engine.stepName));

	}

	public void actionPerformed(ActionEvent e) {

		System.out.println("prevConfig.actionPerformed");
		if (e.getActionCommand() == "Select File") {
			System.out.println("Select File clicked");
			int returnValue = fc.showOpenDialog(null);
			if (returnValue == JFileChooser.APPROVE_OPTION) {
				engine.prevCalFilename = fc.getSelectedFile().getAbsolutePath();
				fileDisp.setText("Selected file: "+ fc.getSelectedFile().getAbsolutePath()); 
			}
		}
		
		if (e.getActionCommand() == "OK") {
			engine.prevCalRunNo = Integer.parseInt(runText.getText());
		}

		if (e.getActionCommand() == "DB") {
			engine.calDBSource = engine.CAL_DB;
		}
		else if (e.getActionCommand() == "FILE") {
			engine.calDBSource = engine.CAL_FILE;
		}
		else {
			engine.calDBSource = engine.CAL_DEFAULT;
		}
	}

}
