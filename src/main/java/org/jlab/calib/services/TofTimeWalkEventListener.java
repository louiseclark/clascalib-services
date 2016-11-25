package org.jlab.calib.services;


import java.util.ArrayList;
import java.util.List;

import javax.swing.JOptionPane;

import org.jlab.detector.calib.utils.CalibrationConstants;
import org.jlab.groot.data.GraphErrors;
import org.jlab.groot.data.H2F;
import org.jlab.groot.fitter.DataFitter;
import org.jlab.groot.graphics.EmbeddedCanvas;
import org.jlab.groot.group.DataGroup;
import org.jlab.groot.math.F1D;
import org.jlab.io.base.DataEvent;
import org.jlab.utils.groups.IndexedList;

public class TofTimeWalkEventListener extends TOFCalibrationEngine {

	public final int NUM_ITERATIONS = 5;

	private List<TOFPaddle>     allPaddleList = new ArrayList<TOFPaddle>();
	
	// constants for indexing the histograms
	public final int LEFT = 0;
	public final int RIGHT = 1;
	
	// indices for constants
	public final int LAMBDA_LEFT_OVERRIDE = 0;
	public final int ORDER_LEFT_OVERRIDE = 1;
	public final int LAMBDA_RIGHT_OVERRIDE = 2;
	public final int ORDER_RIGHT_OVERRIDE = 3;


	private final double[]		ADC_MIN = {0.0, 100.0, 200.0, 100.0};
	private final double[]		ADC_MAX = {0.0,	3000.0,	5000.0,	3000.0};
	private final double[]		FIT_MIN = {0.0,	500.0, 	1200.0, 500.0};
	private final double[]		FIT_MAX = {0.0, 1300.0,	2500.0, 1500.0};

	final double[] fitLambda = {40.0,40.0};  // default values for the constants
	final double[] fitOrder = {0.5,0.5};  // default values for the constants
	
    private String showPlotType = "TW_LEFT";
	
	public TofTimeWalkEventListener() {

		stepName = "Timewalk";
		fileNamePrefix = "FTOF_CALIB_TIMEWALK_";
		// get file name here so that each timer update overwrites it
		filename = nextFileName();
		
		calib = 
				new CalibrationConstants(3,
						"tw0_left/F:tw1_left/F:tw2_left/F:tw0_right/F:tw1_right/F:tw2_right/F");

		calib.setName("/calibration/ftof/time_walk");
		calib.setPrecision(3);

		// assign constraints
		calib.addConstraint(3, fitLambda[LEFT]*0.9,
				   fitLambda[LEFT]*1.1);
		calib.addConstraint(4, fitOrder[LEFT]*0.9,
				   fitOrder[LEFT]*1.1);
		calib.addConstraint(6, fitLambda[RIGHT]*0.9,
				   fitLambda[RIGHT]*1.1);
		calib.addConstraint(7, fitOrder[RIGHT]*0.9,
				   fitOrder[RIGHT]*1.1);

	}
	
	@Override
	public void timerUpdate() {
		// only analyze at end of events for timewalk
		
	}

	@Override
	public void resetEventListener() {

		// histograms are reset for subsequent iterations at the analyze step
		createHists();
	}
	
	public void resetHists() {
		for (int sector = 1; sector <= 6; sector++) {
			for (int layer = 1; layer <= 3; layer++) {
				int layer_index = layer - 1;
				for (int paddle = 1; paddle <= NUM_PADDLES[layer_index]; paddle++) {
					dataGroups.getItem(sector,layer,paddle).getH2F("trLeftHist").reset();
					dataGroups.getItem(sector,layer,paddle).getH2F("trRightHist").reset();
				}	
			}
		}
	}
	
