package org.jlab.calib.services;


import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeMap;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JSplitPane;

import org.jlab.detector.base.DetectorType;
import org.jlab.detector.calib.tasks.CalibrationEngine;
import org.jlab.detector.calib.utils.CalibrationConstants;
import org.jlab.detector.decode.CodaEventDecoder;
import org.jlab.detector.decode.DetectorDataDgtz;
import org.jlab.detector.decode.DetectorDecoderView;
import org.jlab.detector.decode.DetectorEventDecoder;
import org.jlab.detector.examples.RawEventViewer;
import org.jlab.detector.view.DetectorPane2D;
import org.jlab.detector.view.DetectorShape2D;
import org.jlab.groot.data.GraphErrors;
import org.jlab.groot.data.H1F;
import org.jlab.groot.data.H2F;
import org.jlab.groot.fitter.DataFitter;
import org.jlab.groot.group.DataGroup;
import org.jlab.groot.math.F1D;
import org.jlab.io.base.DataEvent;
import org.jlab.io.base.DataEventType;
import org.jlab.io.evio.EvioDataEvent;
import org.jlab.io.task.DataSourceProcessorPane;
import org.jlab.io.task.IDataEventListener;
import org.jlab.utils.groups.IndexedList;

public class TofTimeWalkEventListener extends CalibrationEngine { // IDataEventListener {

	public final static int[]		NUM_PADDLES = {23,62,5};
	public final static String[]	LAYER_NAME = {"FTOF1A","FTOF1B","FTOF2"};
	public final int NUM_ITERATIONS = 5;

	// constants for indexing the histograms
	public final int LEFT = 0;
	public final int RIGHT = 1;

	private final double[]		ADC_MAX = {0.0,4000.0,8000.0,3000.0};

	double[] lambda = {0.0,0.0};
	double[] order = {2.0,2.0};
	
	IndexedList<CalibrationConstants> calibList;
	//CalibrationConstants calib;
	IndexedList<DataGroup> dataGroups;
	
	private int iter = 1; // will be passed as parameter

