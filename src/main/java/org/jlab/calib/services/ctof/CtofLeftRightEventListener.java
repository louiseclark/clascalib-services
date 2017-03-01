package org.jlab.calib.services.ctof;


import java.awt.BorderLayout;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeMap;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSplitPane;

import org.jlab.calib.services.TOFCalibrationEngine;
import org.jlab.calib.services.TOFCustomFitPanel;
import org.jlab.calib.services.TOFPaddle;
import org.jlab.detector.base.DetectorType;
import org.jlab.detector.calib.tasks.CalibrationEngine;
import org.jlab.detector.calib.utils.CalibrationConstants;
import org.jlab.detector.calib.utils.CalibrationConstantsListener;
import org.jlab.detector.calib.utils.CalibrationConstantsView;
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
import org.jlab.groot.ui.TCanvas;
import org.jlab.io.base.DataEvent;
import org.jlab.io.base.DataEventType;
import org.jlab.io.evio.EvioDataEvent;
import org.jlab.io.task.DataSourceProcessorPane;
import org.jlab.io.task.IDataEventListener;
import org.jlab.utils.groups.IndexedList;

public class CtofLeftRightEventListener extends CTOFCalibrationEngine {

	// indices for override values
	public final int LEFTRIGHT_OVERRIDE = 0;

	final double LEFT_RIGHT_RATIO = 0.6;
	final double MAX_LEFTRIGHT = 10.0;

	//final double REF_OFFSET = 6.0;

	public H1F allPaddlePos = new H1F("allPaddlePos","allPaddlePos", 200, -200, 200);

