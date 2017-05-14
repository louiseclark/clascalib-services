package org.jlab.calib.services;


import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
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
import org.jlab.groot.fitter.ParallelSliceFitter;
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

	// Slice fitter ranges
//	private final double[]        FIT_MIN = {0.0,  400.0, 1200.0, 400.0};
//	private final double[]        FIT_MAX = {0.0, 800.0, 2300.0, 800.0};
//	private final double[]        ADC_MIN = {0.0,  400.0, 1200.0, 400.0};
//	private final double[]        ADC_MAX = {0.0, 800.0, 2300.0, 800.0};
	
	// Preferred ranges
	private final double[]        FIT_MIN = {0.0,  200.0, 1200.0, 400.0};
	private final double[]        FIT_MAX = {0.0, 1000.0, 2400.0, 1000.0};
	private final double[]        ADC_MIN = {0.0, 150.0,  500.0,  150.0};
	private final double[]        ADC_MAX = {0.0, 2000.0, 3500.0, 2000.0};
	
	// Slice fitter bins
//	private int[] xbins = {0, 20, 20, 20};
	// Preferred bins
	private int[] xbins = {0, 80, 40, 80};
	private int ybins = 60;

	final double[] fitLambda = {40.0,40.0};  // default values for the constants
	final double[] fitOrder = {0.5,0.5};  // default values for the constants

	private String showPlotType = "TW_LEFT";

	private IndexedList<H2F[]> offsetHists = new IndexedList<H2F[]>(4);  // indexed by s,l,c, offset (in beam bucket multiples), H2F has {left, right}
	private final int NUM_OFFSET_HISTS = 20;
	
	private String fitOption = "RNQ";

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

	@Override
	public void populatePrevCalib() {

		System.out.println("Populating "+stepName+" previous calibration values");
		if (calDBSource==CAL_FILE) {

			System.out.println("File: "+prevCalFilename);
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
				System.out.println(
						"Unable to open file '" + 
								prevCalFilename + "'");
				return;
			}
			catch(IOException ex) {
				System.out.println(
						"Error reading file '" 
								+ prevCalFilename + "'"); 
				return;
			}			
		}
		else if (calDBSource==CAL_DEFAULT) {
			System.out.println("Default");
			for (int sector = 1; sector <= 6; sector++) {
				for (int layer = 1; layer <= 3; layer++) {
					int layer_index = layer - 1;
					for (int paddle = 1; paddle <= NUM_PADDLES[layer_index]; paddle++) {
						timeWalkValues.addEntry(sector, layer, paddle);
						timeWalkValues.setDoubleValue(fitLambda[0],
								"tw0_left", sector, layer, paddle);
						timeWalkValues.setDoubleValue(fitOrder[0],
								"tw1_left", sector, layer, paddle);
						timeWalkValues.setDoubleValue(fitLambda[1],
								"tw0_right", sector, layer, paddle);
						timeWalkValues.setDoubleValue(fitOrder[1],
								"tw1_right", sector, layer, paddle);
					}
				}
			}			
		}
		else if (calDBSource==CAL_DB) {
			System.out.println("Database Run No: "+prevCalRunNo);
			DatabaseConstantProvider dcp = new DatabaseConstantProvider(prevCalRunNo, "default");
			timeWalkValues = dcp.readConstants("/calibration/ftof/time_walk");
			dcp.disconnect();
		}
		prevCalRead = true;
		System.out.println(stepName+" previous calibration values populated successfully");
	}
	
	@Override
	public void resetEventListener() {
		
		// perform init processing
		
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
							xbins[layer], ADC_MIN[layer], ADC_MAX[layer],
							ybins, -1.5, 1.5);
					H2F rightHist = new H2F("trRightHist",
							"Time residual vs ADC RIGHT Sector "+sector+
							" Paddle "+paddle,
							xbins[layer], ADC_MIN[layer], ADC_MAX[layer],
							ybins, -1.5, 1.5);

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
					//F1D trLeftFunc = new F1D("trLeftFunc", "(([a]/(x^[b]))+[c])", FIT_MIN[layer], FIT_MAX[layer]);
					F1D trLeftFunc = new F1D("trLeftFunc", "(([a]/(x^[b]))-(40.0/(x^0.5))+[c])", FIT_MIN[layer], FIT_MAX[layer]);
					GraphErrors trLeftGraph = new GraphErrors();
					trLeftGraph.setName("trLeftGraph");                    
					//F1D trRightFunc = new F1D("trRightFunc", "(([a]/(x^[b]))+[c])", FIT_MIN[layer], FIT_MAX[layer]);
					F1D trRightFunc = new F1D("trRightFunc", "(([a]/(x^[b]))-(40.0/(x^0.5))+[c])", FIT_MIN[layer], FIT_MAX[layer]);
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

					dg.addDataSet(trLeftFunc, 4);
					dg.addDataSet(trLeftGraph, 4);
					dg.addDataSet(trRightFunc, 5);
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
								xbins[layer], ADC_MIN[layer], ADC_MAX[layer],
								ybins, -1.5, 1.5);

						H2F offRightHist = new H2F("offsetRight",
								"Time residual vs ADC Right Sector "+sector+
								" Paddle "+paddle+" Offset = "+offset+"+ ns",
								xbins[layer], ADC_MIN[layer], ADC_MAX[layer],
								ybins, -1.5, 1.5);

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
			if (paddle.goodTrackFound()) {
			
				dataGroups.getItem(sector,layer,component).getH2F("trLeftHist").fill(paddle.ADCL, paddle.deltaTLeft(0.0));
				dataGroups.getItem(sector,layer,component).getH2F("trRightHist").fill(paddle.ADCR, paddle.deltaTRight(0.0));
				
//				if (sector==2 && layer==1 && component==13) {
//					paddle.show();
//				}

				// fill the offset histograms
				for (int i=0; i<NUM_OFFSET_HISTS; i++) {
					double n = NUM_OFFSET_HISTS;
					double offset = i*(BEAM_BUCKET/n);
					offsetHists.getItem(sector,layer,component,i)[0].fill(paddle.ADCL, (paddle.deltaTLeft(offset)));
					offsetHists.getItem(sector,layer,component,i)[1].fill(paddle.ADCR, (paddle.deltaTRight(offset)));
				}
			}
		}
	}    
	
	@Override
	public void timerUpdate() {
		if (fitMethod!=0) {
			// only analyze at end of file for slice fitter - takes too long
			analyze();
		}
		save();
		calib.fireTableDataChanged();
	}

	@Override
	public void fit(int sector, int layer, int paddle, double minRange, double maxRange) {
		
		//System.out.println("TW fit SLC "+sector+layer+paddle);

//		H2F trLeftHist = dataGroups.getItem(sector,layer,paddle).getH2F("trLeftHist");
//		ArrayList<H1F> lSlices = trLeftHist.getSlicesX();
//		int lEntries = 0;
//		for (int i=0; i < trLeftHist.getXAxis().getNBins(); i++) {
//			lEntries = lEntries + lSlices.get(i).getEntries();
//		}
//		
//		H2F trRightHist = dataGroups.getItem(sector,layer,paddle).getH2F("trRightHist");
//		ArrayList<H1F> rSlices = trRightHist.getSlicesX();
//		int rEntries = 0;
//		for (int i=0; i < trRightHist.getXAxis().getNBins(); i++) {
//			rEntries = rEntries + rSlices.get(i).getEntries();
//		}
//		
//		boolean fitLeft = lEntries>100;
//		boolean fitRight = rEntries>100;
		
		double startChannelForFit = 0.0;
		double endChannelForFit = 0.0;
		if (minRange==UNDEFINED_OVERRIDE) {
			// default value
			startChannelForFit = FIT_MIN[layer];
		}
		else {
			// custom value
			startChannelForFit = minRange;
		}
		if (maxRange==UNDEFINED_OVERRIDE) {
			//default value
			endChannelForFit = FIT_MAX[layer];
		}
		else {
			// custom value
			endChannelForFit = maxRange;
		}		

		// Find the best offset hist
		
		int offsetIdxLeft = 0;
		int offsetIdxRight = 0;
		double minYmaxLeft = 9999.0;
		double minYmaxRight = 9999.0;

		for (int i=0; i<NUM_OFFSET_HISTS; i++) {
			
			// Get the max position of the y projection
			H1F yProjL = offsetHists.getItem(sector,layer,paddle,i)[0].projectionY();
			int ymaxBinL = yProjL.getMaximumBin();
			double ymaxL = yProjL.getxAxis().getBinCenter(ymaxBinL);
			
			H1F yProjR = offsetHists.getItem(sector,layer,paddle,i)[1].projectionY();
			int ymaxBinR = yProjR.getMaximumBin();
			double ymaxR = yProjR.getxAxis().getBinCenter(ymaxBinR);

			if (Math.abs(ymaxL) < minYmaxLeft) {
				minYmaxLeft = Math.abs(ymaxL);
				offsetIdxLeft = i;
			}

			if (Math.abs(ymaxR) < minYmaxRight) {
				minYmaxRight = Math.abs(ymaxR);
				offsetIdxRight = i;
			}
		}

		H2F twL = offsetHists.getItem(sector,layer,paddle,offsetIdxLeft)[0];
		H2F twR = offsetHists.getItem(sector,layer,paddle,offsetIdxRight)[1];

		GraphErrors twLGraph = (GraphErrors) dataGroups.getItem(sector, layer, paddle).getData("trLeftGraph"); 
		if (fitMethod==0 && sector==2) {
			ParallelSliceFitter psfL = new ParallelSliceFitter(twL);
			psfL.fitSlicesX();
			twLGraph.copy(fixGraph(psfL.getMeanSlices(),"trLeftGraph"));
		}
		else if (fitMethod==1) {
			twLGraph.copy(maxGraph(twL, "trLeftGraph"));
		}
		else {
			twLGraph.copy(twL.getProfileX());
		}

		GraphErrors twRGraph = (GraphErrors) dataGroups.getItem(sector, layer, paddle).getData("trRightGraph"); 
		if (fitMethod==0) {
			ParallelSliceFitter psfR = new ParallelSliceFitter(twR);
			psfR.fitSlicesX();
			twRGraph.copy(fixGraph(psfR.getMeanSlices(),"trRightGraph"));
		}
		else if (fitMethod==1) {
			twRGraph.copy(maxGraph(twR, "trRightGraph"));
		}
		else {
			twRGraph.copy(twR.getProfileX());
		}
			
		// fit function to the graph of means
		F1D twLFunc = dataGroups.getItem(sector,layer,paddle).getF1D("trLeftFunc");
		twLFunc.setRange(startChannelForFit, endChannelForFit);
		twLFunc.setParameter(0, fitLambda[LEFT]);
		twLFunc.setParLimits(0, 0.0, 200.0);
		twLFunc.setParameter(1, fitOrder[LEFT]);
		twLFunc.setParLimits(1, 0.45, 0.55);
		//if (fitLeft) {
			try {
				//System.out.println("Starting fit L "+sector+layer+paddle);
				DataFitter.fit(twLFunc, twLGraph, fitOption);
				//System.out.println("Completed fit L "+sector+layer+paddle);
			}
			catch (Exception e) {
				//System.out.println("Exception L "+sector+layer+paddle);
				e.printStackTrace();
			}
		//}
		
		F1D twRFunc = dataGroups.getItem(sector,layer,paddle).getF1D("trRightFunc");
		twRFunc.setRange(startChannelForFit, endChannelForFit);
		twRFunc.setParameter(0, fitLambda[RIGHT]);
		twRFunc.setParLimits(0, 0.0, 200.0);
		twRFunc.setParameter(1, fitOrder[RIGHT]);
		twRFunc.setParLimits(1, 0.45, 0.55);
		
		//if (fitRight) {
			try {
				//System.out.println("Starting fit R "+sector+layer+paddle);
				DataFitter.fit(twRFunc, twRGraph, fitOption);
				//System.out.println("Completed fit R "+sector+layer+paddle);
			}
			catch (Exception e) {
				//System.out.println("Exception R "+sector+layer+paddle);
				e.printStackTrace();
			}
		//}
		
		// add the correct offset hist to the data group
		DataGroup dg = dataGroups.getItem(sector,layer,paddle);
		dg.addDataSet(offsetHists.getItem(sector,layer,paddle,offsetIdxLeft)[0], 2);
		dg.addDataSet(offsetHists.getItem(sector,layer,paddle,offsetIdxRight)[1], 3);
	}
	
