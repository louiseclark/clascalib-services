package org.jlab.calib.services;


import java.awt.BorderLayout;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeMap;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSplitPane;

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


public class TofAttenEventListener extends TOFCalibrationEngine { 

	// constants for indexing the constants array
	public final int ATTEN_OVERRIDE = 0;
	public final int ATTEN_UNC_OVERRIDE = 1;
	public final int OFFSET_OVERRIDE = 2;
	
	public TofAttenEventListener() {

		stepName = "Attenuation Length";
		fileNamePrefix = "FTOF_CALIB_ATTEN_";
		// get file name here so that each timer update overwrites it
		filename = nextFileName();
		
		calib = new CalibrationConstants(3,
				"attlen_left/F:attlen_right/F:attlen_left_err/F:attlen_right_err/F:y_offset/F");
		calib.setName("/calibration/ftof/attenuation");
		calib.setPrecision(3);
		
		// assign constraints corresponding to layer 1 values for now
		// need addConstraint to be able to check layer and paddle
		for (int paddle=1; paddle<=23; paddle++) {
			calib.addConstraint(3, expectedAttlen(1,1,paddle)*0.9,
								   expectedAttlen(1,1,paddle)*1.1,
								   2,
								   paddle);
			calib.addConstraint(4, expectedAttlen(1,1,paddle)*0.9,
					   expectedAttlen(1,1,paddle)*1.1,
					   2,
					   paddle);
		}

	}