	public CtofLeftRightEventListener() {

		stepName = "Left Right";
		fileNamePrefix = "CTOF_CALIB_LEFTRIGHT_";
		// get file name here so that each timer update overwrites it
		filename = nextFileName();

		calib = new CalibrationConstants(3,
				"left_right/F:paddle2paddle/F");
		calib.setName("/calibration/ctof/timing_offset");

		calib.addConstraint(3, -MAX_LEFTRIGHT, MAX_LEFTRIGHT);

		if (CTOFCalibrationEngine.calDBSource==CTOFCalibrationEngine.CAL_FILE) {

			// read in the left right values from the text file
			//String inputFile = "/home/louise/workspace/clascalib-services/CTOF_CALIB_LEFTRIGHT_15_files.txt";
			String inputFile = "test.txt";

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

					int sector = Integer.parseInt(line.substring(0, 3).trim());
					int layer = Integer.parseInt(line.substring(3, 7).trim());
					int paddle = Integer.parseInt(line.substring(7, 11).trim());
					double lr = Double.parseDouble(line.substring(11,27).trim());
					//double lr = Double.parseDouble(line.substring(11).trim());

					leftRightValues.add(lr, sector, layer, paddle);

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

	public void resetEventListener() {

		// LC perform init processing
		for (int paddle = 1; paddle <= NUM_PADDLES[0]; paddle++) {

			// create all the histograms
			H1F hist = new H1F("left_right","Left Right: Paddle "+paddle, 
					2001, -50.05, 50.05);

			hist.setTitle("Left Right  : " 
					+ " Paddle "+paddle);

			// create all the functions
			F1D edgeToEdgeFunc = new F1D("edgeToEdgeFunc","[height]",
					-10.0, 10.0);

			DataGroup dg = new DataGroup(1,1);
			dg.addDataSet(hist, 0);
			//dg.addDataSet(edgeToEdgeFunc, 0);
			dataGroups.add(dg, 1,1,paddle);

			// initialize the constants array
			Double[] consts = {UNDEFINED_OVERRIDE};
			// override value
			constants.add(consts, 1, 1, paddle);

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

			//if ((paddle.zPosCTOF() > 35.0) && (paddle.zPosCTOF() < 40.0)) {
			//if (component!=1) {
			dataGroups.getItem(sector,layer,component).getH1F("left_right").fill(
					paddle.leftRight());
			//}
			// TEST CODE
			// use paddle 1 for all paddles
			//				dataGroups.getItem(1,1,1).getH1F("left_right").fill(
			//						paddle.leftRight());
			//}

			// Plot the positions
			//			if (paddle.ZPOS != 0) {
			//				allPaddlePos.fill(paddle.zPosCTOF());
			//			}
		}
	}

	public void fit(int sector, int layer, int paddle) {

		H1F leftRightHist = dataGroups.getItem(sector,layer,paddle).getH1F("left_right");

		int nBin = leftRightHist.getXaxis().getNBins();
		int maxBin = leftRightHist.getMaximumBin();

		// calculate the 'average' of all bins
		double averageAllBins=0;
		for(int i=1;i<=nBin;i++)
			averageAllBins+=leftRightHist.getBinContent(i);
		averageAllBins/=nBin;

		// find the first points left and right of centre with bin content < average
		int lowRangeFirstCut=0,highRangeFirstCut=0;
		for(int i=maxBin;i>=1;i--){
			if(leftRightHist.getBinContent(i)<averageAllBins){
				lowRangeFirstCut=i;
				break;
			}
		}
		for(int i=maxBin;i<=nBin;i++){
			if(leftRightHist.getBinContent(i)<averageAllBins){
				highRangeFirstCut=i;
				break;
			}
		}

		// now calculate the 'average' in this range
		double averageCentralRange=0;
		for(int i=lowRangeFirstCut;i<=highRangeFirstCut;i++)
			averageCentralRange+=leftRightHist.getBinContent(i);
		averageCentralRange/=(highRangeFirstCut-lowRangeFirstCut+1);

		// find the first points left and right of centre with bin content < 0.3 * average in the range
		double threshold=averageCentralRange*LEFT_RIGHT_RATIO;
		//if(averageCentralRange<20) return;
		int lowRangeSecondCut=0, highRangeSecondCut=0;
		for(int i=maxBin;i>=1;i--){
			if(leftRightHist.getBinContent(i)<threshold){
				lowRangeSecondCut=i;
				break;
			}
		}
		for(int i=maxBin;i<=nBin;i++){
			if(leftRightHist.getBinContent(i)<threshold){
				highRangeSecondCut=i;
				break;
			}
		}


		// find error
		// find the points left and right of centre with bin content < 0.3 * (average + sqrt of average)
		double errorThreshold = (averageCentralRange + Math.sqrt(averageCentralRange))*LEFT_RIGHT_RATIO;
		int lowRangeError=0, highRangeError=0;
		for(int i=maxBin;i>=1;i--){
			if(leftRightHist.getBinContent(i)<errorThreshold){
				lowRangeError=i;
				break;
			}
		}
		for(int i=maxBin;i<=nBin;i++){
			if(leftRightHist.getBinContent(i)<errorThreshold){
				highRangeError=i;
				break;
			}
		}

		// create the function showing the width of the spread
		//		F1D edgeToEdgeFunc = dataGroups.getItem(sector,layer,paddle).getF1D("edgeToEdgeFunc");
		//		edgeToEdgeFunc.setRange(leftRightHist.getAxis().getBinCenter(lowRangeSecondCut), 
		//							leftRightHist.getAxis().getBinCenter(highRangeSecondCut));
		//
		//		edgeToEdgeFunc.setParameter(0, averageCentralRange*LEFT_RIGHT_RATIO); // height to draw line at

		// create the function with range = error values
		// not currently used for calibration
		F1D errorFunc = new F1D("p1","[height]",
				leftRightHist.getAxis().getBinCenter(lowRangeError) -
				leftRightHist.getAxis().getBinCenter(lowRangeSecondCut),
				leftRightHist.getAxis().getBinCenter(highRangeError) -
				leftRightHist.getAxis().getBinCenter(highRangeSecondCut));
		errorFunc.setParameter(0, averageCentralRange*LEFT_RIGHT_RATIO); // height to draw line at

	}

	@Override
	public void customFit(int sector, int layer, int paddle){

		// draw the stats
		TCanvas c1 = new TCanvas("All Paddle position",1200,800);
		c1.setDefaultCloseOperation(c1.HIDE_ON_CLOSE);
		c1.cd(0);
		c1.draw(allPaddlePos);	

		String[] fields = { "Override centroid:" , "SPACE"};
		TOFCustomFitPanel panel = new TOFCustomFitPanel(fields);

		int result = JOptionPane.showConfirmDialog(null, panel, 
				"Adjust Fit / Override for paddle "+paddle, JOptionPane.OK_CANCEL_OPTION);
		if (result == JOptionPane.OK_OPTION) {

			double overrideValue = toDouble(panel.textFields[0].getText());

			// save the override values
			Double[] consts = constants.getItem(sector, layer, paddle);
			consts[LEFTRIGHT_OVERRIDE] = overrideValue;

			fit(sector, layer, paddle);

			// update the table
			saveRow(sector,layer,paddle);
			calib.fireTableDataChanged();

		}	 
	}

	public Double getCentroid(int sector, int layer, int paddle) {

		double leftRight = 0.0;
		double overrideVal = constants.getItem(sector, layer, paddle)[LEFTRIGHT_OVERRIDE];

		if (overrideVal != UNDEFINED_OVERRIDE) {
			leftRight = overrideVal;
		}
		else {
			//			int maxBin = dataGroups.getItem(sector,layer,paddle).getH1F("left_right").getMaximumBin();
			//			leftRight = 
			//				dataGroups.getItem(sector,layer,paddle).getH1F("left_right").getXaxis().getBinCenter(maxBin);
			leftRight = 
					dataGroups.getItem(sector,layer,paddle).getH1F("left_right").getMean();
		}
		return leftRight;
	}

	@Override
	public void saveRow(int sector, int layer, int paddle) {
		calib.setDoubleValue(getCentroid(sector,layer,paddle),
				"left_right", sector, layer, paddle);
		calib.setDoubleValue(0.0, "paddle2paddle", sector, layer, paddle);
	}

	@Override
	public boolean isGoodPaddle(int sector, int layer, int paddle) {

		return (getCentroid(sector,layer,paddle) >= -MAX_LEFTRIGHT
				&&
				getCentroid(sector,layer,paddle) <= MAX_LEFTRIGHT);
	}

	@Override
	public void drawPlots(int sector, int layer, int paddle, EmbeddedCanvas canvas) {

		canvas.draw(dataGroups.getItem(sector,layer,paddle).getH1F("left_right"));
		//canvas.draw(dataGroups.getItem(sector,layer,paddle).getF1D("edgeToEdgeFunc"), "same");

	}

	@Override
	public DataGroup getSummary(int sector, int layer) {

		double[] paddleNumbers = new double[NUM_PADDLES[0]];
		double[] paddleUncs = new double[NUM_PADDLES[0]];
		double[] values = new double[NUM_PADDLES[0]];
		double[] valueUncs = new double[NUM_PADDLES[0]];

		for (int p = 1; p <= NUM_PADDLES[0]; p++) {

			paddleNumbers[p - 1] = (double) p;
			paddleUncs[p - 1] = 0.0;
			values[p - 1] = getCentroid(sector, layer, p);
			valueUncs[p - 1] = 0.0;
		}

		GraphErrors summ = new GraphErrors("summ", paddleNumbers,
				values, paddleUncs, valueUncs);

		summ.setTitle("Left Right centroids");

		summ.setTitleX("Paddle Number");
		summ.setTitleY("Centroid (ns)");
		summ.setMarkerSize(MARKER_SIZE);
		summ.setLineThickness(MARKER_LINE_WIDTH);


		DataGroup dg = new DataGroup(1,1);
		dg.addDataSet(summ, 0);
		return dg;

	}
}
