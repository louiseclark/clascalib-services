package org.jlab.calib.services;


import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
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
import org.jlab.groot.graphics.EmbeddedCanvas;
import org.jlab.groot.group.DataGroup;
import org.jlab.groot.math.F1D;
import org.jlab.io.base.DataEvent;
import org.jlab.io.base.DataEventType;
import org.jlab.io.evio.EvioDataEvent;
import org.jlab.io.task.DataSourceProcessorPane;
import org.jlab.io.task.IDataEventListener;
import org.jlab.utils.groups.IndexedList;

public class TofTimeWalkEventListener extends TOFCalibrationEngine {

	public final int NUM_ITERATIONS = 5;

	// constants for indexing the histograms
	public final int LEFT = 0;
	public final int RIGHT = 1;
	
//	// indices for constants
//	public final int LAMBDA_LEFT = 0;
//	public final int ORDER_LEFT = 1;
//	public final int LAMBDA_RIGHT = 2;
//	public final int ORDER_RIGHT = 3;

	private final double[]		ADC_MAX = {0.0,	3000.0,	8000.0,	3000.0};
	private final double[]		FIT_MIN = {0.0,	200.0, 	300.0, 	100.0};
	private final double[]		FIT_MAX = {0.0, 2000.0,	5000.0, 1200.0};

	double[] fitLambda = {40.0,40.0};  // starting values for the fit
	double[] fitOrder = {0.5,0.5};  // starting values for the fit
	double[] lambda = {0.0,0.0};
	double[] order = {0.0,0.0};
	
	//IndexedList<CalibrationConstants> calibList;
	
	//private int iter = 1; // will be passed as parameter

	public TofTimeWalkEventListener() {

		stepName = "Timewalk";
		calib = 
				new CalibrationConstants(3,
						"tw0_left/F:tw1_left/F:tw2_left/F:tw0_right/F:tw1_right/F:tw2_right/F");

		calib.setName("/calibration/ftof/time_walk");
		calib.setPrecision(3);

		// assign constraints
		// TO DO

	}
	
	//@Override
//	public void timerUpdate() {
//		// only analyze at end of events for timewalk
//		
//	}

	@Override
	public void resetEventListener() {

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
							100, -2.5, 2.5);
					H2F rightHist = new H2F("TW_right",
							"Time residual vs ADC RIGHT Sector "+sector+
							" Paddle "+paddle,
							100, 0.0, ADC_MAX[layer],
							100, -2.5, 2.5);

					leftHist.setTitle("Time residual vs ADC LEFT : " + LAYER_NAME[layer_index] 
							+ " Sector "+sector+" Paddle "+paddle);
					leftHist.setXTitle("ADC LEFT");
					leftHist.setYTitle("Time residual");
					rightHist.setTitle("Time residual vs ADC RIGHT : " + LAYER_NAME[layer_index] 
							+ " Sector "+sector+" Paddle "+paddle);
					rightHist.setXTitle("ADC RIGHT");
					rightHist.setYTitle("Time residual");
					
					// create all the functions and graphs
					F1D trLeftFunc = new F1D("trLeftFunc", "[a]-([b]/(x^[c]))", 300.0, ADC_MAX[layer]);
					GraphErrors trLeftGraph = new GraphErrors();
					trLeftGraph.setName("trLeftGraph");					
					F1D trRightFunc = new F1D("trRightFunc", "[a]+([b]/(x^[c]))", 300.0, ADC_MAX[layer]);
					GraphErrors trRightGraph = new GraphErrors();
					trRightGraph.setName("trRightGraph");					
					
					DataGroup dg = new DataGroup(2,2);
					dg.addDataSet(leftHist, 0);
					dg.addDataSet(trLeftGraph, 2);
					dg.addDataSet(trLeftFunc, 2);
					dg.addDataSet(rightHist, 1);
					dg.addDataSet(trRightGraph, 3);
					dg.addDataSet(trRightFunc, 3);

					dataGroups.add(dg, sector,layer,paddle);					

				}
			}
		}
	}

	@Override
	public void processEvent(DataEvent event) {

		List<TOFPaddle> paddleList = DataProvider.getPaddleList(event);
		processPaddleList(paddleList);

	}
	
	@Override
	public void processPaddleList(List<TOFPaddle> paddleList) {

		for (TOFPaddle paddle : paddleList) {

			int sector = paddle.getDescriptor().getSector();
			int layer = paddle.getDescriptor().getLayer();
			int component = paddle.getDescriptor().getComponent();

				// fill timeResidual vs ADC
			//double [] tr = paddle.timeResidualsTest(lambda, order);
			double [] tr = paddle.timeResiduals(lambda, order);

			//if (paddle.includeInTimeWalk()) {

				dataGroups.getItem(sector,layer,component).getH2F("TW_left").fill(paddle.ADCL, tr[LEFT]);
				dataGroups.getItem(sector,layer,component).getH2F("TW_right").fill(paddle.ADCR, tr[RIGHT]);
			//}

		}
	}	

