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

	public final int NUM_ITERATIONS = 5;
	private boolean doTimewalk = true;

	public boolean calibChallTest = true;

	private List<TOFPaddle>     allPaddleList = new ArrayList<TOFPaddle>();

	// constants for indexing the histograms
	public final int LEFT = 0;
	public final int RIGHT = 1;

	// indices for constants
	public final int LAMBDA_LEFT_OVERRIDE = 0;
	public final int ORDER_LEFT_OVERRIDE = 1;
	public final int LAMBDA_RIGHT_OVERRIDE = 2;
	public final int ORDER_RIGHT_OVERRIDE = 3;


	private final double[]		ADC_MIN = {0.0, 500.0, 1200.0, 500.0};
	private final double[]		ADC_MAX = {0.0,	1500.0,	3000.0,	1500.0};
	private final double[]		FIT_MIN = {0.0,	600.0,	1500.0, 600.0};
	private final double[]		FIT_MAX = {0.0, 1000.0,	2500.0, 1000.0};

	final double[] fitLambda = {40.0,40.0};  // default values for the constants
	final double[] fitOrder = {0.5,0.5};  // default values for the constants

	private String showPlotType = "TW_LEFT";

	private IndexedList<H2F[]> testHists = new IndexedList<H2F[]>(4);
	private IndexedList<H2F> testAdcHists = new IndexedList<H2F>(3);
	private IndexedList<GraphErrors[]> testGraphs = new IndexedList<GraphErrors[]>(4);
	private IndexedList<F1D[]> testFuncs = new IndexedList<F1D[]>(4);
	private IndexedList<String> testResultsBefore = new IndexedList<String>(4);
	private IndexedList<String> testResultsAfter = new IndexedList<String>(4);

	private IndexedList<H2F> test3DHists = new IndexedList<H2F>(3);
	private IndexedList<H1F> allResHists = new IndexedList<H1F>(3);
	
	// test paddles (show all iterations)
//	int testSectors[] =  {1, 1, 1, 2, 2, 2};
//	int testLayers[] =   {1, 2, 2, 1, 2, 2};
//	int testPaddles[] = {11,33,51,19,24,53};
	int testSectors[] =  {1, 3, 3, 2, 4, 4};
	int testLayers[] =   {1, 2, 2, 1, 2, 2};
	int testPaddles[] = {11,43,51,19,23,37};

	
	// calib challenge Dec paddles
