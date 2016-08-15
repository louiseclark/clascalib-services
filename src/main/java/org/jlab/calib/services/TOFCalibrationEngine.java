package org.jlab.calib.services;

import java.util.Arrays;
import java.util.List;

import org.jlab.detector.calib.tasks.CalibrationEngine;
import org.jlab.detector.calib.utils.CalibrationConstants;
import org.jlab.groot.graphics.EmbeddedCanvas;
import org.jlab.groot.group.DataGroup;
import org.jlab.io.base.DataEvent;
import org.jlab.io.base.DataEventType;
import org.jlab.utils.groups.IndexedList;

public class TOFCalibrationEngine extends CalibrationEngine {

	public final static int[]		NUM_PADDLES = {23,62,5};
	public final static String[]	LAYER_NAME = {"FTOF1A","FTOF1B","FTOF2"};
	
	IndexedList<Double[]> constants = new IndexedList<Double[]>(3);
	
	CalibrationConstants calib;
	IndexedList<DataGroup> dataGroups = new IndexedList<DataGroup>(3);


	public TOFCalibrationEngine() {
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

	@Override
	public void timerUpdate() {
		analyze();
	}

	public void processEvent(DataEvent event) {
		// overridden in calibration step classes
		
	}

	public void analyze() {
		for (int sector = 1; sector <= 6; sector++) {
			for (int layer = 1; layer <= 3; layer++) {
				int layer_index = layer - 1;
				for (int paddle = 1; paddle <= NUM_PADDLES[layer_index]; paddle++) {
					fit(sector, layer, paddle, 0.0, 0.0);
				}
			}
		}
		save();
		calib.fireTableDataChanged();
	}
	
	public void fit(int sector, int layer, int paddle,
			double minRange, double maxRange){
		// overridden in calibration step class
	}

	public void save() {
		// overridden in calibration step class
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

	public void showPlots(int sector, int layer) {
		// Overridden in calibration step class
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
    	double len = 0.0;
    	
    	if (layer==1 && paddle<=5) {
    		len = (15.85*paddle) + 16.43;
    	}
    	else if (layer==1 && paddle>5) {
    		len = (15.85*paddle) + 11.45;
    	}
    	else if (layer==2) {
    		len = (6.4*paddle) + 10.84;
    	}
    	else if (layer==3) {
    		len = (13.73*paddle) + 357.55;
    	}
    	
    	return len;
    	
    }	
		
}
