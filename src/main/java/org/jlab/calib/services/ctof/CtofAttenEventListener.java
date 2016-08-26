package org.jlab.calib.services.ctof;


import java.awt.BorderLayout;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeMap;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JSplitPane;

import org.jlab.calib.services.TOFCalibrationEngine;
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


public class CtofAttenEventListener extends TOFCalibrationEngine { // IDataEventListener {

	public CtofAttenEventListener() {

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

		if (minRange != 0.0 && maxRange != 0.0) {

			// use custom values for fit
			lowLimit = minRange;
			highLimit = maxRange;
		} else {
			lowLimit = paddleLength(sector,layer,paddle) * -0.4;
			highLimit = paddleLength(sector,layer,paddle) * 0.4;
		}

//		System.out.println("SLC "+sector+layer+paddle);
//		System.out.println("paddleLength "+paddleLength(sector,layer,paddle));
		
		F1D attenFunc = dataGroups.getItem(sector,layer,paddle).getF1D("attenFunc");
		attenFunc.setRange(lowLimit, highLimit);
		attenFunc.setParameter(0, 0.0);
		attenFunc.setParameter(1, 2.0/200.0);
		attenFunc.setParLimits(0, -5.0, 5.0);
		attenFunc.setParLimits(1, 2.0/500.0, 2.0/10.0);
		DataFitter.fit(attenFunc, meanGraph, "RNQ");
		
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

	public Double getAttlen(int sector, int layer, int paddle) {

		double attLen = 0.0;
		double gradient = dataGroups.getItem(sector,layer,paddle).getF1D("attenFunc")
				.getParameter(1);
		if (gradient == 0.0) {
			attLen = 0.0;
		} else {
			attLen = 2 / gradient;
		}

		return attLen;
	}

	public Double getAttlenError(int sector, int layer, int paddle) {

		double attLenUnc = 0.0;

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
		return attLenUnc;
	}
	
	public Double getOffset(int sector, int layer, int paddle) {
		
		return dataGroups.getItem(sector,layer,paddle).getF1D("attenFunc")
				.getParameter(1);

	}	

	@Override
	public void save() {

		for (int sector = 1; sector <= 6; sector++) {
			for (int layer = 1; layer <= 3; layer++) {
				int layer_index = layer - 1;
				for (int paddle = 1; paddle <= NUM_PADDLES[layer_index]; paddle++) {
					calib.addEntry(sector, layer, paddle);
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
			}
		}
		calib.save("FTOF_CALIB_ATTEN.txt");
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