//	public GraphErrors maxGraph(H2F hist, String graphName) {
//		
//		ArrayList<H1F> slices = hist.getSlicesX();
//		int nBins = hist.getXAxis().getNBins();
//		double[] sliceMax = new double[nBins];
//		double[] maxErrs = new double[nBins];
//		double[] adcs = new double[nBins];
//		double[] adcErrs = new double[nBins];
//		
//		for (int i=0; i<nBins; i++) {
//			int maxBin = slices.get(i).getMaximumBin();
//			sliceMax[i] = slices.get(i).getxAxis().getBinCenter(maxBin);
//			maxErrs[i] = 0.1;
//			
//			adcs[i] = hist.getXAxis().getBinCenter(i);
//			adcErrs[i] = hist.getXAxis().getBinWidth(i)/2.0;
//		}
//		
//		GraphErrors maxGraph = new GraphErrors(graphName, adcs, sliceMax, adcErrs, maxErrs);
//		maxGraph.setName(graphName);
//		
//		return maxGraph;
//		
//	}
//	
//	public GraphErrors fixGraph(GraphErrors graphIn, String graphName) {
//		
//		int n = graphIn.getDataSize(0);
//		double[] x = new double[n];
//		double[] xerr = new double[n];
//		double[] y = new double[n];
//		double[] yerr = new double[n];
//		
//		for (int i=0; i<n; i++) {
//			
//			if (graphIn.getDataEY(i) < 0.3) {
//				x[i] = graphIn.getDataX(i);
//				xerr[i] = graphIn.getDataEX(i);
//				y[i] = graphIn.getDataY(i);
//				yerr[i] = graphIn.getDataEY(i);
//				
//			}
//		}
//		
//		GraphErrors fixGraph = new GraphErrors(graphName, x, y, xerr, yerr);
//		fixGraph.setName(graphName);
//		
//		return fixGraph;
//		
//	}	
	
	public void writeSlicesFile(int sector, int layer, int paddle) {

		try { 

			// Open the output file
			File outputFile = new File("slicefitterTest.txt");
			FileWriter outputFw = new FileWriter(outputFile.getAbsoluteFile());
			BufferedWriter outputBw = new BufferedWriter(outputFw);

			H2F twL = dataGroups.getItem(sector,layer,paddle).getH2F("offsetLeft");
			
			for (int i=0; i<twL.getXAxis().getNBins(); i++) {
				for (int j=0; j<twL.getYAxis().getNBins(); j++) {
					
					DecimalFormat df = new DecimalFormat("0.000");

					String line = new String();
					line = df.format(twL.getXAxis().getBinCenter(i)) + " " +
						   df.format(twL.getYAxis().getBinCenter(j)) + " " +
						   df.format(twL.getBinContent(i, j));
					outputBw.write(line);
					outputBw.newLine();
				}
			}

			outputBw.close();
		}
		catch(IOException ex) {
			System.out.println(
					"Error reading file '" 
							+ filename + "'");                   
			// Or we could just do this: 
			ex.printStackTrace();
		}

	}
	
	@Override
	public void customFit(int sector, int layer, int paddle){
		
		//showOffsetHists(sector,layer,paddle);
//		writeSlicesFile(sector,layer,paddle);
//		showSlices(sector,layer,paddle);

		String[] fields = {"Min range for fit:", "Max range for fit:", "SPACE", 
				"Override Lambda Left:", "Override Order Left:", "SPACE",
				"Override Lambda Right:", "Override Order Right:"};
		TOFCustomFitPanel panel = new TOFCustomFitPanel(fields,sector,layer);

		int result = JOptionPane.showConfirmDialog(null, panel, 
				"Adjust Fit / Override for paddle "+paddle, JOptionPane.OK_CANCEL_OPTION);
		if (result == JOptionPane.OK_OPTION) {

			double minRange = toDouble(panel.textFields[0].getText());
			double maxRange = toDouble(panel.textFields[1].getText());
			double overrideLL = toDouble(panel.textFields[2].getText());
			double overrideOL = toDouble(panel.textFields[3].getText());
			double overrideLR = toDouble(panel.textFields[4].getText());
			double overrideOR = toDouble(panel.textFields[5].getText());

			int minP = paddle;
			int maxP = paddle;
			if (panel.applyToAll) {
				minP = 1;
				maxP = NUM_PADDLES[layer-1];
			}
			
			for (int p=minP; p<=maxP; p++) {
				// save the override values
				Double[] consts = constants.getItem(sector, layer, p);
				consts[LAMBDA_LEFT_OVERRIDE] = overrideLL;
				consts[ORDER_LEFT_OVERRIDE] = overrideOL;
				consts[LAMBDA_RIGHT_OVERRIDE] = overrideLR;
				consts[ORDER_RIGHT_OVERRIDE] = overrideOR;
			
				fit(sector, layer, p, minRange, maxRange);

				// update the table
				saveRow(sector,layer,p);
			}
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
		//F1D smfunc = new F1D("trsmFunc");
		if (showPlotType == "TW_LEFT") { 
			hist = dataGroups.getItem(sector,layer,paddle).getH2F("offsetLeft");
			func = dataGroups.getItem(sector,layer,paddle).getF1D("trLeftFunc");
			//smfunc = dataGroups.getItem(sector,layer,paddle).getF1D("smLeftFunc");
		}
		else {
			hist = dataGroups.getItem(sector,layer,paddle).getH2F("offsetRight");
			func = dataGroups.getItem(sector,layer,paddle).getF1D("trRightFunc");
			//smfunc = dataGroups.getItem(sector,layer,paddle).getF1D("smRightFunc");
		}

		hist.setTitle("Paddle "+paddle);
		hist.setTitleX("");
		hist.setTitleY("");
		canvas.draw(hist);    
		canvas.draw(func, "same");
		//canvas.draw(smfunc, "same");
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
	
	public void showSlices(int sector, int layer, int component) {

		JFrame frame = new JFrame("Time walk slices Sector "+sector+"Layer "+layer+" Component"+component);
		frame.setSize(1000, 800);

		JTabbedPane pane = new JTabbedPane();
		EmbeddedCanvas leftCanvas = new EmbeddedCanvas();
		leftCanvas.divide(4, 4);

		H2F twL = dataGroups.getItem(sector,layer,component).getH2F("offsetLeft");
		
		ArrayList<H1F> lSlices = twL.getSlicesX();
		for (int i=0; i < twL.getXAxis().getNBins(); i++) {
			leftCanvas.cd(i);
			leftCanvas.draw(lSlices.get(i));
		}
		
		pane.add("Left slices",leftCanvas);
		frame.add(pane);
		// frame.pack();
		frame.setVisible(true);
		frame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);

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
		//F1D nomFunc = new F1D("nomFunc", "(([a]/(x^[b]))-(40.0/(x^0.5))+[c])", FIT_MIN[layer], FIT_MAX[layer]);
		F1D nomFunc = new F1D("nomFunc", "(([a]/(x^[b]))-(40.0/(x^0.5)))", FIT_MIN[layer], FIT_MAX[layer]);
		nomFunc.setParLimits(0, 39.9, 40.1);
		nomFunc.setParLimits(1, 0.49, 0.51);
		//nomFunc.setParLimits(2, 0.99, 1.01);

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
				try {
					DataFitter.fit(nomFunc, leftGraph, fitOption);
				}
				catch (Exception e) {
					e.printStackTrace();
				}

				leftGraph.setTitle("Chi squared "+nomFunc.getChiSquare());
				//System.out.println("Chi Squared "+i+nomFunc.getChiSquare());
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
				try {
					DataFitter.fit(nomFunc, rightGraph, fitOption);
				}
				catch (Exception e) {
					e.printStackTrace();
				}

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