	public TofTimeWalkEventListener() {

		for (int i=1; i<=NUM_ITERATIONS; i++) {
			CalibrationConstants c = 
				new CalibrationConstants(3,
				"tw0_left/F:tw1_left/F:tw2_left/F:tw0_right/F:tw1_right/F:tw2_right/F");
			
			c.setName("/calibration/ftof/time_walk");
			calibList.add(c, i);
		}
		
//		calib = new CalibrationConstants(3,
//				"tw0_left/F:tw1_left/F:tw2_left/F:tw0_right/F:tw1_right/F:tw2_right/F");
//		calib.setName("/calibration/ftof/time_walk");
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

	public void timerUpdate() {
		analyze();
	}

	public void resetEventListener() {

		System.out.println("resetEventListener");
		// LC perform init processing
		for (int sector = 1; sector <= 6; sector++) {
			for (int layer = 1; layer <= 3; layer++) {
				int layer_index = layer - 1;
				for (int paddle = 1; paddle <= NUM_PADDLES[layer_index]; paddle++) {

					// create all the histograms
					H2F leftHist = new H2F("TW_left",
							"Time residual vs ADC LEFT Sector "+sector+
							" Paddle "+paddle,
							100, 0.0, ADC_MAX[layer],
							100, -10.0, 10.0);
					H2F rightHist = new H2F("TW_right",
							"Time residual vs ADC RIGHT Sector "+sector+
							" Paddle "+paddle,
							100, 0.0, ADC_MAX[layer],
							100, -10.0, 10.0);

					leftHist.setTitle("Time residual vs ADC LEFT : " + LAYER_NAME[layer_index] 
							+ " Sector "+sector+" Paddle "+paddle);
					leftHist.setXTitle("ADC LEFT");
					leftHist.setYTitle("Time residual");
					rightHist.setTitle("Time residual vs ADC RIGHT : " + LAYER_NAME[layer_index] 
							+ " Sector "+sector+" Paddle "+paddle);
					rightHist.setXTitle("ADC RIGHT");
					rightHist.setYTitle("Time residual");
					
					DataGroup dg = new DataGroup(2,1);
					dg.addDataSet(leftHist, LEFT);
					dg.addDataSet(rightHist, RIGHT);
					dataGroups.add(dg, sector,layer,paddle);					

				}
			}
		}
	}

	public void processEvent(DataEvent event) {

		List<TOFPaddle> paddleList = DataProvider.getPaddleList(event);
		for (TOFPaddle paddle : paddleList) {

			int sector = paddle.getDescriptor().getSector();
			int layer = paddle.getDescriptor().getLayer();
			int component = paddle.getDescriptor().getComponent();

				// fill timeResidual vs ADC
			double [] tr = paddle.timeResiduals(lambda, order);

			if (paddle.includeInTimeWalk()) {

				dataGroups.getItem(sector,layer,component).getH2F("TW_left").fill(paddle.ADCL, tr[LEFT]);
				dataGroups.getItem(sector,layer,component).getH2F("TW_right").fill(paddle.ADCR, tr[RIGHT]);
			}

		}
	}

	public void analyze() {
		System.out.println("analyze");
		for (int sector = 1; sector <= 6; sector++) {
			for (int layer = 1; layer <= 3; layer++) {
				int layer_index = layer - 1;
				for (int paddle = 1; paddle <= NUM_PADDLES[layer_index]; paddle++) {
					fit(sector, layer, paddle);
				}
			}
		}
		save();
	}

	public void fit(int sector, int layer, int paddle) {

		H2F twL = dataGroups.getItem(sector,layer,paddle).getH2F("TW_left");

		ArrayList<H1F> twLSlices = twL.getSlicesX();
		int numBins = twL.getXAxis().getNBins();

		double[] binSlices = new double[numBins];
		double[] means = new double[numBins];

		for (int i=0; i<numBins; i++) {

			H1F h = twLSlices.get(i);
			F1D f = new F1D("gaus","[amp]*gaus([mean],[sigma])",-2.0,2.0);
			f.setParameter(0, 250.0);
			f.setParameter(1, 0.0);
			f.setParameter(2, 2.0);
			DataFitter.fit(f, h, "REQ");

			binSlices[i] = twL.getXAxis().getBinCenter(i);
			means[i] = f.getParameter(1);

		}

		GraphErrors meanGraph = new GraphErrors(); //("Mean Graph", binSlices, means);
		
		// fit function to the graph of means
		F1D trFunc = new F1D("trFunc","[0]+([1]/x^[2]))",0.0, ADC_MAX[layer]);
		if (iter==1) {
			trFunc.setParameter(0, 0.0);
			trFunc.setParameter(1, 5.0);
			trFunc.setParameter(0, 0.5);
		}
		if (iter>=2){
			CalibrationConstants c = calibList.getItem(iter-1);
			trFunc.setParameter(0, 0.0);
			trFunc.setParameter(1, c.getDoubleValue("tw0_left", sector,layer,paddle));
			trFunc.setParameter(0, c.getDoubleValue("tw1_left", sector,layer,paddle));
		}
		DataFitter.fit(trFunc, meanGraph, "RNQ");
//		System.out.println("New lambda left is "+trFunc.getParameter(1));
	}

	public Double getLambdaLeft(int sector, int layer, int paddle) {

		return 0.0;
	}	

	public Double getOrderLeft(int sector, int layer, int paddle) {

		return 0.0;
	}	
		
	public Double getLambdaRight(int sector, int layer, int paddle) {

		return 0.0;
	}	

	public Double getOrderRight(int sector, int layer, int paddle) {

		return 0.0;
	}	

	public void save() {

		CalibrationConstants calib = calibList.getItem(iter);
		
		for (int sector = 1; sector <= 6; sector++) {
			for (int layer = 1; layer <= 3; layer++) {
				int layer_index = layer - 1;
				for (int paddle = 1; paddle <= NUM_PADDLES[layer_index]; paddle++) {
					calib.addEntry(sector, layer, paddle);
					calib.setDoubleValue(getLambdaLeft(sector,layer,paddle),
							"tw0_left", sector, layer, paddle);
					calib.setDoubleValue(getOrderLeft(sector,layer,paddle),
							"tw1_left", sector, layer, paddle);
					calib.setDoubleValue(0.0,
							"tw2_left", sector, layer, paddle);
					calib.setDoubleValue(getLambdaRight(sector,layer,paddle),
							"tw0_right", sector, layer, paddle);
					calib.setDoubleValue(getOrderRight(sector,layer,paddle),
							"tw1_right", sector, layer, paddle);
					calib.setDoubleValue(0.0,
							"tw2_right", sector, layer, paddle);
				}
			}
		}
		calib.save("FTOF_CALIB_TIMEWALK.txt");
	}

	@Override
	public List<CalibrationConstants> getCalibrationConstants() {
		
		CalibrationConstants calib = calibList.getItem(iter);

		return Arrays.asList(calib);
	}

	@Override
	public IndexedList<DataGroup>  getDataGroup(){
		return dataGroups;
	}

	public static void main(String[] args){
		JFrame frame = new JFrame();
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		RawEventViewer viewer = new RawEventViewer();
		frame.add(viewer.getPanel());
		frame.setSize(900, 600);
		frame.setVisible(true);
	}
}
