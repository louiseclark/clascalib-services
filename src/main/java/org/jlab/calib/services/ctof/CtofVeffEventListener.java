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
import org.jlab.io.base.DataEvent;
import org.jlab.io.base.DataEventType;
import org.jlab.io.evio.EvioDataEvent;
import org.jlab.io.task.DataSourceProcessorPane;
import org.jlab.io.task.IDataEventListener;
import org.jlab.utils.groups.IndexedList;

public class CtofVeffEventListener extends CTOFCalibrationEngine {

	public final int VEFF_OVERRIDE = 0;
	public final int VEFF_UNC_OVERRIDE = 1;

	public final double EXPECTED_VEFF = 16.0;
	public final double ALLOWED_VEFF_DIFF = 0.1;

	public CtofVeffEventListener() {

		stepName = "Effective Velocity";
		fileNamePrefix = "CTOF_CALIB_VEFF_";
		// get file name here so that each timer update overwrites it
		filename = nextFileName();

		calib = new CalibrationConstants(3,
				"veff_left/F:veff_right/F:veff_left_err/F:veff_right_err/F");
		calib.setName("/calibration/ctof/effective_velocity");
		calib.setPrecision(3);

		// assign constraints to all paddles
		// effective velocity to be within 10% of 16.0 cm/ns
		calib.addConstraint(3, EXPECTED_VEFF*(1-ALLOWED_VEFF_DIFF),
				EXPECTED_VEFF*(1+ALLOWED_VEFF_DIFF));
		calib.addConstraint(4, EXPECTED_VEFF*(1-ALLOWED_VEFF_DIFF),
				EXPECTED_VEFF*(1+ALLOWED_VEFF_DIFF));

		if (CTOFCalibrationEngine.calDBSource==CTOFCalibrationEngine.CAL_FILE) {
			// read in the veff values from the text file
			//String inputFile = "/home/louise/workspace/clascalib-services/CTOF_CALIB_VEFF_20161214_1M_events.txt";
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
				line = bufferedReader.readLine(); // skip header

				while (line != null) {

					int sector = Integer.parseInt(line.substring(0, 3).trim());
					int layer = Integer.parseInt(line.substring(3, 7).trim());
					int paddle = Integer.parseInt(line.substring(7, 11).trim());
					double veff = Double.parseDouble(line.substring(11,26).trim());

					veffValues.add(veff, sector, layer, paddle);

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
	public void resetEventListener() {

		// LC perform init processing
		for (int paddle = 1; paddle <= NUM_PADDLES[0]; paddle++) {

			// create all the histograms
			int numBins = (int) (paddleLength(1,1,paddle)*0.6);  // 1 bin per 2cm + 10% either side
			double min = paddleLength(1,1,paddle) * -0.6;
			double max = paddleLength(1,1,paddle) * 0.6;

			H2F hist = 
					new H2F("veff",
							"veff",
							numBins, min, max, 
							200, -10.0, 10.0);

			hist.setName("veff");
			hist.setTitle("Half Time Diff vs Position : " 
					+ " Paddle "+paddle);
			hist.setTitleX("Position (cm)");
			hist.setTitleY("Half Time Diff (ns)");

			// create all the functions and graphs
			F1D veffFunc = new F1D("veffFunc", "[a]+[b]*x", 0.0, 40.0);
			GraphErrors veffGraph = new GraphErrors();
			veffGraph.setName("veffGraph");

			veffFunc.setLineColor(FUNC_COLOUR);
			veffFunc.setLineWidth(FUNC_LINE_WIDTH);
			veffGraph.setMarkerSize(MARKER_SIZE);
			veffGraph.setLineThickness(MARKER_LINE_WIDTH);

			DataGroup dg = new DataGroup(2,1);
			dg.addDataSet(hist, 0);
			dg.addDataSet(veffGraph, 1);
			dg.addDataSet(veffFunc, 1);
			dataGroups.add(dg, 1,1,paddle);

			// initialize the constants array
			Double[] consts = {UNDEFINED_OVERRIDE, UNDEFINED_OVERRIDE};
			// override values

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

			if (paddle.includeInCtofVeff()) {
				dataGroups.getItem(sector,layer,component).getH2F("veff").fill(
						paddle.zPosCTOF(), paddle.recHalfTimeDiff());
			}
		}
	}

	@Override
	public void fit(int sector, int layer, int paddle,
			double minRange, double maxRange) {

		H2F veffHist = dataGroups.getItem(sector,layer,paddle).getH2F("veff");

		// fit function to the graph of means
		GraphErrors veffGraph = (GraphErrors) dataGroups.getItem(sector,layer,paddle).getData("veffGraph");
		veffGraph.copy(veffHist.getProfileX());

		// find the range for the fit
		double lowLimit;
		double highLimit;

		if (minRange != UNDEFINED_OVERRIDE) {
			// use custom values for fit
			lowLimit = minRange;
		}
		else {
			//			lowLimit = paddleLength(sector,layer,paddle) * -0.4;
			lowLimit = 0.0;
		}

		if (maxRange != UNDEFINED_OVERRIDE) {
			// use custom values for fit
			highLimit = maxRange;
		}
		else {
			highLimit = paddleLength(sector,layer,paddle) * 0.4;
		}

		F1D veffFunc = dataGroups.getItem(sector,layer,paddle).getF1D("veffFunc");
		veffFunc.setRange(lowLimit, highLimit);

		veffFunc.setParameter(0, 0.0);
		veffFunc.setParameter(1, 1.0/16.0);
		//		veffFunc.setParLimits(0, -5.0, 5.0);
		//		veffFunc.setParLimits(1, 1.0/20.0, 1.0/12.0);

		DataFitter.fit(veffFunc, veffGraph, "RNQ");

	}

	public void customFit(int sector, int layer, int paddle){

		String[] fields = { "Min range for fit:", "Max range for fit:", "SPACE",
				"Override Effective Velocity:", "Override Effective Velocity uncertainty:"};

		TOFCustomFitPanel panel = new TOFCustomFitPanel(fields);

		int result = JOptionPane.showConfirmDialog(null, panel, 
				"Adjust Fit / Override for paddle "+paddle, JOptionPane.OK_CANCEL_OPTION);
		if (result == JOptionPane.OK_OPTION) {

			double minRange = toDouble(panel.textFields[0].getText());
			double maxRange = toDouble(panel.textFields[1].getText());
			double overrideValue = toDouble(panel.textFields[2].getText());
			double overrideUnc = toDouble(panel.textFields[3].getText());

			// save the override values
			Double[] consts = constants.getItem(sector, layer, paddle);
			consts[VEFF_OVERRIDE] = overrideValue;
			consts[VEFF_UNC_OVERRIDE] = overrideUnc;

			fit(sector, layer, paddle, minRange, maxRange);

			// update the table
			saveRow(sector,layer,paddle);
			calib.fireTableDataChanged();

		}	 
	}

	public Double getVeff(int sector, int layer, int paddle) {

		double veff = 0.0;
		double overrideVal = constants.getItem(sector, layer, paddle)[VEFF_OVERRIDE];

		if (overrideVal != UNDEFINED_OVERRIDE) {
			veff = overrideVal;
		}
		else {
			double gradient = dataGroups.getItem(sector,layer,paddle).getF1D("veffFunc") 
					.getParameter(1);
			if (gradient==0.0) {
				veff=0.0;
			}
			else {
				veff = 1/gradient;
			}
		}
		return veff;
	}

	public Double getVeffError(int sector, int layer, int paddle){

		double veffError = 0.0;
		double overrideVal = constants.getItem(sector, layer, paddle)[VEFF_UNC_OVERRIDE];

		if (overrideVal != UNDEFINED_OVERRIDE) {
			veffError = overrideVal;
		}
		else {
			// Calculate the error
			// fractional error in veff = fractional error in 1/veff

			double gradient = dataGroups.getItem(sector,layer,paddle).getF1D("veffFunc")
					.getParameter(1);
			double gradientErr = dataGroups.getItem(sector,layer,paddle).getF1D("veffFunc")
					.parameter(1).error();
			double veff = getVeff(sector, layer, paddle);

			if (gradient==0.0) {
				veffError = 0.0;
			}
			else {
				veffError = (gradientErr/gradient) * veff;
			}
		}
		return veffError;
	}	

	@Override
	public void saveRow(int sector, int layer, int paddle) {
		calib.setDoubleValue(getVeff(sector,layer,paddle),
				"veff_left", sector, layer, paddle);
		calib.setDoubleValue(getVeff(sector,layer,paddle),
				"veff_right", sector, layer, paddle);
		calib.setDoubleValue(getVeffError(sector,layer,paddle),
				"veff_left_err", sector, layer, paddle);
		calib.setDoubleValue(getVeffError(sector,layer,paddle),
				"veff_right_err", sector, layer, paddle);

	}

	@Override
	public boolean isGoodPaddle(int sector, int layer, int paddle) {

		return (getVeff(sector,layer,paddle) >= EXPECTED_VEFF*(1-ALLOWED_VEFF_DIFF)
				&&
				getVeff(sector,layer,paddle) <= EXPECTED_VEFF*(1+ALLOWED_VEFF_DIFF));

	}

	@Override
	public void drawPlots(int sector, int layer, int paddle, EmbeddedCanvas canvas) {

		canvas.draw(dataGroups.getItem(sector,layer,paddle).getGraph("veffGraph"));
		canvas.draw(dataGroups.getItem(sector,layer,paddle).getF1D("veffFunc"), "same");

	}

	@Override
	public DataGroup getSummary(int sector, int layer) {

		int layer_index = layer-1;
		double[] paddleNumbers = new double[NUM_PADDLES[0]];
		double[] paddleUncs = new double[NUM_PADDLES[0]];
		double[] veffs = new double[NUM_PADDLES[0]];
		double[] veffUncs = new double[NUM_PADDLES[0]];

		for (int p = 1; p <= NUM_PADDLES[0]; p++) {

			paddleNumbers[p - 1] = (double) p;
			paddleUncs[p - 1] = 0.0;
			veffs[p - 1] = getVeff(sector, layer, p);
			//			veffUncs[p - 1] = getVeffError(sector, layer, p);
			veffUncs[p - 1] = 0.0;
		}

		GraphErrors summ = new GraphErrors("summ", paddleNumbers,
				veffs, paddleUncs, veffUncs);

		summ.setTitle("Effective Velocity");
		summ.setTitleX("Paddle Number");
		summ.setTitleY("Effective velocity (cm/ns)");
		summ.setMarkerSize(MARKER_SIZE);
		summ.setLineThickness(MARKER_LINE_WIDTH);

		DataGroup dg = new DataGroup(1,1);
		dg.addDataSet(summ, 0);
		return dg;

	}

}
