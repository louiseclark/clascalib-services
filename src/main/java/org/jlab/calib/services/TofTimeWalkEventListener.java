package org.jlab.calib.services;


import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JTabbedPane;

import org.jlab.detector.base.DetectorDescriptor;
import org.jlab.detector.calib.utils.CalibrationConstants;
import org.jlab.detector.calib.utils.DatabaseConstantProvider;
import org.jlab.groot.data.GraphErrors;
import org.jlab.groot.data.H1F;
import org.jlab.groot.data.H2F;
import org.jlab.groot.fitter.DataFitter;
import org.jlab.groot.graphics.EmbeddedCanvas;
import org.jlab.groot.group.DataGroup;
//import org.jlab.calib.temp.DataGroup;
import org.jlab.groot.math.F1D;
import org.jlab.io.base.DataEvent;
import org.jlab.utils.groups.IndexedList;

public class TofTimeWalkEventListener extends TOFCalibrationEngine {

	public boolean calibChallTest = false;

	// constants for indexing the histograms
	public final int LEFT = 0;
	public final int RIGHT = 1;

	// indices for constants
	public final int LAMBDA_LEFT_OVERRIDE = 0;
	public final int ORDER_LEFT_OVERRIDE = 1;
	public final int LAMBDA_RIGHT_OVERRIDE = 2;
	public final int ORDER_RIGHT_OVERRIDE = 3;

	private final double[]        ADC_MIN = {0.0, 150.0,  500.0,  150.0};
	private final double[]        ADC_MAX = {0.0, 2000.0, 3500.0, 2000.0};
	private final double[]        FIT_MIN = {0.0,  400.0, 1200.0, 400.0};
	private final double[]        FIT_MAX = {0.0, 1000.0, 2400.0, 1000.0};

//	private final double[]        ADC_MIN = {0.0, 300.0, 500.0, 300.0};
//	private final double[]        ADC_MAX = {0.0, 3000.0,    5000.0,    3000.0};
//	private final double[]        FIT_MIN = {0.0,  500.0,    1200.0,     500.0};
//	private final double[]        FIT_MAX = {0.0, 1100.0,    2400.0,    1100.0};

	final double[] fitLambda = {40.0,40.0};  // default values for the constants
	final double[] fitOrder = {0.5,0.5};  // default values for the constants

	private String showPlotType = "TW_LEFT";

	private IndexedList<H2F[]> offsetHists = new IndexedList<H2F[]>(4);  // indexed by s,l,c, offset (in beam bucket multiples), H2F has {left, right}
	private final int NUM_OFFSET_HISTS = 20;