//	int testSectors[] =  {1, 1, 1, 1, 1, 1};
//	int testLayers[] =   {1, 1, 2, 2, 2, 2};
//	int testPaddles[] = {11,21,12,14,19,22};

	// Output histogram data for one paddle
	private BufferedWriter testBw;

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

		// get the effective velocity constants
		//		DatabaseConstantProvider  dbprovider = new DatabaseConstantProvider(10,"default");
		//		dbprovider.loadTable("/calibration/ftof/effective_velocity");
		//		dbprovider.disconnect();
		//		dbprovider.show();
		//		
		//		for(int loop = 0; loop < dbprovider.length("/calibration/ftof/effective_velocity/veff_left"); loop++){
		//	        double value = dbprovider.getDouble("/calibration/ftof/effective_velocity/veff_left",loop);
		//	        // for integer values use dbprovider.getInteger("/calibration/ftof/attenuation/y_offset",loop);
		//	        System.out.println("loop "+loop+" value "+value);
		//		}
		//		
		//		DetectorDescriptor desc = new DetectorDescriptor();
		//		desc.setSectorLayerComponent(1, 1, 4);
		//		System.out.println("desc "+desc);

		if (TOFCalibrationEngine.calDBSource==TOFCalibrationEngine.CAL_FILE) {
			// read in the time walk values from the text file
			//String inputFile = "/home/louise/workspace/clascalib-services/ftof.time_walk.smeared.txt";
			String inputFile = "/home/louise/workspace/clascalib-services/ftof_tw_smeared_new_formatted.txt";

			String line = null;
			try { 

				// Open the file
				FileReader fileReader = 
						new FileReader(inputFile);

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

					double[] twValues = {lamL,ordL,lamR,ordR};
					
					System.out.println("SLC "+sector+layer+paddle+" lamL "+lamL+" ordL "+ordL+" lamR "+lamR+" ordR "+ordR);

					timeWalkValues.add(twValues, sector, layer, paddle);

					line = bufferedReader.readLine();
				}    

				bufferedReader.close();            
			}
			catch(FileNotFoundException ex) {
				ex.printStackTrace();
				System.out.println(
						"Unable to open file '" + 
								inputFile + "'");                
			}
			catch(IOException ex) {
				System.out.println(
						"Error reading file '" 
								+ inputFile + "'");                   
				// Or we could just do this: 
				// ex.printStackTrace();
			}
		}
	}

	@Override
	public void timerUpdate() {
		// only analyze at end of events for timewalk
//		save();
//		calib.fireTableDataChanged();
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

		if (calibChallTest) {
			createTestHists();
		}

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
							100, -2.0, 10.0);
					H2F rightHist = new H2F("trRightHist",
							"Time residual vs ADC RIGHT Sector "+sector+
							" Paddle "+paddle,
							100, 0.0, ADC_MAX[layer],
							100, -2.0, 10.0);

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
					//F1D trLeftFunc = new F1D("trLeftFunc", "([b]/(x^0.5))+[a]", FIT_MIN[layer], FIT_MAX[layer]);
					F1D trLeftFunc = new F1D("trLeftFunc", "([b]/(x^[c]))", FIT_MIN[layer], FIT_MAX[layer]);
					GraphErrors trLeftGraph = new GraphErrors();
					trLeftGraph.setName("trLeftGraph");					
					//F1D trRightFunc = new F1D("trRightFunc", "([b]/(x^0.5))+[a]", FIT_MIN[layer], FIT_MAX[layer]);
					F1D trRightFunc = new F1D("trRightFunc", "([b]/(x^[c]))", FIT_MIN[layer], FIT_MAX[layer]);
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

					if (calibChallTest) {
						// ************  TEST ***************
						// create functions for the actual smeared values
						F1D smearedLeftFunc = new F1D("smLeftFunc", "[a]+([b]/(x^[c]))", ADC_MIN[layer], ADC_MAX[layer]);
						F1D smearedRightFunc = new F1D("smRightFunc", "[a]+([b]/(x^[c]))", ADC_MIN[layer], ADC_MAX[layer]);

						smearedLeftFunc.setParameter(0, 0.0);
						smearedLeftFunc.setParameter(1, 		
								TOFCalibrationEngine.timeWalkValues.getItem(sector,layer,paddle)[0]/2.0);
						smearedLeftFunc.setParameter(2, 		
								TOFCalibrationEngine.timeWalkValues.getItem(sector,layer,paddle)[1]);

						smearedRightFunc.setParameter(0, 0.0);
						smearedRightFunc.setParameter(1, 		
								TOFCalibrationEngine.timeWalkValues.getItem(sector,layer,paddle)[2]/2.0);
						smearedRightFunc.setParameter(2, 		
								TOFCalibrationEngine.timeWalkValues.getItem(sector,layer,paddle)[3]);

						//trLeftFunc.setLineColor(FUNC_COLOUR);
						smearedLeftFunc.setLineWidth(FUNC_LINE_WIDTH);
						///trRightFunc.setLineColor(FUNC_COLOUR);
						smearedRightFunc.setLineWidth(FUNC_LINE_WIDTH);

						if (calibChallTest) {
							dg.addDataSet(smearedLeftFunc, 2);
							dg.addDataSet(smearedRightFunc, 3);
						}
					}

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

	public void createTestHists() {

		for (int i=0; i < testPaddles.length; i++) {

			int sector = testSectors[i];
			int layer = testLayers[i];
			int paddle = testPaddles[i];

			H2F adcHist = new H2F("adcHist",
					"ADC RIGHT vs ADC LEFT Layer "+layer+
					" Paddle "+paddle,
					100, 0.0, 4000.0, //ADC_MAX[layer],
					100, 0.0, 4000.0); //ADC_MAX[layer]);
			testAdcHists.add(adcHist, sector,layer,paddle);
			
			H2F test3DHist = new H2F("test3DHist",
					"Time residual vs ADC RIGHT vs ADC LEFT Layer "+layer+
					" Paddle "+paddle,
					100, 0.0, 4000.0, //ADC_MAX[layer],
					100, 0.0, 4000.0); //ADC_MAX[layer]);
			test3DHists.add(test3DHist, sector,layer,paddle);

			H1F allResHist = new H1F("allResHist",
					"Time residual Layer "+layer+
					" Paddle "+paddle,
					100, -50.0, 50.0); //ADC_MAX[layer]);
			allResHists.add(allResHist, sector,layer,paddle);

			
			for (int iter=0; iter<NUM_ITERATIONS; iter++) {

				// create all the histograms
				H2F leftHist = new H2F("trLeftHist",
						"Time residual vs ADC LEFT Layer "+layer+
						" Paddle "+paddle +" Iteration "+iter,
						100, 0.0, ADC_MAX[layer],
						100, -2.0, 10.0);
				H2F rightHist = new H2F("trRightHist",
						"Time residual vs ADC RIGHT Layer "+layer+
						" Paddle "+paddle+" Iteration "+iter,
						100, 0.0, ADC_MAX[layer],
						100, -2.0, 10.0);

				leftHist.setTitleX("ADC LEFT");
				leftHist.setTitleY("Time residual (ns)");
				rightHist.setTitleX("ADC RIGHT");
				rightHist.setTitleY("Time residual (ns)");

				// create all the functions and graphs
				//F1D trLeftFunc = new F1D("trLeftFunc", "([b]/(x^0.5))+[a]", FIT_MIN[layer], FIT_MAX[layer]);
				F1D trLeftFunc = new F1D("trLeftFunc", "([b]/(x^[c]))", FIT_MIN[layer], FIT_MAX[layer]);
				GraphErrors trLeftGraph = new GraphErrors();
				trLeftGraph.setName("trLeftGraph");					
				//F1D trRightFunc = new F1D("trRightFunc", "([b]/(x^0.5))+[a]", FIT_MIN[layer], FIT_MAX[layer]);
				F1D trRightFunc = new F1D("trRightFunc", "([b]/(x^[c]))", FIT_MIN[layer], FIT_MAX[layer]);
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

				H2F[] hists = {leftHist, rightHist};
				testHists.add(hists, sector,layer,paddle,iter);
				testHists.getItem(sector,layer,paddle,iter)[0].setTitle("Test");

				GraphErrors[] graphs = {trLeftGraph, trRightGraph};
				testGraphs.add(graphs, sector,layer,paddle,iter);

				F1D[] funcs = {trLeftFunc, trRightFunc};
				testFuncs.add(funcs, sector,layer,paddle,iter);

			}
		}

		// open test file
		try {
			File outputFile = new File("timeWalkTestSLC1111.txt");
			FileWriter outputFw = new FileWriter(outputFile.getAbsoluteFile());
			testBw = new BufferedWriter(outputFw);
			testBw.write("timeDiffTdc timeDiffTracking yPos ADCL ADCR");
			testBw.newLine();
		}
		catch (IOException ex) {
			ex.printStackTrace();
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

		// store the paddle so we can iterate through them
		for (TOFPaddle paddle : paddleList) {

			allPaddleList.add(paddle);
			
//			int sector = paddle.getDescriptor().getSector();
//			int layer = paddle.getDescriptor().getLayer();
//			int component = paddle.getDescriptor().getComponent();
//			double [] tr = paddle.timeResiduals(getLambdas(sector,layer,component,0), 
//					getOrders(sector,layer,component,0),0);
//			
//			if (paddle.ADCL>500 && paddle.ADCR > 500) {
//				dataGroups.getItem(sector,layer,component).getH2F("trLeftHist").fill(paddle.ADCL, tr[LEFT]);
//				dataGroups.getItem(sector,layer,component).getH2F("trRightHist").fill(paddle.ADCR, tr[RIGHT]);
//			}
//			
//			if (calibChallTest) {
//				allPaddleList.add(paddle);
//			}
//			// just one paddle for testing
//			else if (paddle.getDescriptor().getSector()==2 && 
//					paddle.getDescriptor().getLayer()==2 &&
//					paddle.getDescriptor().getComponent()==32) {
//
//				//			if (doTimewalk) {
//				allPaddleList.add(paddle);
//				//				if (allPaddleList.size() > 1000000) {
//				//					doTimewalk = false;
//				//					analyze();
//				//				}
//			}
		}
	}	


	// non-standard analyze step for timewalk
	// require to iterate through all events several times
	@Override
	public void analyze() {

		for (int iter=0; iter<NUM_ITERATIONS; iter++) {

			if (calibChallTest) {
				writeTestValues(iter,"Before");
			}

			System.out.println("Iteration "+iter+" start");

			resetHists();
			System.out.println("Iteration "+iter+"middle");
			fitAll(iter);

			System.out.println("Iteration "+iter+" end");
			
			if (calibChallTest) {
				writeTestValues(iter,"After");
			}

		}
		save();
		calib.fireTableDataChanged();
		
		if (calibChallTest) {
			writeTestResults();
		}
	}

	public void writeTestValues(int iter, String stage) {

		if (!calibChallTest) return;

		for (int i=0; i < testPaddles.length; i++) {

			int sector = testSectors[i];
			int layer = testLayers[i];
			int paddle = testPaddles[i];

			if (stage == "Before") {
				testResultsBefore.add("Iteration "+iter+" "+stage+"\n "+
						"SLC "+sector+layer+paddle+"\n"+
						" Lambda left is "+getLambdaLeft(sector,layer,paddle)+"\n"+
						" Order left is "+getOrderLeft(sector,layer,paddle)+"\n"+
						" Lambda right is "+getLambdaRight(sector,layer,paddle)+"\n"+
						" Order right is "+getOrderRight(sector,layer,paddle)+"\n"
						, sector,layer,paddle,iter);
			}
			else {
				testResultsAfter.add("Iteration "+iter+" "+stage+"\n "+
						"SLC "+sector+layer+paddle+"\n"+
						" Lambda left is "+getLambdaLeft(sector,layer,paddle)+"\n"+
						" Order left is "+getOrderLeft(sector,layer,paddle)+"\n"+
						" Lambda right is "+getLambdaRight(sector,layer,paddle)+"\n"+
						" Order right is "+getOrderRight(sector,layer,paddle)+"\n"
						, sector,layer,paddle,iter);

			}
		}
	}

	public void writeTestResults() {

		if (!calibChallTest) return;

		JFrame frame = new JFrame("Time walk test results");
		frame.setSize(1000, 800);

		JTabbedPane pane = new JTabbedPane();
		EmbeddedCanvas adcCanvas;
		adcCanvas = new EmbeddedCanvas();
		adcCanvas.divide(2, 3);
		EmbeddedCanvas test3DCanvas;
		test3DCanvas = new EmbeddedCanvas();
		test3DCanvas.divide(2, 3);
		EmbeddedCanvas testResCanvas;
		testResCanvas = new EmbeddedCanvas();
		testResCanvas.divide(2, 3);

		for (int i=0; i < testPaddles.length; i++) {

			int sector = testSectors[i];
			int layer = testLayers[i];
			int paddle = testPaddles[i];

			adcCanvas.cd(i);
			adcCanvas.draw(testAdcHists.getItem(sector,layer,paddle));
			test3DCanvas.cd(i);
			test3DCanvas.draw(test3DHists.getItem(sector,layer,paddle));
			testResCanvas.cd(i);
			testResCanvas.draw(allResHists.getItem(sector,layer,paddle));			

			// ************  TEST ***************
			// create functions for the actual smeared values
			F1D smearedLeftFunc = new F1D("smLeftFunc", "[a]+([b]/(x^[c]))", ADC_MIN[layer], ADC_MAX[layer]);
			F1D smearedRightFunc = new F1D("smRightFunc", "[a]+([b]/(x^[c]))", ADC_MIN[layer], ADC_MAX[layer]);

			smearedLeftFunc.setParameter(0, 0.0);
			smearedLeftFunc.setParameter(1, 		
					TOFCalibrationEngine.timeWalkValues.getItem(sector,layer,paddle)[0]/2.0);
			smearedLeftFunc.setParameter(2, 		
					TOFCalibrationEngine.timeWalkValues.getItem(sector,layer,paddle)[1]);

			smearedRightFunc.setParameter(0, 0.0);
			smearedRightFunc.setParameter(1, 		
					TOFCalibrationEngine.timeWalkValues.getItem(sector,layer,paddle)[2]/2.0);
			smearedRightFunc.setParameter(2, 		
					TOFCalibrationEngine.timeWalkValues.getItem(sector,layer,paddle)[3]);

			//trLeftFunc.setLineColor(FUNC_COLOUR);
			smearedLeftFunc.setLineWidth(FUNC_LINE_WIDTH);
			///trRightFunc.setLineColor(FUNC_COLOUR);
			smearedRightFunc.setLineWidth(FUNC_LINE_WIDTH);


			EmbeddedCanvas resultCanvas;
			resultCanvas = new EmbeddedCanvas();
			resultCanvas.divide(4, NUM_ITERATIONS);

			for (int iter=0; iter<NUM_ITERATIONS; iter++) {

				resultCanvas.cd(4*iter);
				resultCanvas.draw(testHists.getItem(sector,layer,paddle,iter)[0]);

				resultCanvas.cd((4*iter)+1);
				resultCanvas.draw(testGraphs.getItem(sector,layer,paddle,iter)[0]);						
				resultCanvas.draw(testFuncs.getItem(sector,layer,paddle,iter)[0],"same");
				resultCanvas.draw(smearedLeftFunc,"same");

				resultCanvas.cd((4*iter)+2);
				resultCanvas.draw(testHists.getItem(sector,layer,paddle,iter)[1]);

				resultCanvas.cd((4*iter)+3);
				resultCanvas.draw(testGraphs.getItem(sector,layer,paddle,iter)[1]);
				resultCanvas.draw(testFuncs.getItem(sector,layer,paddle,iter)[1],"same");
				resultCanvas.draw(smearedRightFunc,"same");

				System.out.println(testResultsBefore.getItem(sector,layer,paddle,iter));
				System.out.println(testResultsAfter.getItem(sector,layer,paddle,iter));

			}
			pane.add(
					"SLC "+sector+layer+paddle,
					resultCanvas);

		}

		pane.add("ADC",adcCanvas);
		pane.add("Time residual vs ADCL vs ADCR",test3DCanvas);
		pane.add("Time residual",testResCanvas);
		frame.add(pane);
		// frame.pack();
		frame.setVisible(true);
		frame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);

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
							getOrders(sector,layer,component,iter),iter);

					// output test data
					if (sector==2 && layer==1 && component==19 && iter==0) {
						double timeDiff = (paddle.tdcToTime(paddle.TDCL) -
								paddle.tdcToTime(paddle.TDCR) -
								paddle.leftRightAdjustment(sector, layer, component)) / 2.0;
						double timeDiffTracking = (paddle.paddleY() / paddle.veff());

						try {
							testBw.write(timeDiff+" "+timeDiffTracking+" "+paddle.paddleY()+" "+paddle.ADCL+" "+paddle.ADCR);
							testBw.newLine();
						} 
						catch (IOException e) {
							e.printStackTrace();
						}
					}

					// use only high ADCR for left plot
					//if (paddle.ADCR >1000) {
					dataGroups.getItem(sector,layer,component).getH2F("trLeftHist").fill(paddle.ADCL, tr[LEFT]);
					//}
					// use only high ADCL for right plot
					//if (paddle.ADCL >1000) {
					dataGroups.getItem(sector,layer,component).getH2F("trRightHist").fill(paddle.ADCR, tr[RIGHT]);
					//}

					if (calibChallTest && iter==0) {
						boolean useThisPaddle = false;

						for (int i=0; i<testPaddles.length; i++) {
							if (sector==testSectors[i] && layer==testLayers[i] && component==testPaddles[i]) {
								useThisPaddle = true;
							}
						}
						if (useThisPaddle) {
							H2F test3DHist = test3DHists.getItem(sector,layer,component);
							H2F adcHist = testAdcHists.getItem(sector,layer,component);
							H1F allResHist = allResHists.getItem(sector,layer,component);
							int binL = test3DHist.getXAxis().getBin(paddle.ADCL);
							int binR = test3DHist.getYAxis().getBin(paddle.ADCR);
							double av = test3DHist.getBinContent(binL, binR);
							double n = adcHist.getBinContent(binL,binR);
							double combRes = paddle.combinedRes();
							
							double newAv = (av*n + combRes) / (n+1);
							
							//if (combRes<2.0 && combRes>-2.0) {
//							if (paddle.ADCL > FIT_MIN[layer] &&
//								paddle.ADCL < FIT_MAX[layer] &&
//								paddle.ADCR > FIT_MIN[layer] &&
//								paddle.ADCR < FIT_MAX[layer]) {
							if (paddle.ADCL > 300 &&
							    paddle.ADCR > 300) {
								
//								System.out.println("SLC "+sector+layer+component+
//										   "ADCL "+paddle.ADCL+" ADCR "+paddle.ADCR+" binL "+binL+" binR "+binR+
//										   " combinedRes "+combRes+
//										   " av "+av+" n "+n+" newAv "+newAv);
								
								test3DHist.setBinContent(binL, binR, newAv);
//								System.out.println("binContent "+test3DHist.getBinContent(binL, binR));
								adcHist.fill(paddle.ADCL, paddle.ADCR);
								allResHist.fill(combRes);
								
							}
														
						}
					}
				}
			}

		}

		// close the test file
		try {
			testBw.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		// Now do the fits for this iteration
		for (int sector = 1; sector <= 6; sector++) {
			for (int layer = 1; layer <= 3; layer++) {
				int layer_index = layer - 1;
				for (int paddle = 1; paddle <= NUM_PADDLES[layer_index]; paddle++) {

					fit(sector,layer,paddle,0.0,0.0);

					if(calibChallTest) {
						saveTestResults(sector,layer,paddle,iter);
					}
					//					System.out.println("SLC "+sector+layer+paddle+" Lambda left is "+getLambdaLeft(sector,layer,paddle));
					//					System.out.println("SLC "+sector+layer+paddle+" Lambda right is "+getLambdaRight(sector,layer,paddle));
					//					System.out.println("SLC "+sector+layer+paddle+" Order left is "+getOrderLeft(sector,layer,paddle));
					//					System.out.println("SLC "+sector+layer+paddle+" Order right is "+getOrderRight(sector,layer,paddle));

				}
			}
		}
	}

	public void saveTestResults(int sector, int layer, int paddle, int iter) {

		boolean useThisPaddle = false;

		for (int i=0; i<testPaddles.length; i++) {
			if (sector==testSectors[i] && layer==testLayers[i] && paddle==testPaddles[i]) {
				useThisPaddle = true;
			}
		}
		if (useThisPaddle) {

			//H2F twL = testHists.getItem(sector,layer,paddle,iter)[0];
			H2F twL = dataGroups.getItem(sector,layer,paddle).getH2F("trLeftHist").histClone("trLeftHistCopy");
			//H2F twR = testHists.getItem(sector,layer,paddle,iter)[1];
			H2F twR = dataGroups.getItem(sector,layer,paddle).getH2F("trRightHist").histClone("trRightHistCopy");
			H2F[] hists = {twL,twR};
			testHists.add(hists, sector,layer,paddle,iter);

			F1D twfL = testFuncs.getItem(sector,layer,paddle,iter)[0];
			twfL.setParameter(0, dataGroups.getItem(sector,layer,paddle).getF1D("trLeftFunc").getParameter(0));
			twfL.setParameter(1, dataGroups.getItem(sector,layer,paddle).getF1D("trLeftFunc").getParameter(1));		
			F1D twfR = testFuncs.getItem(sector,layer,paddle,iter)[1];
			twfR.setParameter(0, dataGroups.getItem(sector,layer,paddle).getF1D("trRightFunc").getParameter(0));
			twfR.setParameter(1, dataGroups.getItem(sector,layer,paddle).getF1D("trRightFunc").getParameter(1));

			GraphErrors twgL = testGraphs.getItem(sector,layer,paddle,iter)[0];
			twgL.copy((GraphErrors) dataGroups.getItem(sector, layer, paddle).getData("trLeftGraph"));
			GraphErrors twgR = testGraphs.getItem(sector,layer,paddle,iter)[1];
			twgR.copy((GraphErrors) dataGroups.getItem(sector, layer, paddle).getData("trRightGraph"));

		}
	}

	@Override
	public void fit(int sector, int layer, int paddle, double minRange, double maxRange) {

		System.out.println("Fitting "+sector+layer+paddle+" start");

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
		//twLFunc.setParameter(0, 1.0);
		//twLFunc.setParameter(1, fitLambda[LEFT]/2.0);
		//twLFunc.setParameter(2, fitOrder[LEFT]);
		twLFunc.setParameter(0, fitLambda[LEFT]/2.0);
		twLFunc.setParameter(1, fitOrder[LEFT]);
		//twLFunc.setParameter(1, 5.0);
		//twLFunc.setParLimits(1, 0.2, 0.85);
		//twLFunc.setParLimits(0, 20, 100);
		twLFunc.setParLimits(1, 0.4, 0.6);
		try {
			DataFitter.fit(twLFunc, twLGraph, "RN");
		}
		catch (Exception e) {
			e.printStackTrace();
		}

		F1D twRFunc = dataGroups.getItem(sector,layer,paddle).getF1D("trRightFunc");
		twRFunc.setRange(FIT_MIN[layer], FIT_MAX[layer]);
		//		twRFunc.setParameter(0, 1.0);
		//		twRFunc.setParameter(1, fitLambda[RIGHT]/2.0);
		//		twRFunc.setParameter(2, fitOrder[RIGHT]);
		twRFunc.setParameter(0, fitLambda[RIGHT]/2.0);
		twRFunc.setParameter(1, fitOrder[RIGHT]);
		//twRFunc.setParameter(1, 5.0);
		//twRFunc.setParLimits(1, 0.2, 0.85);
		//twRFunc.setParLimits(0, 20, 100);
		twRFunc.setParLimits(1, 0.4, 0.6);
		//twRFunc.setParLimits(1, 0.4999, 0.5001);
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
		//System.out.println("Fitting "+sector+layer+paddle+" end");
	}

	public double[] getLambdas(int sector, int layer, int paddle, int iter) {
		// leave as default for first iteration
		// take values from function for subsequent iterations
		double[] lambdas = {40.0, 40.0}; 
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
			lambdaLeft = dataGroups.getItem(sector,layer,paddle).getF1D("trLeftFunc").getParameter(0) * 2.0;
			//lambdaLeft = dataGroups.getItem(sector,layer,paddle).getF1D("trLeftFunc").getParameter(0);
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
			//orderLeft = dataGroups.getItem(sector,layer,paddle).getF1D("trLeftFunc").getParameter(2);
			orderLeft = dataGroups.getItem(sector,layer,paddle).getF1D("trLeftFunc").getParameter(1);
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
			lambdaRight = dataGroups.getItem(sector,layer,paddle).getF1D("trRightFunc").getParameter(0) * 2.0;
			//lambdaRight = dataGroups.getItem(sector,layer,paddle).getF1D("trRightFunc").getParameter(0);
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
			//orderRight = dataGroups.getItem(sector,layer,paddle).getF1D("trRightFunc").getParameter(2);
			orderRight = dataGroups.getItem(sector,layer,paddle).getF1D("trRightFunc").getParameter(1);
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