	public void createHists() {

		// LC perform init processing
		for (int sector = 1; sector <= 6; sector++) {
			for (int layer = 1; layer <= 3; layer++) {
				int layer_index = layer - 1;
				for (int paddle = 1; paddle <= NUM_PADDLES[layer_index]; paddle++) {

					// data group format is
					
					DataGroup dg = new DataGroup(2,2);
					
					// create all the histograms
					H2F leftHist = new H2F("trLeftHist",
							"Time residual vs ADC LEFT Sector "+sector+
							" Paddle "+paddle,
							100, 0.0, ADC_MAX[layer],
							100, -2.0, 6.0);
					H2F rightHist = new H2F("trRightHist",
							"Time residual vs ADC RIGHT Sector "+sector+
							" Paddle "+paddle,
							100, 0.0, ADC_MAX[layer],
							100, -2.0, 6.0);

					leftHist.setTitle("Time residual vs ADC LEFT : " + LAYER_NAME[layer_index] 
							+ " Sector "+sector+" Paddle "+paddle);
					leftHist.setTitleX("ADC LEFT");
					leftHist.setTitleY("Time residual (ns)");
					rightHist.setTitle("Time residual vs ADC RIGHT : " + LAYER_NAME[layer_index] 
							+ " Sector "+sector+" Paddle "+paddle);
					rightHist.setTitleX("ADC RIGHT");
					rightHist.setTitleY("Time residual (ns)");
					
					dg.addDataSet(leftHist, 0);
					dg.addDataSet(rightHist, 1);
					
					// create all the functions and graphs
					F1D trLeftFunc = new F1D("trLeftFunc", "[a]+([b]/(x^[c]))", ADC_MIN[layer], ADC_MAX[layer]);
					GraphErrors trLeftGraph = new GraphErrors();
					trLeftGraph.setName("trLeftGraph");					
					F1D trRightFunc = new F1D("trRightFunc", "[a]+([b]/(x^[c]))", ADC_MIN[layer], ADC_MAX[layer]);
					GraphErrors trRightGraph = new GraphErrors();
					trRightGraph.setName("trRightGraph");
					
					trLeftFunc.setLineColor(FUNC_COLOUR);
					trLeftFunc.setLineWidth(FUNC_LINE_WIDTH);
					trLeftGraph.setMarkerSize(MARKER_SIZE);
					trLeftGraph.setLineThickness(MARKER_LINE_WIDTH);

					trRightFunc.setLineColor(FUNC_COLOUR);
					trRightFunc.setLineWidth(FUNC_LINE_WIDTH);
					trRightGraph.setMarkerSize(MARKER_SIZE);
					trRightGraph.setLineThickness(MARKER_LINE_WIDTH);
					
					dg.addDataSet(trLeftFunc, 2);
					dg.addDataSet(trLeftGraph, 2);
					dg.addDataSet(trRightFunc, 3);
					dg.addDataSet(trRightGraph, 3);
					dataGroups.add(dg,sector,layer,paddle);	
					
					setPlotTitle(sector,layer,paddle);
					
					// initialize the constants array
					Double[] consts = {UNDEFINED_OVERRIDE, UNDEFINED_OVERRIDE, UNDEFINED_OVERRIDE, UNDEFINED_OVERRIDE};
					// override values
					
					constants.add(consts, sector, layer, paddle);
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

		// store the paddle so we can iterate through them
		for (TOFPaddle paddle : paddleList) {
			allPaddleList.add(paddle);
		}
	}	


	// non-standard analyze step for timewalk
	// require to iterate through all events several times
	@Override
	public void analyze() {
		
		for (int iter=0; iter<NUM_ITERATIONS; iter++) {

			System.out.println("Iteration "+iter+" start");

			resetHists();
			fitAll(iter);
			
			System.out.println("Iteration "+iter+" end");

		}
		save();
		calib.fireTableDataChanged();
	}

	public void fitAll(int iter) {
		
		for(TOFPaddle paddle : allPaddleList){
			
			// Fill the histograms
			int sector = paddle.getDescriptor().getSector();
			int layer = paddle.getDescriptor().getLayer();
			int component = paddle.getDescriptor().getComponent();

			// fill timeResidual vs ADC
			boolean test = false;
			if (test) {
				double [] tr = paddle.timeResidualsTest(getLambdas(sector,layer,component,iter), 
												getOrders(sector,layer,component,iter));
				
				dataGroups.getItem(sector,layer,component).getH2F("trLeftHist").fill(paddle.geometricMean(), tr[LEFT]);
				dataGroups.getItem(sector,layer,component).getH2F("trRightHist").fill(paddle.geometricMean(), tr[RIGHT]);
			}
			else {
				
				if (paddle.includeInTimeWalk()) {
					double [] tr = paddle.timeResiduals(getLambdas(sector,layer,component,iter), 
							getOrders(sector,layer,component,iter));

					dataGroups.getItem(sector,layer,component).getH2F("trLeftHist").fill(paddle.ADCL, tr[LEFT]);
					dataGroups.getItem(sector,layer,component).getH2F("trRightHist").fill(paddle.ADCR, tr[RIGHT]);
				}
			}
						
		}
		
		// Now do the fits for this iteration
		for (int sector = 1; sector <= 6; sector++) {
			for (int layer = 1; layer <= 3; layer++) {
				int layer_index = layer - 1;
				for (int paddle = 1; paddle <= NUM_PADDLES[layer_index]; paddle++) {
					fit(sector,layer,paddle,0.0,0.0);
		
//					System.out.println("SLC "+sector+layer+paddle+" Lambda left is "+getLambdaLeft(sector,layer,paddle));
//					System.out.println("SLC "+sector+layer+paddle+" Lambda right is "+getLambdaRight(sector,layer,paddle));
//					System.out.println("SLC "+sector+layer+paddle+" Order left is "+getOrderLeft(sector,layer,paddle));
//					System.out.println("SLC "+sector+layer+paddle+" Order right is "+getOrderRight(sector,layer,paddle));

				}
			}
		}
	}
	
	@Override
	public void fit(int sector, int layer, int paddle, double minRange, double maxRange) {
		
		//System.out.println("Fitting "+sector+layer+paddle+" start");
		
		H2F twL = dataGroups.getItem(sector,layer,paddle).getH2F("trLeftHist");
		H2F twR = dataGroups.getItem(sector,layer,paddle).getH2F("trRightHist");

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
		twLFunc.setParameter(1, fitLambda[LEFT]/2.0);
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
		twRFunc.setParameter(1, fitLambda[RIGHT]/2.0);
		twRFunc.setParameter(2, fitOrder[RIGHT]);
		try {
			if (paddle==20) {
				DataFitter.fit(twRFunc, twRGraph, "RN");
			}
			else {
				DataFitter.fit(twRFunc, twRGraph, "RNQ");
			}
			
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		//System.out.println("Fitting "+sector+layer+paddle+" end");
	}

	public double[] getLambdas(int sector, int layer, int paddle, int iter) {
		// leave as default for first iteration
		// take values from function for subsequent iterations
		double[] lambdas = {40.0, 40.0}; //fitLambda; //{0.0,0.0};
		if (iter>0) {
			lambdas[0] = getLambdaLeft(sector,layer,paddle);
			lambdas[1] = getLambdaRight(sector,layer,paddle);
			
		}
		return lambdas;
	}

	public double[] getOrders(int sector, int layer, int paddle, int iter) {
		// leave as zero for first iteration
		// take values from function for subsequent iterations
		double[] orders = {0.5, 0.5}; // fitOrder; //{0.0,0.0};
		if (iter>0) {
			orders[0] = getOrderLeft(sector,layer,paddle);
			orders[1] = getOrderRight(sector,layer,paddle);
			
		}
		return orders;
	}
	
	@Override
	public void customFit(int sector, int layer, int paddle){

		String[] fields = { "Override Lambda Left:", "Override Order Left:", "SPACE",
				"Override Lambda Right:", "Override Order Right:"};
		TOFCustomFitPanel panel = new TOFCustomFitPanel(fields);

		int result = JOptionPane.showConfirmDialog(null, panel, 
				"Adjust Fit / Override for paddle "+paddle, JOptionPane.OK_CANCEL_OPTION);
		if (result == JOptionPane.OK_OPTION) {

			double overrideLL = toDouble(panel.textFields[0].getText());
			double overrideOL = toDouble(panel.textFields[1].getText());
			double overrideLR = toDouble(panel.textFields[2].getText());
			double overrideOR = toDouble(panel.textFields[3].getText());
			
			// save the override values
			Double[] consts = constants.getItem(sector, layer, paddle);
			consts[LAMBDA_LEFT_OVERRIDE] = overrideLL;
			consts[ORDER_LEFT_OVERRIDE] = overrideOL;
			consts[LAMBDA_RIGHT_OVERRIDE] = overrideLR;
			consts[ORDER_RIGHT_OVERRIDE] = overrideOR;

			fit(sector, layer, paddle);

			// update the table
			saveRow(sector,layer,paddle);
			calib.fireTableDataChanged();
			
		}	 
	}
	
	public Double getLambdaLeft(int sector, int layer, int paddle) {
		
		double lambdaLeft = 0.0;
		double overrideVal = constants.getItem(sector, layer, paddle)[LAMBDA_LEFT_OVERRIDE];

		if (overrideVal != UNDEFINED_OVERRIDE) {
			lambdaLeft = overrideVal;
		}
		else {
			lambdaLeft = dataGroups.getItem(sector,layer,paddle).getF1D("trLeftFunc").getParameter(1) * 2.0;
		}
		return lambdaLeft;
	}	

	public Double getOrderLeft(int sector, int layer, int paddle) {

		double orderLeft = 0.0;
		double overrideVal = constants.getItem(sector, layer, paddle)[ORDER_LEFT_OVERRIDE];

		if (overrideVal != UNDEFINED_OVERRIDE) {
			orderLeft = overrideVal;
		}
		else {
			orderLeft = dataGroups.getItem(sector,layer,paddle).getF1D("trLeftFunc").getParameter(2);
		}
		return orderLeft;
	}	
		
	public Double getLambdaRight(int sector, int layer, int paddle) {
		
		double lambdaRight = 0.0;
		double overrideVal = constants.getItem(sector, layer, paddle)[LAMBDA_RIGHT_OVERRIDE];

		if (overrideVal != UNDEFINED_OVERRIDE) {
			lambdaRight = overrideVal;
		}
		else {
			lambdaRight = dataGroups.getItem(sector,layer,paddle).getF1D("trRightFunc").getParameter(1) * 2.0;
		}
		return lambdaRight;
	}	

	public Double getOrderRight(int sector, int layer, int paddle) {

		double orderRight = 0.0;
		double overrideVal = constants.getItem(sector, layer, paddle)[ORDER_RIGHT_OVERRIDE];

		if (overrideVal != UNDEFINED_OVERRIDE) {
			orderRight = overrideVal;
		}
		else {
			orderRight = dataGroups.getItem(sector,layer,paddle).getF1D("trRightFunc").getParameter(2);
		}
		return orderRight;
	}	

	@Override
	public void saveRow(int sector, int layer, int paddle) {

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
	public void setPlotTitle(int sector, int layer, int paddle) {
		// reset hist title as may have been set to null by show all 
		dataGroups.getItem(sector,layer,paddle).getGraph("trLeftGraph").setTitleX("ADC LEFT");
		dataGroups.getItem(sector,layer,paddle).getGraph("trLeftGraph").setTitleY("Time residual (ns)");
		dataGroups.getItem(sector,layer,paddle).getGraph("trRightGraph").setTitleX("ADC LEFT");
		dataGroups.getItem(sector,layer,paddle).getGraph("trRightGraph").setTitleY("Time residual (ns)");
		//System.out.println("Setting TW graph titles");
	}

	@Override
	public void drawPlots(int sector, int layer, int paddle, EmbeddedCanvas canvas) {

		if (showPlotType == "TW_LEFT") {
			GraphErrors graph = dataGroups.getItem(sector,layer,paddle).getGraph("trLeftGraph");
			graph.setTitleX("");
			graph.setTitleY("");
			canvas.draw(graph);
			canvas.draw(dataGroups.getItem(sector,layer,paddle).getF1D("trLeftFunc"), "same");
		}
		else {
			GraphErrors graph = dataGroups.getItem(sector,layer,paddle).getGraph("trRightGraph");
			graph.setTitleX("");
			graph.setTitleY("");
			canvas.draw(graph);
			canvas.draw(dataGroups.getItem(sector,layer,paddle).getF1D("trRightFunc"), "same");
			
		}

	}

	@Override
	public void showPlots(int sector, int layer) {

		showPlotType = "TW_LEFT";
		stepName = "Time walk - ADC Left";
		super.showPlots(sector, layer);
		showPlotType = "TW_RIGHT";
		stepName = "Time walk - ADC right";
		super.showPlots(sector, layer);
		
	}
	
	@Override
	public boolean isGoodPaddle(int sector, int layer, int paddle) {

		return (getLambdaLeft(sector,layer,paddle) >= fitLambda[LEFT]*0.9 && 
				getLambdaLeft(sector,layer,paddle) <= fitLambda[LEFT]*1.1 &&
				getOrderLeft(sector,layer,paddle) >= fitOrder[LEFT]*0.9 && 
				getOrderLeft(sector,layer,paddle) <= fitOrder[LEFT]*1.1 &&
				getLambdaRight(sector,layer,paddle) >= fitLambda[RIGHT]*0.9 && 
				getLambdaRight(sector,layer,paddle) <= fitLambda[RIGHT]*1.1 &&
				getOrderRight(sector,layer,paddle) >= fitOrder[RIGHT]*0.9 && 
				getOrderRight(sector,layer,paddle) <= fitOrder[RIGHT]*1.1);

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
		
		llSumm.setTitleX("Paddle Number");
		llSumm.setTitleY("Lambda left");
		llSumm.setMarkerSize(MARKER_SIZE);
		llSumm.setLineThickness(MARKER_LINE_WIDTH);

		GraphErrors lrSumm = new GraphErrors("lrSumm", paddleNumbers,
				lambdaRights, paddleUncs, zeroUncs);
		
		lrSumm.setTitleX("Paddle Number");
		lrSumm.setTitleY("Lambda right");
		lrSumm.setMarkerSize(MARKER_SIZE);
		lrSumm.setLineThickness(MARKER_LINE_WIDTH);

		GraphErrors olSumm = new GraphErrors("olSumm", paddleNumbers,
				orderLefts, paddleUncs, zeroUncs);
		
		olSumm.setTitleX("Paddle Number");
		olSumm.setTitleY("Order left");
		olSumm.setMarkerSize(MARKER_SIZE);
		olSumm.setLineThickness(MARKER_LINE_WIDTH);

		GraphErrors orSumm = new GraphErrors("orSumm", paddleNumbers,
				orderRights, paddleUncs, zeroUncs);
		
		orSumm.setTitleX("Paddle Number");
		orSumm.setTitleY("Order right");
		orSumm.setMarkerSize(MARKER_SIZE);
		orSumm.setLineThickness(MARKER_LINE_WIDTH);

		DataGroup dg = new DataGroup(2,2);
		dg.addDataSet(llSumm, 0);
		dg.addDataSet(lrSumm, 1);
		dg.addDataSet(olSumm, 2);
		dg.addDataSet(orSumm, 3);
		
		return dg;
		
	}

}
