package org.jlab.calib.services.ctof;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import javax.swing.JFrame;
import javax.swing.JTabbedPane;

import org.jlab.calib.services.TOFPaddle;
import org.jlab.detector.calib.tasks.CalibrationEngine;
import org.jlab.detector.calib.utils.CalibrationConstants;
import org.jlab.groot.data.H1F;
import org.jlab.groot.graphics.EmbeddedCanvas;
import org.jlab.groot.group.DataGroup;
import org.jlab.groot.math.F1D;
import org.jlab.io.base.DataEvent;
import org.jlab.io.base.DataEventType;
import org.jlab.utils.groups.IndexedList;

public class CTOFCalibrationEngine extends CalibrationEngine {

	public final static int			NUM_PADDLES = 48;
	public final static double UNDEFINED_OVERRIDE = Double.NEGATIVE_INFINITY;
	
	public IndexedList<Double[]> constants = new IndexedList<Double[]>(3);
	
	public CalibrationConstants calib;
	public IndexedList<DataGroup> dataGroups = new IndexedList<DataGroup>(3);

	public String stepName = "Unknown";
	public String fileNamePrefix = "Unknown";
	public String filename = "Unknown.txt";

	public CTOFCalibrationEngine() {
		// controlled by calibration step class
	}
	
	@Override
	public void dataEventAction(DataEvent event) {

		if (event.getType()==DataEventType.EVENT_START) {
			resetEventListener();
			processEvent(event);
		}
		else if (event.getType()==DataEventType.EVENT_ACCUMULATE) {
			processEvent(event);
		}
		else if (event.getType()==DataEventType.EVENT_STOP) {
			analyze();
		} 
	}
	
	public void processPaddleList(List<TOFPaddle> paddleList) {
		// overridden in calibration step classes
	}

	@Override
	public void timerUpdate() {
		analyze();
	}

	public void processEvent(DataEvent event) {
		// overridden in calibration step classes
		
	}

	public void analyze() {
		for (int paddle = 1; paddle <= NUM_PADDLES; paddle++) {
			fit(1, 1, paddle);
		}
		save();
		calib.fireTableDataChanged();
	}
	
	public void fit(int sector, int layer, int paddle) {
		// fit to default range
		fit(sector, layer, paddle, UNDEFINED_OVERRIDE, UNDEFINED_OVERRIDE);
	}
	
	public void fit(int sector, int layer, int paddle,
			double minRange, double maxRange){
		// overridden in calibration step class
	}

	public void saveRow(int sector, int layer, int paddle) {
		// overridden in calibration step class
	}

	public void save() {

		for (int paddle = 1; paddle <= NUM_PADDLES; paddle++) {
			calib.addEntry(1, 1, paddle);
			saveRow(1,1,paddle);
		}
		calib.save(filename);
	}
	
	public String nextFileName() {

		// Get the next file name
		Date today = new Date();
		DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
		String todayString = dateFormat.format(today);
		String filePrefix = fileNamePrefix + todayString;
		int newFileNum = 0;

		File dir = new File(".");
		File[] filesList = dir.listFiles();

		for (File file : filesList) {
			if (file.isFile()) {
				String fileName = file.getName();
				if (fileName.matches(filePrefix + "[.]\\d+[.]txt")) {
					String fileNumString = fileName.substring(
							fileName.indexOf('.') + 1,
							fileName.lastIndexOf('.'));
					int fileNum = Integer.parseInt(fileNumString);
					if (fileNum >= newFileNum)
						newFileNum = fileNum + 1;

				}
			}
		}

		return filePrefix + "." + newFileNum + ".txt";
	}	
	
	public void customFit(int sector, int layer, int paddle){
		// overridden in calibration step class
	}
	
	@Override
	public List<CalibrationConstants> getCalibrationConstants() {
		return Arrays.asList(calib);
	}

	@Override
	public IndexedList<DataGroup>  getDataGroup(){
		return dataGroups;
	}

	public boolean isGoodPaddle(int sector, int layer, int paddle) {
		// Overridden in calibration step class
		return true;
	}
	
	public DataGroup getSummary(int sector, int layer) {
		// Overridden in calibration step class
		DataGroup dg = null;
		return dg;
	}
	
    public double paddleLength(int sector, int layer, int paddle) {
    	return 100.0;
    	
    }
    
	public static double toDouble(String stringVal) {

		double doubleVal;
		try {
			doubleVal = Double.parseDouble(stringVal);
		} catch (NumberFormatException e) {
			doubleVal = UNDEFINED_OVERRIDE;
		}
		return doubleVal;
	}
	
	public void drawPlots(int sector, int layer, int paddle, EmbeddedCanvas canvas) {
		// Overridden in calibration step classes
	}	

	public void showPlots(int sector, int layer) {
		
		EmbeddedCanvas[] fitCanvases;
		fitCanvases = new EmbeddedCanvas[3];
		
		int canvasNum = 0;
		int padNum = 0;
		
		for (int paddleNum=1; paddleNum <= NUM_PADDLES; paddleNum++) {

    		if ((paddleNum-1)%24 == 0) {
    			// new canvas
    			padNum = 0;
    			
    			fitCanvases[canvasNum] = new EmbeddedCanvas();
    			fitCanvases[canvasNum].divide(6, 4);
    			
    		}

    		System.out.println("paddleNum "+paddleNum);
    		System.out.println("canvasNum "+canvasNum);
    		System.out.println("padNum "+padNum);
			fitCanvases[canvasNum].cd(padNum);
			fitCanvases[canvasNum].getPad(padNum).setOptStat(0);
			drawPlots(sector, layer, paddleNum, fitCanvases[canvasNum]);
			
    		padNum = padNum+1;
    		
    		if ((paddleNum)%24 == 0) {
    			// new canvas
    			canvasNum++;
    		}
    		
		}
		
    	JFrame frame = new JFrame(stepName);
        frame.setSize(1000, 800);
        
        JTabbedPane pane = new JTabbedPane();
        for (int i=0; i<canvasNum; i++) {
        	pane.add("Paddles "+((i*24)+1)+" to "+Math.min(((i+1)*24),NUM_PADDLES), fitCanvases[i]);
        }
 		
        frame.add(pane);
        //frame.pack();
        frame.setVisible(true);
        frame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
				
	}	
	
}