	public TofTimeWalkEventListener() {

		stepName = "Timewalk";
		fileNamePrefix = "FTOF_CALIB_TIMEWALK_";
		// get file name here so that each timer update overwrites it
		filename = nextFileName();

		calib = new CalibrationConstants(3,
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

	public void populatePrevCalib() {

		if (calDBSource==CAL_FILE) {

			// read in the left right values from the text file			
			String line = null;
			try { 

				// Open the file
				FileReader fileReader = 
						new FileReader(prevCalFilename);

				// Always wrap FileReader in BufferedReader
				BufferedReader bufferedReader = 
						new BufferedReader(fileReader);            

				line = bufferedReader.readLine();

				while (line != null) {

					String[] lineValues;
					lineValues = line.split(" ");

					int sector = Integer.parseInt(lineValues[0]);
					int layer = Integer.parseInt(lineValues[1]);
					int paddle = Integer.parseInt(lineValues[2]);
					double lamL = Double.parseDouble(lineValues[3]);
					double ordL = Double.parseDouble(lineValues[4]);
					double lamR = Double.parseDouble(lineValues[6]);
					double ordR = Double.parseDouble(lineValues[7]);

					timeWalkValues.addEntry(sector, layer, paddle);
					timeWalkValues.setDoubleValue(lamL,
							"tw0_left", sector, layer, paddle);
					timeWalkValues.setDoubleValue(ordL,
							"tw1_left", sector, layer, paddle);
					timeWalkValues.setDoubleValue(lamR,
							"tw0_right", sector, layer, paddle);
					timeWalkValues.setDoubleValue(ordR,
							"tw1_right", sector, layer, paddle);
					
					line = bufferedReader.readLine();
				}

				bufferedReader.close();            
			}
			catch(FileNotFoundException ex) {
				ex.printStackTrace();
				System.out.println(
						"Unable to open file '" + 
								prevCalFilename + "'");                
			}
			catch(IOException ex) {
				System.out.println(
						"Error reading file '" 
								+ prevCalFilename + "'");                   
				ex.printStackTrace();
			}			
		}
		else if (calDBSource==CAL_DEFAULT) {
			for (int sector = 1; sector <= 6; sector++) {
				for (int layer = 1; layer <= 3; layer++) {
					int layer_index = layer - 1;
					for (int paddle = 1; paddle <= NUM_PADDLES[layer_index]; paddle++) {
						timeWalkValues.addEntry(sector, layer, paddle);
						timeWalkValues.setDoubleValue(0.0, //fitLambda[0],
								"tw0_left", sector, layer, paddle);
						timeWalkValues.setDoubleValue(fitOrder[0],
								"tw1_left", sector, layer, paddle);
						timeWalkValues.setDoubleValue(0.0, //fitLambda[1],
								"tw0_right", sector, layer, paddle);
						timeWalkValues.setDoubleValue(fitOrder[1],
								"tw1_right", sector, layer, paddle);
					}
				}
			}			
		}
		else if (calDBSource==CAL_DB) {
			DatabaseConstantProvider dcp = new DatabaseConstantProvider(prevCalRunNo, "default");
			timeWalkValues = dcp.readConstants("/calibration/ftof/time_walk");
			dcp.disconnect();
		}
	}
	
	@Override
	public void resetEventListener() {

		// perform init processing
		
		// get the previous iteration calibration values
		populatePrevCalib();
		
		System.out.println(stepName);
		System.out.println("calDBSource "+calDBSource);
		System.out.println("prevCalRunNo "+prevCalRunNo);
		System.out.println("prevCalFilename "+prevCalFilename);
		for (int i=0; i<timeWalkValues.getRowCount(); i++) {
			String line = new String();
			for (int j=0; j<timeWalkValues.getColumnCount(); j++) {
				line = line+timeWalkValues.getValueAt(i, j);
				if (j<timeWalkValues.getColumnCount()-1) {
					line = line+" ";
				}
			}
			System.out.println(line);
		}

		// create the histograms
		for (int sector = 1; sector <= 6; sector++) {
			for (int layer = 1; layer <= 3; layer++) {
				int layer_index = layer - 1;
				for (int paddle = 1; paddle <= NUM_PADDLES[layer_index]; paddle++) {

					// data group format is
					DataGroup dg = new DataGroup(2,3);
					
					// create all the histograms
					H2F leftHist = new H2F("trLeftHist",
							"Time residual vs ADC LEFT Sector "+sector+
							" Paddle "+paddle,
							100, ADC_MIN[layer], ADC_MAX[layer],
							100, -1.0, 3.0);
					H2F rightHist = new H2F("trRightHist",
							"Time residual vs ADC RIGHT Sector "+sector+
							" Paddle "+paddle,
							100, ADC_MIN[layer], ADC_MAX[layer],
							100, -1.0, 3.0);

					leftHist.setTitle("Time residual vs ADC LEFT : " + LAYER_NAME[layer_index] 
							+ " Sector "+sector+" Paddle "+paddle);
					leftHist.setTitleX("ADC LEFT");
					leftHist.setTitleY("Time residual (ns) % "+BEAM_BUCKET);
					rightHist.setTitle("Time residual vs ADC RIGHT : " + LAYER_NAME[layer_index] 
							+ " Sector "+sector+" Paddle "+paddle);
					rightHist.setTitleX("ADC RIGHT");
					rightHist.setTitleY("Time residual (ns)% "+BEAM_BUCKET);

					dg.addDataSet(leftHist, 0);
					dg.addDataSet(rightHist, 1);
					
					// create all the functions and graphs
					F1D trLeftFunc = new F1D("trLeftFunc", "(([a]/(x^[b]))+[c])", FIT_MIN[layer], FIT_MAX[layer]);
					F1D trLeftFuncHist = new F1D("trLeftFuncHist", "(([a]/(x^[b]))+[c])", FIT_MIN[layer], FIT_MAX[layer]);
					GraphErrors trLeftGraph = new GraphErrors();
					trLeftGraph.setName("trLeftGraph");                    
					F1D trRightFunc = new F1D("trRightFunc", "(([a]/(x^[b]))+[c])", FIT_MIN[layer], FIT_MAX[layer]);
					F1D trRightFuncHist = new F1D("trRightFuncHist", "(([a]/(x^[b]))+[c])", FIT_MIN[layer], FIT_MAX[layer]);
					GraphErrors trRightGraph = new GraphErrors();
					trRightGraph.setName("trRightGraph");

					trLeftFunc.setLineColor(FUNC_COLOUR);
					trLeftFunc.setLineWidth(FUNC_LINE_WIDTH);
					trLeftFuncHist.setLineColor(FUNC_COLOUR);
					trLeftFuncHist.setLineWidth(FUNC_LINE_WIDTH);
					trLeftGraph.setMarkerSize(MARKER_SIZE);
					trLeftGraph.setLineThickness(MARKER_LINE_WIDTH);

					trRightFunc.setLineColor(FUNC_COLOUR);
					trRightFunc.setLineWidth(FUNC_LINE_WIDTH);
					trRightFuncHist.setLineColor(FUNC_COLOUR);
					trRightFuncHist.setLineWidth(FUNC_LINE_WIDTH);
					trRightGraph.setMarkerSize(MARKER_SIZE);
					trRightGraph.setLineThickness(MARKER_LINE_WIDTH);

					dg.addDataSet(trLeftFunc, 4);
					dg.addDataSet(trLeftFuncHist, 2);
					dg.addDataSet(trLeftGraph, 4);
					dg.addDataSet(trRightFunc, 5);
					dg.addDataSet(trRightFuncHist, 3);
					dg.addDataSet(trRightGraph, 5);
					
					if (calibChallTest) {
						// ************  TEST ***************
						// create functions for the actual smeared values
						F1D smearedLeftFunc = new F1D("smLeftFunc", "[a]+([b]/(x^[c]))", ADC_MIN[layer], ADC_MAX[layer]);
						F1D smearedRightFunc = new F1D("smRightFunc", "[a]+([b]/(x^[c]))", ADC_MIN[layer], ADC_MAX[layer]);

						smearedLeftFunc.setParameter(0, 0.0);
						smearedLeftFunc.setParameter(1,         
								TOFCalibrationEngine.timeWalkValues.getDoubleValue("tw0_left",
										sector,layer,paddle));
						smearedLeftFunc.setParameter(2,         
								TOFCalibrationEngine.timeWalkValues.getDoubleValue("tw1_left",
										sector,layer,paddle));

						smearedRightFunc.setParameter(0, 0.0);
						smearedRightFunc.setParameter(1,         
								TOFCalibrationEngine.timeWalkValues.getDoubleValue("tw0_right",
										sector,layer,paddle));
						smearedRightFunc.setParameter(2,         
								TOFCalibrationEngine.timeWalkValues.getDoubleValue("tw1_right",
										sector,layer,paddle));

						smearedLeftFunc.setLineWidth(FUNC_LINE_WIDTH);
						smearedRightFunc.setLineWidth(FUNC_LINE_WIDTH);

						if (calibChallTest) {
							dg.addDataSet(smearedLeftFunc, 4);
							dg.addDataSet(smearedRightFunc, 5);
						}
					}

					dataGroups.add(dg,sector,layer,paddle);

					setPlotTitle(sector,layer,paddle);

					// now create the offset hists
					for (int i=0; i<NUM_OFFSET_HISTS; i++) {

						double n = NUM_OFFSET_HISTS;
						double offset = i*(BEAM_BUCKET/n);
						H2F offLeftHist = new H2F("offsetLeft",
								"Time residual vs ADC Left Sector "+sector+
								" Paddle "+paddle+" Offset = "+offset+"+ ns",
								100, ADC_MIN[layer], ADC_MAX[layer],
								100, -1.0, 3.0);

						H2F offRightHist = new H2F("offsetRight",
								"Time residual vs ADC Right Sector "+sector+
								" Paddle "+paddle+" Offset = "+offset+"+ ns",
								100, ADC_MIN[layer], ADC_MAX[layer],
								100, -1.0, 3.0);

						H2F[] hists = {offLeftHist, offRightHist};
						offsetHists.add(hists, sector,layer,paddle,i);
					}

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

		//DataProvider dp = new DataProvider();
		List<TOFPaddle> paddleList = DataProvider.getPaddleList(event);
		processPaddleList(paddleList);

	}

	@Override
	public void processPaddleList(List<TOFPaddle> paddleList) {

		for (TOFPaddle paddle : paddleList) {

			// Fill the histograms
			int sector = paddle.getDescriptor().getSector();
			int layer = paddle.getDescriptor().getLayer();
			int component = paddle.getDescriptor().getComponent();

			// fill timeResidual vs ADC
			if (paddle.includeInTimeWalk()) {
			
				dataGroups.getItem(sector,layer,component).getH2F("trLeftHist").fill(paddle.ADCL, paddle.deltaTLeft());
				dataGroups.getItem(sector,layer,component).getH2F("trRightHist").fill(paddle.ADCR, paddle.deltaTRight());

				// fill the offset histograms
				for (int i=0; i<NUM_OFFSET_HISTS; i++) {
					double n = NUM_OFFSET_HISTS;
					double offset = i*(BEAM_BUCKET/n);
					offsetHists.getItem(sector,layer,component,i)[0].fill(paddle.ADCL, (paddle.deltaTLeft()+offset)%BEAM_BUCKET);
					offsetHists.getItem(sector,layer,component,i)[1].fill(paddle.ADCR, (paddle.deltaTRight()+offset)%BEAM_BUCKET);
				}
			}
		}
	}    

	@Override
	public void fit(int sector, int layer, int paddle, double minRange, double maxRange) {
		
		System.out.println("TW fit SLC "+sector+layer+paddle);

		// Find the best offset hist
		// create function for the nominal values
		//F1D nomFunc = new F1D("nomFunc", "([a]/(x^[b]))", FIT_MIN[layer], FIT_MAX[layer]);
		F1D nomFunc = new F1D("nomFunc", "(([a]/(x^[b]))-[c])", FIT_MIN[layer], FIT_MAX[layer]);
		nomFunc.setParLimits(0, 39.9, 40.1);
		nomFunc.setParLimits(1, 0.49, 0.51);
		if (layer==1) {
			nomFunc.setParLimits(2, 0.99, 1.01);
		}
		else {
			nomFunc.setParLimits(2, -0.01, 0.01);
		}

		double minChiSqLeft = 1000.0;
		double minChiSqRight = 1000.0;
		int offsetIdxLeft = 0;
		int offsetIdxRight = 0;

		for (int i=0; i < NUM_OFFSET_HISTS; i++) {

			H2F leftHist = offsetHists.getItem(sector,layer,paddle,i)[0];
			GraphErrors leftGraph = leftHist.getProfileX();
			leftGraph.setMarkerSize(MARKER_SIZE);
			leftGraph.setLineThickness(MARKER_LINE_WIDTH);

			if (leftGraph.getDataSize(0) != 0) {
				DataFitter.fit(nomFunc, leftGraph, "RN");
				if (nomFunc.getChiSquare() < minChiSqLeft) {
					minChiSqLeft = nomFunc.getChiSquare();
					offsetIdxLeft = i;
				}
			}
			
			H2F rightHist = offsetHists.getItem(sector,layer,paddle,i)[1];
			GraphErrors rightGraph = rightHist.getProfileX();
			rightGraph.setMarkerSize(MARKER_SIZE);
			rightGraph.setLineThickness(MARKER_LINE_WIDTH);
			if (rightGraph.getDataSize(0) != 0) {
				DataFitter.fit(nomFunc, rightGraph, "RN");
				if (nomFunc.getChiSquare() < minChiSqRight) {
					minChiSqRight = nomFunc.getChiSquare();
					offsetIdxRight = i;
				}
			}
		}			

		System.out.println("Best offset hists are "+offsetIdxLeft+" "+minChiSqLeft+" "+offsetIdxRight+" "+minChiSqRight);
		
		System.out.println("Fitting "+sector+layer+paddle+" start");

//		H2F twL = dataGroups.getItem(sector,layer,paddle).getH2F("trLeftHist");
		H2F twL = offsetHists.getItem(sector,layer,paddle,offsetIdxLeft)[0];
//		H2F twR = dataGroups.getItem(sector,layer,paddle).getH2F("trRightHist");
		H2F twR = offsetHists.getItem(sector,layer,paddle,offsetIdxRight)[1];

		//        ArrayList<H1F> twLSlices = twL.getSlicesX();
		//        ArrayList<H1F> twRSlices = twR.getSlicesX();
		//        int numBins = twL.getXAxis().getNBins();
		//
		//        double[] binSlicesL = new double[numBins];
		//        double[] binSlicesR = new double[numBins];
		//        double[] meansL = new double[numBins];
		//        double[] meansR = new double[numBins];
		//
		//        System.out.println("Before loop "+new Date());
		//        for (int i=0; i<numBins; i++) {
		//            System.out.println("Bin number "+i+" "+new Date());
		//
		//            H1F twLSlice = twLSlices.get(i);
		//            H1F twRSlice = twRSlices.get(i);
		//            System.out.println("Got slices "+new Date());
		//
		//            F1D fLeft = new F1D("gaus","[amp]*gaus([mean],[sigma])",-2.0,2.0);
		//            F1D fRight = new F1D("gaus","[amp]*gaus([mean],[sigma])",-2.0,2.0);
		//            
		//            System.out.println("Created functions "+new Date());
		//
		//
		//            fLeft.setParameter(0, 250.0);
		//            fLeft.setParameter(1, 0.0);
		//            fLeft.setParameter(2, 2.0);
		//            DataFitter.fit(fLeft, twLSlice, "RNQ");
		//            
		//            System.out.println("Left fit done "+new Date());
		//
		//
		//            fRight.setParameter(0, 250.0);
		//            fRight.setParameter(1, 0.0);
		//            fRight.setParameter(2, 2.0);
		//            DataFitter.fit(fRight, twRSlice, "RNQ");
		//
		//            binSlicesL[i] = twL.getXAxis().getBinCenter(i);
		//            meansL[i] = fLeft.getParameter(1);
		//
		//            binSlicesR[i] = twR.getXAxis().getBinCenter(i);
		//            meansR[i] = fRight.getParameter(1);
		//            
		//        }
		//
		//        GraphErrors twLGraph = (GraphErrors) dataGroups.getItem(sector, layer, paddle).getData("trLeftGraph"); 
		//        twLGraph.copy(new GraphErrors("trLeftGraph", binSlicesL, meansL));
		//        GraphErrors twRGraph = (GraphErrors) dataGroups.getItem(sector, layer, paddle).getData("trRightGraph"); 
		//        twRGraph.copy(new GraphErrors("trRightGraph", binSlicesR, meansR));

		// Try just using profile for now
		// above code seems to hang
		GraphErrors twLGraph = (GraphErrors) dataGroups.getItem(sector, layer, paddle).getData("trLeftGraph"); 
		twLGraph.copy(twL.getProfileX());

		GraphErrors twRGraph = (GraphErrors) dataGroups.getItem(sector, layer, paddle).getData("trRightGraph"); 
		twRGraph.copy(twR.getProfileX());

		// fit function to the graph of means
		F1D twLFunc = dataGroups.getItem(sector,layer,paddle).getF1D("trLeftFunc");
		F1D twLFuncHist = dataGroups.getItem(sector,layer,paddle).getF1D("trLeftFuncHist");
		twLFunc.setRange(FIT_MIN[layer], FIT_MAX[layer]);
		twLFuncHist.setRange(FIT_MIN[layer], FIT_MAX[layer]);
		twLFunc.setParameter(0, fitLambda[LEFT]);
		twLFunc.setParameter(1, fitOrder[LEFT]);
		twLFunc.setParLimits(1, 0.45, 0.55);
		try {
			DataFitter.fit(twLFunc, twLGraph, "RN");
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		
		// Create copy of the function for display on the histogram
		twLFuncHist.setParameter(0, twLFunc.getParameter(0));
		twLFuncHist.setParameter(1, twLFunc.getParameter(1));
		twLFuncHist.setParameter(2, twLFunc.getParameter(2));

		F1D twRFunc = dataGroups.getItem(sector,layer,paddle).getF1D("trRightFunc");
		F1D twRFuncHist = dataGroups.getItem(sector,layer,paddle).getF1D("trRightFuncHist");
		twRFunc.setRange(FIT_MIN[layer], FIT_MAX[layer]);
		twRFuncHist.setRange(FIT_MIN[layer], FIT_MAX[layer]);
		twRFunc.setParameter(0, fitLambda[RIGHT]);
		twRFunc.setParameter(1, fitOrder[RIGHT]);
		twRFunc.setParLimits(1, 0.45, 0.55);
		try {
			if (paddle==20) {
				DataFitter.fit(twRFunc, twRGraph, "RN");
			}
			else {
				DataFitter.fit(twRFunc, twRGraph, "RN");
			}

		}
		catch (Exception e) {
			e.printStackTrace();
		}
		
		// Create copy of the function for display on the histogram
		twLFuncHist.setParameter(0, twLFunc.getParameter(0));
		twLFuncHist.setParameter(1, twLFunc.getParameter(1));
		twLFuncHist.setParameter(2, twLFunc.getParameter(2));
		
		// add the correct offset hist to the data group
		DataGroup dg = dataGroups.getItem(sector,layer,paddle);
		dg.addDataSet(offsetHists.getItem(sector,layer,paddle,offsetIdxLeft)[0], 2);
		dg.addDataSet(offsetHists.getItem(sector,layer,paddle,offsetIdxRight)[1], 3);
	}

	@Override
	public void customFit(int sector, int layer, int paddle){
		
		showOffsetHists(sector,layer,paddle);

		String[] fields = { "Override Lambda Left:", "Override Order Left:", "SPACE",
				"Override Lambda Right:", "Override Order Right:"};
		TOFCustomFitPanel panel = new TOFCustomFitPanel(fields,sector,layer);

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
			lambdaLeft = dataGroups.getItem(sector,layer,paddle).getF1D("trLeftFunc").getParameter(0);
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
			orderLeft = dataGroups.getItem(sector,layer,paddle).getF1D("trLeftFunc").getParameter(1);
		}
		return orderLeft;
	}    
	
	public Double getOffsetLeft(int sector, int layer, int paddle) {
		return dataGroups.getItem(sector,layer,paddle).getF1D("trLeftFunc").getParameter(2);
	}   

	public Double getLambdaRight(int sector, int layer, int paddle) {

		double lambdaRight = 0.0;
		double overrideVal = constants.getItem(sector, layer, paddle)[LAMBDA_RIGHT_OVERRIDE];

		if (overrideVal != UNDEFINED_OVERRIDE) {
			lambdaRight = overrideVal;
		}
		else {
			lambdaRight = dataGroups.getItem(sector,layer,paddle).getF1D("trRightFunc").getParameter(0);
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
			orderRight = dataGroups.getItem(sector,layer,paddle).getF1D("trRightFunc").getParameter(1);
		}
		return orderRight;
	}    

	public Double getOffsetRight(int sector, int layer, int paddle) {
		return dataGroups.getItem(sector,layer,paddle).getF1D("trRightFunc").getParameter(2);
	}   

	@Override
	public void saveRow(int sector, int layer, int paddle) {

		calib.setDoubleValue(getLambdaLeft(sector,layer,paddle),
				"tw0_left", sector, layer, paddle);
		calib.setDoubleValue(getOrderLeft(sector,layer,paddle),
				"tw1_left", sector, layer, paddle);
		calib.setDoubleValue(0.0,
				"tw2_left", sector, layer, paddle);
//		calib.setDoubleValue(getOffsetLeft(sector,layer,paddle),
//				"tw2_left", sector, layer, paddle);
		calib.setDoubleValue(getLambdaRight(sector,layer,paddle),
				"tw0_right", sector, layer, paddle);
		calib.setDoubleValue(getOrderRight(sector,layer,paddle),
				"tw1_right", sector, layer, paddle);
		calib.setDoubleValue(0.0,
				"tw2_right", sector, layer, paddle);
//		calib.setDoubleValue(getOffsetRight(sector,layer,paddle),
//				"tw2_right", sector, layer, paddle);

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

	//@Override
	public void drawPlotsGraphs(int sector, int layer, int paddle, EmbeddedCanvas canvas) {

		if (showPlotType == "TW_LEFT") {
			GraphErrors graph = dataGroups.getItem(sector,layer,paddle).getGraph("trLeftGraph");
			if (graph.getDataSize(0) != 0) {
				graph.setTitleX("");
				graph.setTitleY("");
				canvas.draw(graph);
				canvas.draw(dataGroups.getItem(sector,layer,paddle).getF1D("trLeftFunc"), "same");
			}
		}
		else {
			GraphErrors graph = dataGroups.getItem(sector,layer,paddle).getGraph("trRightGraph");
			if (graph.getDataSize(0) != 0) {
				graph.setTitleX("");
				graph.setTitleY("");
				canvas.draw(graph);
				canvas.draw(dataGroups.getItem(sector,layer,paddle).getF1D("trRightFunc"), "same");
			}
		}

	}

	@Override
	public void drawPlots(int sector, int layer, int paddle, EmbeddedCanvas canvas) {

		H2F hist = new H2F();
		F1D func = new F1D("trFunc");
		F1D smfunc = new F1D("trsmFunc");
		if (showPlotType == "TW_LEFT") { 
			hist = dataGroups.getItem(sector,layer,paddle).getH2F("offsetLeft");
			func = dataGroups.getItem(sector,layer,paddle).getF1D("trLeftFunc");
			smfunc = dataGroups.getItem(sector,layer,paddle).getF1D("smLeftFunc");
		}
		else {
			hist = dataGroups.getItem(sector,layer,paddle).getH2F("offsetRight");
			func = dataGroups.getItem(sector,layer,paddle).getF1D("trRightFunc");
			smfunc = dataGroups.getItem(sector,layer,paddle).getF1D("smRightFunc");
		}

		hist.setTitle("Paddle "+paddle);
		hist.setTitleX("");
		hist.setTitleY("");
		canvas.draw(hist);    
		canvas.draw(func, "same");
		canvas.draw(smfunc, "same");
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

	public void showOffsetHists(int sector, int layer, int component) {

		JFrame frame = new JFrame("Time walk offset histograms Sector "+sector+"Layer "+layer+" Component"+component);
		frame.setSize(1000, 800);

		JTabbedPane pane = new JTabbedPane();
		EmbeddedCanvas leftCanvas = new EmbeddedCanvas();
		leftCanvas.divide(4, NUM_OFFSET_HISTS/2);
		EmbeddedCanvas rightCanvas = new EmbeddedCanvas();
		rightCanvas.divide(4, NUM_OFFSET_HISTS/2);

		// create function for the nominal values
		//F1D nomFunc = new F1D("nomFunc", "([a]/(x^[b]))", FIT_MIN[layer], FIT_MAX[layer]);
		F1D nomFunc = new F1D("nomFunc", "(([a]/(x^[b]))-[c])", FIT_MIN[layer], FIT_MAX[layer]);
		nomFunc.setParLimits(0, 39.9, 40.1);
		nomFunc.setParLimits(1, 0.49, 0.51);
		if (layer==1) {
			nomFunc.setParLimits(2, 0.99, 1.01);
		}
		else {
			nomFunc.setParLimits(2, -0.01, 0.01);
		}
		nomFunc.setLineWidth(FUNC_LINE_WIDTH);
		nomFunc.setLineColor(FUNC_COLOUR);


		for (int i=0; i < NUM_OFFSET_HISTS; i++) {

			leftCanvas.cd(2*i);
			H2F leftHist = offsetHists.getItem(sector,layer,component,i)[0];
			leftCanvas.draw(leftHist);
			leftCanvas.draw(nomFunc, "same");

			leftCanvas.cd(2*i+1);
			GraphErrors leftGraph = leftHist.getProfileX();
			leftGraph.setMarkerSize(MARKER_SIZE);
			leftGraph.setLineThickness(MARKER_LINE_WIDTH);
			if (leftGraph.getDataSize(0) != 0) {
				DataFitter.fit(nomFunc, leftGraph, "RN");
				leftGraph.setTitle("Chi squared "+nomFunc.getChiSquare());
				System.out.println("Chi Squared "+i+nomFunc.getChiSquare());
				leftCanvas.draw(leftGraph);
				leftCanvas.draw(nomFunc, "same");
			}
			
			rightCanvas.cd(2*i);
			H2F rightHist = offsetHists.getItem(sector,layer,component,i)[1];
			rightCanvas.draw(rightHist);
			rightCanvas.draw(nomFunc, "same");

			rightCanvas.cd(2*i+1);
			GraphErrors rightGraph = rightHist.getProfileX();
			rightGraph.setMarkerSize(MARKER_SIZE);
			rightGraph.setLineThickness(MARKER_LINE_WIDTH);
			if (rightGraph.getDataSize(0) != 0) {
				DataFitter.fit(nomFunc, rightGraph, "RN");
				rightGraph.setTitle("Chi squared "+nomFunc.getChiSquare());
				rightCanvas.draw(rightGraph);
				rightCanvas.draw(nomFunc, "same");
				
			}
		}			

		pane.add("Time residual vs ADC (left)",leftCanvas);
		pane.add("Time residual vs ADC (right)",rightCanvas);
		frame.add(pane);
		// frame.pack();
		frame.setVisible(true);
		frame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);

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