//	public void analyze() {
//		System.out.println("analyze");
//		for (int sector = 1; sector <= 6; sector++) {
//			for (int layer = 1; layer <= 3; layer++) {
//				int layer_index = layer - 1;
//				for (int paddle = 1; paddle <= NUM_PADDLES[layer_index]; paddle++) {
//					fit(sector, layer, paddle);
//				}
//			}
//		}
//		save();
//	}

	@Override
	public void fit(int sector, int layer, int paddle, double minRange, double maxRange) {

		H2F twL = dataGroups.getItem(sector,layer,paddle).getH2F("TW_left");
		H2F twR = dataGroups.getItem(sector,layer,paddle).getH2F("TW_right");

//		ArrayList<H1F> twLSlices = twL.getSlicesX();
//		ArrayList<H1F> twRSlices = twR.getSlicesX();
//		int numBins = twL.getXAxis().getNBins();
//
//		double[] binSlicesL = new double[numBins];
//		double[] binSlicesR = new double[numBins];
//		double[] meansL = new double[numBins];
//		double[] meansR = new double[numBins];
//
//		System.out.println("Before loop "+new Date());
//		for (int i=0; i<numBins; i++) {
//			System.out.println("Bin number "+i+" "+new Date());
//
//			H1F twLSlice = twLSlices.get(i);
//			H1F twRSlice = twRSlices.get(i);
//			System.out.println("Got slices "+new Date());
//
//			F1D fLeft = new F1D("gaus","[amp]*gaus([mean],[sigma])",-2.0,2.0);
//			F1D fRight = new F1D("gaus","[amp]*gaus([mean],[sigma])",-2.0,2.0);
//			
//			System.out.println("Created functions "+new Date());
//
//
//			fLeft.setParameter(0, 250.0);
//			fLeft.setParameter(1, 0.0);
//			fLeft.setParameter(2, 2.0);
//			DataFitter.fit(fLeft, twLSlice, "RNQ");
//			
//			System.out.println("Left fit done "+new Date());
//
//
//			fRight.setParameter(0, 250.0);
//			fRight.setParameter(1, 0.0);
//			fRight.setParameter(2, 2.0);
//			DataFitter.fit(fRight, twRSlice, "RNQ");
//
//			binSlicesL[i] = twL.getXAxis().getBinCenter(i);
//			meansL[i] = fLeft.getParameter(1);
//
//			binSlicesR[i] = twR.getXAxis().getBinCenter(i);
//			meansR[i] = fRight.getParameter(1);
//			
//		}
//
//		GraphErrors twLGraph = (GraphErrors) dataGroups.getItem(sector, layer, paddle).getData("trLeftGraph"); 
//		twLGraph.copy(new GraphErrors("trLeftGraph", binSlicesL, meansL));
//		GraphErrors twRGraph = (GraphErrors) dataGroups.getItem(sector, layer, paddle).getData("trRightGraph"); 
//		twRGraph.copy(new GraphErrors("trRightGraph", binSlicesR, meansR));
		
		// Try just using profile for now
		// above code seems to hang
		GraphErrors twLGraph = (GraphErrors) dataGroups.getItem(sector, layer, paddle).getData("trLeftGraph"); 
		twLGraph.copy(twL.getProfileX());
		
		GraphErrors twRGraph = (GraphErrors) dataGroups.getItem(sector, layer, paddle).getData("trRightGraph"); 
		twRGraph.copy(twR.getProfileX());
		
		
		// fit function to the graph of means
		F1D twLFunc = dataGroups.getItem(sector,layer,paddle).getF1D("trLeftFunc");
		twLFunc.setRange(FIT_MIN[layer], FIT_MAX[layer]);
		twLFunc.setParameter(0, 1.0);
		twLFunc.setParameter(1, fitLambda[LEFT]/2);
		twLFunc.setParameter(2, fitOrder[LEFT]);
		try {
			DataFitter.fit(twLFunc, twLGraph, "RNQ");
		}
		catch (Exception e) {
			e.printStackTrace();
		}

		F1D twRFunc = dataGroups.getItem(sector,layer,paddle).getF1D("trRightFunc");
		twRFunc.setRange(FIT_MIN[layer], FIT_MAX[layer]);
		twRFunc.setParameter(0, 1.0);
		twRFunc.setParameter(1, fitLambda[RIGHT]/2);
		twRFunc.setParameter(2, fitOrder[RIGHT]);
		try {
			DataFitter.fit(twRFunc, twRGraph, "RNQ");
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		
//		if (iter==1) {
//			trFunc.setParameter(0, 0.0);
//			trFunc.setParameter(1, 5.0);
//			trFunc.setParameter(0, 0.5);
//		}
//		if (iter>=2){
//			CalibrationConstants c = calibList.getItem(iter-1);
//			trFunc.setParameter(0, 0.0);
//			trFunc.setParameter(1, c.getDoubleValue("tw0_left", sector,layer,paddle));
//			trFunc.setParameter(0, c.getDoubleValue("tw1_left", sector,layer,paddle));
//		}
//		DataFitter.fit(trFunc, meanGraph, "RNQ");
//		System.out.println("New lambda left is "+trFunc.getParameter(1));
	}

	public Double getLambdaLeft(int sector, int layer, int paddle) {

		return 2.0 * dataGroups.getItem(sector,layer,paddle).getF1D("trLeftFunc").getParameter(1);
	}	

	public Double getOrderLeft(int sector, int layer, int paddle) {

		return dataGroups.getItem(sector,layer,paddle).getF1D("trLeftFunc").getParameter(2);
	}	
		
	public Double getLambdaRight(int sector, int layer, int paddle) {

		return 2.0 * dataGroups.getItem(sector,layer,paddle).getF1D("trRightFunc").getParameter(1);
	}	

	public Double getOrderRight(int sector, int layer, int paddle) {

		return dataGroups.getItem(sector,layer,paddle).getF1D("trRightFunc").getParameter(2);
	}	

	private void saveRow(int sector, int layer, int paddle) {

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
	
	@Override
	public void save() {

		//CalibrationConstants calib = calibList.getItem(iter);
		
		for (int sector = 1; sector <= 6; sector++) {
			for (int layer = 1; layer <= 3; layer++) {
				int layer_index = layer - 1;
				for (int paddle = 1; paddle <= NUM_PADDLES[layer_index]; paddle++) {
					calib.addEntry(sector, layer, paddle);
					saveRow(sector,layer,paddle);
				}
			}
		}
		calib.save("FTOF_CALIB_TIMEWALK.txt");
	}

	@Override
	public void drawPlots(int sector, int layer, int paddle, EmbeddedCanvas canvas) {

		canvas.draw(dataGroups.getItem(sector,layer,paddle).getGraph("trLeftGraph"));
		canvas.draw(dataGroups.getItem(sector,layer,paddle).getF1D("trLeftFunc"), "same");

	}

	
	@Override
	public DataGroup getSummary(int sector, int layer) {
				
		int layer_index = layer-1;
		double[] paddleNumbers = new double[NUM_PADDLES[layer_index]];
		double[] paddleUncs = new double[NUM_PADDLES[layer_index]];
		double[] lambdaLefts = new double[NUM_PADDLES[layer_index]];
		double[] zeroUncs = new double[NUM_PADDLES[layer_index]];
		double[] lambdaRights = new double[NUM_PADDLES[layer_index]];
		double[] orderLefts = new double[NUM_PADDLES[layer_index]];
		double[] orderRights = new double[NUM_PADDLES[layer_index]];

		for (int p = 1; p <= NUM_PADDLES[layer_index]; p++) {

			paddleNumbers[p - 1] = (double) p;
			paddleUncs[p - 1] = 0.0;
			lambdaLefts[p - 1] = getLambdaLeft(sector, layer, p);
			lambdaRights[p - 1] = getLambdaRight(sector, layer, p);
			orderLefts[p - 1] = getOrderLeft(sector, layer, p);
			orderRights[p - 1] = getOrderRight(sector, layer, p);
			zeroUncs[p - 1] = 0.0;
		}

		GraphErrors llSumm = new GraphErrors("llSumm", paddleNumbers,
				lambdaLefts, paddleUncs, zeroUncs);
		
//		llSumm.setTitle("Lambda Left: "
//				+ LAYER_NAME[layer - 1] + " Sector "
//				+ sector);
//		llSumm.setXTitle("Paddle Number");
//		summary.setYTitle("Lambda Left");
//		summary.setMarkerSize(5);
//		summary.setMarkerStyle(2);
//		

		GraphErrors lrSumm = new GraphErrors("lrSumm", paddleNumbers,
				lambdaRights, paddleUncs, zeroUncs);
		
//		summary.setTitle("Lambda Right: "
//				+ LAYER_NAME[layer - 1] + " Sector "
//				+ sector);
//		summary.setXTitle("Paddle Number");
//		summary.setYTitle("Lambda Right");
//		summary.setMarkerSize(5);
//		summary.setMarkerStyle(2);

		GraphErrors olSumm = new GraphErrors("olSumm", paddleNumbers,
				orderLefts, paddleUncs, zeroUncs);
		
//		summary.setTitle("Order Left: "
//				+ LAYER_NAME[layer - 1] + " Sector "
//				+ sector);
//		summary.setXTitle("Paddle Number");
//		summary.setYTitle("Order Left");
//		summary.setMarkerSize(5);
//		summary.setMarkerStyle(2);
//		

		GraphErrors orSumm = new GraphErrors("orSumm", paddleNumbers,
				orderRights, paddleUncs, zeroUncs);
		
//		summary.setTitle("Order Right: "
//				+ LAYER_NAME[layer - 1] + " Sector "
//				+ sector);
//		summary.setXTitle("Paddle Number");
//		summary.setYTitle("Order Right");
//		summary.setMarkerSize(5);
//		summary.setMarkerStyle(2);
//		

		DataGroup dg = new DataGroup(2,2);
		dg.addDataSet(llSumm, 0);
		dg.addDataSet(lrSumm, 1);
		dg.addDataSet(olSumm, 2);
		dg.addDataSet(orSumm, 3);
		
		return dg;
		
	}

	
}
