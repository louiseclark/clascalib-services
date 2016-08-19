package org.jlab.calib.services;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
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
import org.jlab.groot.graphics.EmbeddedCanvas;
import org.jlab.groot.group.DataGroup;
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
	
	// detector panel
    DetectorPane2D            detectorView        = null;
    
    // event reading panel
    DataSourceProcessorPane processorPane = null;
    public final int UPDATE_RATE = 50000;
    
    // calibration view
	EmbeddedCanvas 	canvas = null;   
    CalibrationConstantsView ccview = null;

    TOFCalibrationEngine[] engines = {
    		new TofHVEventListener(),
    		new TofAttenEventListener(),
    		new TofLeftRightEventListener(),
    		new TofVeffEventListener()};
    
    // engine indices
    public final int HV = 0;
    public final int ATTEN = 1;
    public final int LEFT_RIGHT = 2;
    public final int VEFF = 3;
    
    String[] dirs = {"/calibration/ftof/gain_balance",
    				 "/calibration/ftof/attenuation",
    				 "/calibration/ftof/timing_offset",
    				 "/calibration/ftof/effective_velocity"};
	
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
	
	public TOFCalibration() {
		
		DataProvider.getGeometry();

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
        enginePane.setDividerLocation(0.3);
        enginePane.setResizeWeight(0.3);
        engineView.add(splitPane,BorderLayout.CENTER);

        splitPane.setRightComponent(enginePane);
        pane.add(splitPane,BorderLayout.CENTER);
        
        processorPane = new DataSourceProcessorPane();
        processorPane.setUpdateRate(UPDATE_RATE);
        this.processorPane.addEventListener(engines[0]);
        this.processorPane.addEventListener(this); // add gui listener second so detector view updates 
        										   // as soon as 1st analyze is done
        for (int i=1; i< engines.length; i++) {
        	this.processorPane.addEventListener(engines[i]);
        }
        pane.add(processorPane,BorderLayout.PAGE_END);
		
    	JFrame frame = new JFrame("FTOF Calibration");
        frame.setSize(1200, 700);
 		
        frame.add(pane);
        frame.pack();
        frame.setVisible(true);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		
	}

	public TOFCalibrationEngine getSelectedEngine() {
		
		TOFCalibrationEngine engine = engines[HV];

		if (selectedDir == dirs[HV]) {
			engine = engines[HV];
		} else if (selectedDir == dirs[ATTEN]) {
			engine = engines[ATTEN];
		} else if (selectedDir == dirs[LEFT_RIGHT]) {
			engine = engines[LEFT_RIGHT];
		} else if (selectedDir == dirs[VEFF]) {
			engine = engines[VEFF];
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
	}

    public void dataEventAction(DataEvent event) {
    	if (event.getType()==DataEventType.EVENT_STOP) {
    		this.updateDetectorView(false);
    		this.updateCanvas();
		} 
	}
    
	public void resetEventListener() {
		
	}
	
	public void timerUpdate() {
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

		String str_sector    = (String) cc.getValueAt(row, 0);
        String str_layer     = (String) cc.getValueAt(row, 1);
        String str_component = (String) cc.getValueAt(row, 2);
        
        if (cc.getName() != selectedDir) {
        	selectedDir = cc.getName();
        	this.updateDetectorView(false);
    	}
        
        selectedSector    = Integer.parseInt(str_sector);
        selectedLayer     = Integer.parseInt(str_layer);
        selectedPaddle = Integer.parseInt(str_component);
        
        updateCanvas();
	}

	public void updateCanvas() {

		IndexedList<DataGroup> group = getSelectedEngine().getDataGroup();
		
        if(group.hasItem(selectedSector,selectedLayer,selectedPaddle)==true){
            DataGroup dataGroup = group.getItem(selectedSector,selectedLayer,selectedPaddle);
            this.canvas.draw(dataGroup);
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
		
		this.canvas.draw(getSelectedEngine().getSummary(selectedSector, selectedLayer));
		this.canvas.update();
		
	}

	public void stateChanged(ChangeEvent e) {
		int i = ccview.getTabbedPane().getSelectedIndex();
		String tabTitle = ccview.getTabbedPane().getTitleAt(i);
        if (tabTitle != selectedDir) {
        	selectedDir = tabTitle;
        	this.updateDetectorView(false);
        	this.updateCanvas();
        }
		
	}

	public static void main(String[] args) {

        TOFCalibration calibGUI = new TOFCalibration();
        
	}

}