	@Override
	public void resetEventListener() {

		// LC perform init processing
		for (int sector = 1; sector <= 6; sector++) {
			for (int layer = 1; layer <= 3; layer++) {
				int layer_index = layer - 1;
				for (int paddle = 1; paddle <= NUM_PADDLES[layer_index]; paddle++) {

					// create all the histograms
					int numBins = (int) (paddleLength(sector,layer,paddle)*0.6);  // 1 bin per 2cm + 10% either side
					double min = paddleLength(sector,layer,paddle) * -0.6;
					double max = paddleLength(sector,layer,paddle) * 0.6;
					H2F hist = new H2F("atten", "Log Ratio vs Position : Paddle "
 									+ paddle, numBins, min, max, 100, -3.0, 3.0);

					hist.setName("atten");
					hist.setTitle("Log Ratio vs Position : " + LAYER_NAME[layer_index] 
							+ " Sector "+sector+" Paddle "+paddle);
					hist.setXTitle("Position");
					hist.setYTitle("Log ratio");
					
					// create all the functions and graphs
					F1D attenFunc = new F1D("attenFunc", "[a]+[b]*x", -250.0, 250.0);
					GraphErrors meanGraph = new GraphErrors();
					meanGraph.setName("meanGraph");
					//attenFunc.setLineColor(1);
					
					DataGroup dg = new DataGroup(2,1);
					dg.addDataSet(hist, 0);
					dg.addDataSet(meanGraph, 1);
					dg.addDataSet(attenFunc, 1);
					dataGroups.add(dg, sector,layer,paddle);
					
					// initialize the constants array
					Double[] consts = {UNDEFINED_OVERRIDE, UNDEFINED_OVERRIDE, UNDEFINED_OVERRIDE};
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

		for (TOFPaddle paddle : paddleList) {

			int sector = paddle.getDescriptor().getSector();
			int layer = paddle.getDescriptor().getLayer();
			int component = paddle.getDescriptor().getComponent();

			dataGroups.getItem(sector,layer,component).getH2F("atten").fill(
					paddle.position(), paddle.logRatio());
		}
	}
    
    @Override
	public void fit(int sector, int layer, int paddle, double minRange,
			double maxRange) {

		H2F attenHist = dataGroups.getItem(sector,layer,paddle).getH2F("atten");
		
		// fit function to the graph of means
		GraphErrors meanGraph = (GraphErrors) dataGroups.getItem(sector,layer,paddle).getData("meanGraph");
		meanGraph.copy(attenHist.getProfileX());

		double lowLimit;
		double highLimit;

		if (minRange == UNDEFINED_OVERRIDE) {
			// default value
			lowLimit = paddleLength(sector,layer,paddle) * -0.4;
		} 
		else {
			// custom value
			lowLimit = minRange;
		}

		
		if (maxRange == UNDEFINED_OVERRIDE) {
			// default value
			highLimit = paddleLength(sector,layer,paddle) * 0.4;
		} 
		else {
			// custom value
			highLimit = maxRange;
		}

//		System.out.println("SLC "+sector+layer+paddle);
//		System.out.println("paddleLength "+paddleLength(sector,layer,paddle));
		
		F1D attenFunc = dataGroups.getItem(sector,layer,paddle).getF1D("attenFunc");
		attenFunc.setRange(lowLimit, highLimit);
		attenFunc.setParameter(0, 0.0);
		attenFunc.setParameter(1, 2.0/expectedAttlen(sector,layer,paddle));
		attenFunc.setParLimits(0, -5.0, 5.0);
		attenFunc.setParLimits(1, 2.0/500.0, 2.0/10.0);
//		if (sector==1 && paddle==8) {
//			System.out.println("SLC "+sector+layer+paddle);
//			DataFitter.fit(attenFunc, meanGraph, "RL");
//			System.out.println("Param 0 is "+attenFunc.getParameter(0));
//			System.out.println("Param 0 error is "+attenFunc.parameter(0).error());
//			System.out.println("Param 1 is "+attenFunc.getParameter(1));
//			System.out.println("Param 1 error is "+attenFunc.parameter(1).error());
//
//		}
//		else {
			DataFitter.fit(attenFunc, meanGraph, "RNQ");
//		}
		
		
//		if (sector==1 && layer==1 && paddle==10) {
//			TCanvas c1 = new TCanvas("c1",1,1);
//			c1.draw(meanGraph);
//			c1.draw(attenFunc,"same");
//
//			System.out.println("SLC "+sector+layer+paddle);
//			System.out.println("Param 1 is "+ attenFunc.getParameter(1));
//			System.out.println("Param 1 error is "+ attenFunc.parameter(1).error());
//
//		}
		
	}

	public void customFit(int sector, int layer, int paddle){

		String[] fields = { "Min range for fit:", "Max range for fit:", "SPACE",
				"Override Attenuation Length:", "Override Attenuation Length uncertainty:",
				"Override offset:" };
		TOFCustomFitPanel panel = new TOFCustomFitPanel(fields);

		int result = JOptionPane.showConfirmDialog(null, panel, 
				"Adjust Fit / Override for paddle "+paddle, JOptionPane.OK_CANCEL_OPTION);
		if (result == JOptionPane.OK_OPTION) {

			double minRange = toDouble(panel.textFields[0].getText());
			double maxRange = toDouble(panel.textFields[1].getText());
			double overrideValue = toDouble(panel.textFields[2].getText());
			double overrideUnc = toDouble(panel.textFields[3].getText());			
			double overrideOffset = toDouble(panel.textFields[4].getText());		
			
			// save the override values
			Double[] consts = constants.getItem(sector, layer, paddle);
			consts[ATTEN_OVERRIDE] = overrideValue;
			consts[ATTEN_UNC_OVERRIDE] = overrideUnc;
			consts[OFFSET_OVERRIDE] = overrideOffset;
			
			fit(sector, layer, paddle, minRange, maxRange);

			// update the table
			saveRow(sector,layer,paddle);
			calib.fireTableDataChanged();
			
		}	 
	}
    
	public Double getAttlen(int sector, int layer, int paddle) {

		double attLen = 0.0;
		double overrideVal = constants.getItem(sector, layer, paddle)[ATTEN_OVERRIDE];

		if (overrideVal != UNDEFINED_OVERRIDE) {
			attLen = overrideVal;
		}
		else {
			double gradient = dataGroups.getItem(sector,layer,paddle).getF1D("attenFunc")
					.getParameter(1);
			if (gradient == 0.0) {
				attLen = 0.0;
			} else {
				attLen = 2 / gradient;
			}
		}

		return attLen;
	}

	public Double getAttlenError(int sector, int layer, int paddle) {

		double attLenUnc = 0.0;
		double overrideVal = constants.getItem(sector, layer, paddle)[ATTEN_UNC_OVERRIDE];

		if (overrideVal != UNDEFINED_OVERRIDE) {
			attLenUnc = overrideVal;
		}
		else {
			double gradient = dataGroups.getItem(sector,layer,paddle).getF1D("attenFunc")
					.getParameter(1);
			double gradientErr = dataGroups.getItem(sector,layer,paddle).getF1D("attenFunc")
					.parameter(1).error();
			double attlen = getAttlen(sector, layer, paddle);
			if (gradient == 0.0) {
				attLenUnc = 0.0;
			} else {
				attLenUnc = (gradientErr / gradient) * attlen;
			}
		}
		return attLenUnc;
	}
	
	public Double getOffset(int sector, int layer, int paddle) {
		
		double offset = 0.0;
		double overrideVal = constants.getItem(sector, layer, paddle)[OFFSET_OVERRIDE];
		
		if (overrideVal != UNDEFINED_OVERRIDE) {
			offset = overrideVal;
		}
		else {
			offset = dataGroups.getItem(sector,layer,paddle).getF1D("attenFunc")
					.getParameter(0);
		}
		
		return offset;

	}
	
	@Override
	public void saveRow(int sector, int layer, int paddle) {
		calib.setDoubleValue(getAttlen(sector,layer,paddle),
				"attlen_left", sector, layer, paddle);
		calib.setDoubleValue(getAttlen(sector,layer,paddle),
				"attlen_right", sector, layer, paddle);
		calib.setDoubleValue(getAttlenError(sector,layer,paddle),
				"attlen_left_err", sector, layer, paddle);
		calib.setDoubleValue(getAttlenError(sector,layer,paddle),
				"attlen_right_err", sector, layer, paddle);
		calib.setDoubleValue(getOffset(sector,layer,paddle),
				"y_offset", sector, layer, paddle);

	}
		
	public double expectedAttlen(int sector, int layer, int paddle) {
		
		double expAttlen = 0.0;
		
		if (layer==1) {
			expAttlen = (0.251*paddleLength(sector,layer,paddle)) + 124.96;
		}
		else if (layer==2) {
			expAttlen = (0.637*paddleLength(sector,layer,paddle)) + 128.08;
		} 
		else if (layer==3) {
			expAttlen = (0.251*paddleLength(sector,layer,paddle)) + 124.96;
		}
		
		return expAttlen;
	}
	
	@Override
	public boolean isGoodPaddle(int sector, int layer, int paddle) {

		double attlen = getAttlen(sector,layer,paddle);
		double expAttlen = expectedAttlen(sector,layer,paddle);
		
		return (attlen >= (0.9*expAttlen)) && (attlen <= (1.1*expAttlen));

	}
	
	@Override
	public void drawPlots(int sector, int layer, int paddle, EmbeddedCanvas canvas) {

		canvas.draw(dataGroups.getItem(sector,layer,paddle).getGraph("meanGraph"));
		canvas.draw(dataGroups.getItem(sector,layer,paddle).getF1D("attenFunc"), "same");

	}

	@Override
	public DataGroup getSummary(int sector, int layer) {
				
		int layer_index = layer-1;
		double[] paddleNumbers = new double[NUM_PADDLES[layer_index]];
		double[] paddleUncs = new double[NUM_PADDLES[layer_index]];
		double[] Attlens = new double[NUM_PADDLES[layer_index]];
		double[] AttlenUncs = new double[NUM_PADDLES[layer_index]];

		for (int p = 1; p <= NUM_PADDLES[layer_index]; p++) {

			paddleNumbers[p - 1] = (double) p;
			paddleUncs[p - 1] = 0.0;
			Attlens[p - 1] = getAttlen(sector, layer, p);
//			AttlenUncs[p - 1] = getAttlenError(sector, layer, p);
			AttlenUncs[p - 1] = 0.0;

		}

		GraphErrors attSumm = new GraphErrors("attSumm", paddleNumbers,
				Attlens, paddleUncs, AttlenUncs);
		
//		summary.setTitle("Attenuation Length: "
//				+ LAYER_NAME[paddle - 1] + " Sector "
//				+ sector);
//		summary.setXTitle("Paddle Number");
//		summary.setYTitle("Attenuation Length (cm)");
//		summary.setMarkerSize(5);
//		summary.setMarkerStyle(2);
		
		DataGroup dg = new DataGroup(1,1);
		dg.addDataSet(attSumm, 0);
		return dg;
		
	}

}
