package org.jlab.calib.services;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.TableModelEvent;

import org.jlab.detector.base.DetectorType;
import org.jlab.detector.calib.tasks.CalibrationEngine;
import org.jlab.detector.calib.tasks.CalibrationEngineView;
import org.jlab.detector.calib.utils.CalibrationConstants;
import org.jlab.detector.calib.utils.CalibrationConstantsListener;
import org.jlab.detector.calib.utils.CalibrationConstantsView;
import org.jlab.detector.view.DetectorListener;
import org.jlab.detector.view.DetectorPane2D;
import org.jlab.detector.view.DetectorShape2D;
import org.jlab.groot.data.H1F;
import org.jlab.groot.graphics.EmbeddedCanvas;
import org.jlab.groot.group.DataGroup;
import org.jlab.groot.math.F1D;
import org.jlab.groot.ui.TCanvas;
import org.jlab.io.base.DataEvent;
import org.jlab.io.base.DataEventType;
import org.jlab.io.task.DataSourceProcessorPane;
import org.jlab.io.task.IDataEventListener;
import org.jlab.utils.groups.IndexedList;

public class TOFCalibration implements IDataEventListener, ActionListener, 
									   CalibrationConstantsListener, DetectorListener,
									   ChangeListener {

	// main panel
	JPanel          pane         = null;
	JFrame configFrame = new JFrame("Configure FTOF calibration settings");
	
	// detector panel
    DetectorPane2D            detectorView        = null;
    
    // event reading panel
    DataSourceProcessorPane processorPane = null;
    public final int UPDATE_RATE = 100000;
    
    // calibration view
	EmbeddedCanvas 	canvas = null;   
    CalibrationConstantsView ccview = null;

    TOFCalibrationEngine[] engines = {
    		new TofHVEventListener(),
    		new TofAttenEventListener(),
    		new TofLeftRightEventListener(),
    		new TofVeffEventListener(),
    		new TofTimeWalkEventListener(),
    		new TofP2PEventListener()};
    
    // engine indices
    public final int HV = 0;
    public final int ATTEN = 1;
    public final int LEFT_RIGHT = 2;
    public final int VEFF = 3;
    public final int TW = 4;
    public final int P2P = 5;
    public final int RF_OFFSET = 6;
    
    String[] dirs = {"/calibration/ftof/gain_balance",
    				 "/calibration/ftof/attenuation",
    				 "/calibration/ftof/timing_offset",
    				 "/calibration/ftof/effective_velocity",
    				 "/calibration/ftof/time_walk",
    				 "/calibration/ftof/timing_offset/P2P",
    				 "/calibration/ftof/RF_offset"};
	
	String selectedDir = dirs[HV];
	int selectedSector = 1;
	int selectedLayer = 1;
	int selectedPaddle = 1;
	
	String[] buttons = {"View all", "Adjust Fit/Override", "Adjust HV", "Write"};
	// button indices
	public final int VIEW_ALL = 0;
	public final int FIT_OVERRIDE = 1;
	public final int ADJUST_HV = 2;
	public final int WRITE = 3;
	
	public static H1F totalStatHist;
	public static H1F trackingZeroStatHist;
	public static H1F trackingStatHist;
	public static H1F adcLeftHist1A;
	public static H1F adcRightHist1A;
	public static H1F trackingAdcLeftHist1A;
	public static H1F trackingAdcRightHist1A;
	public static H1F adcLeftHist1B;
	public static H1F adcRightHist1B;
	public static H1F trackingAdcLeftHist1B;
	public static H1F trackingAdcRightHist1B;
	public static H1F paddleHist;
	public static H1F trackingPaddleHist;
	public static H1F hitsPerBankHist;
	
	//public static EventDecoder decoder = new EventDecoder();
	
	public TOFCalibration() {
		
		DataProvider.init();

        pane = new JPanel();
        pane.setLayout(new BorderLayout());

        JSplitPane   splitPane = new JSplitPane();
        
        // combined panel for detector view and button panel
        JPanel combined = new JPanel();
        combined.setLayout(new BorderLayout());
        
        detectorView = new DetectorPane2D();
        detectorView.getView().addDetectorListener(this);
        
        JPanel butPanel = new JPanel();
        for (int i=0; i < buttons.length; i++) {
            JButton button = new JButton(buttons[i]);
            button.addActionListener(this);
            butPanel.add(button);        	
        }
        combined.add(detectorView, BorderLayout.CENTER);
        combined.add(butPanel,BorderLayout.PAGE_END);
        
        this.updateDetectorView(true);

        splitPane.setLeftComponent(combined);

        // Create the engine views with this GUI as listener
		JPanel engineView = new JPanel();
	    JSplitPane          enginePane = null;
	    engineView.setLayout(new BorderLayout());
        enginePane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);

        canvas = new EmbeddedCanvas();
        ccview = new CalibrationConstantsView();
        ccview.getTabbedPane().addChangeListener(this);
        
        for (int i=0; i < engines.length; i++) {
        	ccview.addConstants(engines[i].getCalibrationConstants().get(0),this);
        }
        
        enginePane.setTopComponent(canvas);
        enginePane.setBottomComponent(ccview);
        enginePane.setDividerLocation(0.6);
        enginePane.setResizeWeight(0.6);
        engineView.add(splitPane,BorderLayout.CENTER);

        splitPane.setRightComponent(enginePane);
        pane.add(splitPane,BorderLayout.CENTER);
        
        processorPane = new DataSourceProcessorPane();
        processorPane.setUpdateRate(UPDATE_RATE);
        
        // only add the gui as listener so that extracting paddle list from event is only done once per event
        this.processorPane.addEventListener(this);
        
//        this.processorPane.addEventListener(engines[0]);
//        this.processorPane.addEventListener(this); // add gui listener second so detector view updates 
//        										   // as soon as 1st analyze is done
//        for (int i=1; i< engines.length; i++) {
//        	this.processorPane.addEventListener(engines[i]);
//        }
        pane.add(processorPane,BorderLayout.PAGE_END);
		
    	JFrame frame = new JFrame("FTOF Calibration");
        frame.setSize(1800, 1000);
 		
        frame.add(pane);
        //frame.pack();
        frame.setVisible(true);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		
	}

	public TOFCalibrationEngine getSelectedEngine() {
		
		TOFCalibrationEngine engine = engines[HV];

		System.out.println("before selectedDir = "+selectedDir);
		System.out.println("dirs[RF_OFFSET] = "+dirs[RF_OFFSET]);
		
		if (selectedDir == dirs[HV]) {
			engine = engines[HV];
		} else if (selectedDir == dirs[ATTEN]) {
			engine = engines[ATTEN];
		} else if (selectedDir == dirs[LEFT_RIGHT]) {
			engine = engines[LEFT_RIGHT];
		} else if (selectedDir == dirs[VEFF]) {
			engine = engines[VEFF];
		} else if (selectedDir == dirs[TW]) {
			engine = engines[TW];
		} else if (selectedDir == dirs[P2P]) {
			engine = engines[P2P];
		} else if (selectedDir == dirs[RF_OFFSET]) {
			engine = engines[RF_OFFSET];
		}

		return engine;
	}
		
	
	public void actionPerformed(ActionEvent e) {

		TOFCalibrationEngine engine = getSelectedEngine();
		
		if (e.getActionCommand().compareTo(buttons[VIEW_ALL])==0) {

			System.out.println("View all clicked");
			engine.showPlots(selectedSector, selectedLayer);

		}
		else if (e.getActionCommand().compareTo(buttons[FIT_OVERRIDE])==0) {
			
			engine.customFit(selectedSector, selectedLayer, selectedPaddle);
			updateDetectorView(false);
			this.updateCanvas();
		}
		else if (e.getActionCommand().compareTo(buttons[ADJUST_HV])==0) {
			
			JFrame hvFrame = new JFrame("Adjust HV");
        	hvFrame.add(new TOFHVAdjustPanel((TofHVEventListener) engines[HV]));
            hvFrame.setSize(1000, 800);
        	//hvFrame.pack();
        	hvFrame.setVisible(true);
        	hvFrame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        	// *** TEST CODE while issue with EVENT_STOP
			engine.analyze();

		}
		else if (e.getActionCommand().compareTo(buttons[WRITE])==0) {
			
			String outputFilename = engine.nextFileName();
			engine.calib.save(outputFilename);
			JOptionPane.showMessageDialog(new JPanel(),
					engine.stepName + " calibration values written to "+outputFilename);
		}
		
		if (e.getActionCommand().compareTo("OK")==0) {
			configFrame.setVisible(false);
		}
	}

    public void dataEventAction(DataEvent event) {

    	//DataProvider dp = new DataProvider();
		List<TOFPaddle> paddleList = DataProvider.getPaddleList(event);
    	
    	for (int i=0; i< engines.length; i++) {
		//for (int i=4; i<5; i++) { //only timewalk
		
    		if (event.getType()==DataEventType.EVENT_START) {
    			engines[i].resetEventListener();
    			engines[i].processPaddleList(paddleList);
    			
    		}
    		else if (event.getType()==DataEventType.EVENT_ACCUMULATE) {
    			engines[i].processPaddleList(paddleList);
    		}
    		else if (event.getType()==DataEventType.EVENT_STOP) {
    			engines[i].analyze();
    		} 

    		if (event.getType()==DataEventType.EVENT_STOP) {
    			this.updateDetectorView(false);
    			this.updateCanvas();
    		} 
    	}
	}
    
	public void resetEventListener() {
		
	}
	
	public void timerUpdate() {

    	for (int i=0; i< engines.length; i++) {
		//for (int i=4; i< 5; i++) {
    		System.out.println("Timer update for "+engines[i].stepName);
    		engines[i].timerUpdate();
    	}
		
		this.updateDetectorView(false);
		this.updateCanvas();
	}

	public final void updateDetectorView(boolean isNew){

		TOFCalibrationEngine engine = getSelectedEngine();

		double FTOFSize = 500.0;
		int[]     npaddles = new int[]{23,62,5};
		int[]     widths   = new int[]{6,15,25};
		int[]     lengths  = new int[]{6,15,25};

		String[]  names    = new String[]{"FTOF 1A","FTOF 1B","FTOF 2"};
		for(int sector = 1; sector <= 6; sector++){
			double rotation = Math.toRadians((sector-1)*(360.0/6)+90.0);

			for(int layer = 1; layer <=3; layer++){

				int width  = widths[layer-1];
				int length = lengths[layer-1];

				for(int paddle = 1; paddle <= npaddles[layer-1]; paddle++){

					DetectorShape2D shape = new DetectorShape2D();
					shape.getDescriptor().setType(DetectorType.FTOF);
					shape.getDescriptor().setSectorLayerComponent(sector, layer, paddle);
					shape.createBarXY(20 + length*paddle, width);
					shape.getShapePath().translateXYZ(0.0, 40 + width*paddle , 0.0);
					shape.getShapePath().rotateZ(rotation);
					if (!isNew) {
						if (engine.isGoodPaddle(sector, layer, paddle)) {
							shape.setColor(101,200,59); //green
						}
						else {
							shape.setColor(225,75,60); //red
						}
					}	
					detectorView.getView().addShape(names[layer-1], shape);
				}
			}
		}

		if (isNew) {
			detectorView.updateBox();
		}
		detectorView.repaint();

	}

    public JPanel  getPanel(){
        return pane;
    }

	public void constantsEvent(CalibrationConstants cc, int col, int row) {

		System.out.println("constantsEvent 403");
		String str_sector    = (String) cc.getValueAt(row, 0);
        String str_layer     = (String) cc.getValueAt(row, 1);
        String str_component = (String) cc.getValueAt(row, 2);
        
        System.out.println("constantsEvent 408");
        if (cc.getName() != selectedDir) {
        	selectedDir = cc.getName();
        	this.updateDetectorView(false);
        	System.out.println("constantsEvent 412");
    	}
        
        System.out.println("constantsEvent 415");
        selectedSector    = Integer.parseInt(str_sector);
        selectedLayer     = Integer.parseInt(str_layer);
        selectedPaddle = Integer.parseInt(str_component);
        
        System.out.println("constantsEvent 420");
        updateCanvas();
        System.out.println("constantsEvent 422");
	}

	public void updateCanvas() {

		System.out.println("updateCanvas 427");
		IndexedList<DataGroup> group = getSelectedEngine().getDataGroup();
		System.out.println("updateCanvas 429");
		getSelectedEngine().setPlotTitle(selectedSector,selectedLayer,selectedPaddle);
		System.out.println("updateCanvas 431");
		
        if(group.hasItem(selectedSector,selectedLayer,selectedPaddle)==true){
        	System.out.println("updateCanvas 434");
            DataGroup dataGroup = group.getItem(selectedSector,selectedLayer,selectedPaddle);
            System.out.println("updateCanvas 435");
            this.canvas.clear();
            this.canvas.draw(dataGroup);
            System.out.println("updateCanvas 439");
            canvas.getPad(0).setTitle(TOFCalibrationEngine.LAYER_NAME[selectedLayer-1]+" Sector "+selectedSector+" Paddle "+selectedPaddle);
            this.canvas.update();
        } else {
            System.out.println(" ERROR: can not find the data group");
        }
   
	}
	
	public void processShape(DetectorShape2D shape) {
		
		// show summary
		selectedSector = shape.getDescriptor().getSector();
		selectedLayer = shape.getDescriptor().getLayer();
		selectedPaddle = 1;
		
		this.canvas.clear();
		this.canvas.draw(getSelectedEngine().getSummary(selectedSector, selectedLayer));
		canvas.getPad(0).setTitle("Calibration values for "
				+TOFCalibrationEngine.LAYER_NAME[selectedLayer-1]+" Sector "+selectedSector);
		this.canvas.update();
		
	}

	public void stateChanged(ChangeEvent e) {
		int i = ccview.getTabbedPane().getSelectedIndex();
		String tabTitle = ccview.getTabbedPane().getTitleAt(i);
		
		System.out.println("stateChanged");
		System.out.println("tabTitle "+tabTitle);
		System.out.println("selectedDir "+selectedDir);
        if (tabTitle != selectedDir) {
        	selectedDir = tabTitle;
        	this.updateDetectorView(false);
        	this.updateCanvas();
        }
	}

	public void configure() {
		
		configFrame.setSize(600, 600);
		configFrame.setLocationRelativeTo(pane);
		Container pane = configFrame.getContentPane();
		JPanel confPanel = new JPanel(new FlowLayout());
		TofPrevConfigPanel[] engPanels = {new TofPrevConfigPanel(new TOFCalibrationEngine()), 
										  new TofPrevConfigPanel(new TOFCalibrationEngine()), 
										  new TofPrevConfigPanel(new TOFCalibrationEngine()),
										  new TofPrevConfigPanel(new TOFCalibrationEngine())};

		for (int i=2; i< engines.length; i++) {  // skip HV and attenuation
			engPanels[i-2] = new TofPrevConfigPanel(engines[i]);
			confPanel.add(engPanels[i-2]);
		}
		
		JPanel okPanel = new JPanel();
		JButton okButton = new JButton("OK");
		for (int i=2; i< engines.length; i++) {  // skip HV and attenuation
			okButton.addActionListener(engPanels[i-2]);
		}
		okButton.addActionListener(this);
		okPanel.add(okButton);
		confPanel.add(okPanel);
		
		pane.add(confPanel);
		
		configFrame.setVisible(true);
		
	}

	public static void main(String[] args) {

        TOFCalibration calibGUI = new TOFCalibration();
        calibGUI.configure();
		
	}

}
