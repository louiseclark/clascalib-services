package org.jlab.calib.services.ctof;


import java.awt.BorderLayout;
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
import org.jlab.io.base.DataEvent;
import org.jlab.io.base.DataEventType;
import org.jlab.io.evio.EvioDataEvent;
import org.jlab.io.task.DataSourceProcessorPane;
import org.jlab.io.task.IDataEventListener;
import org.jlab.utils.groups.IndexedList;

public class CtofLeftRightEventListener extends TOFCalibrationEngine {

	public final static int[]		NUM_PADDLES = {23,62,5};
	public final static String[]	LAYER_NAME = {"FTOF1A","FTOF1B","FTOF2"};
	final double LEFT_RIGHT_RATIO = 0.3;
	
	CalibrationConstants calib;
	IndexedList<DataGroup> dataGroups = new IndexedList<DataGroup>(3);

	public CtofLeftRightEventListener() {

		calib = new CalibrationConstants(3,
				"left_right/F");
		calib.setName("/calibration/ftof/timing_offset");

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

		// LC perform init processing
		for (int sector = 1; sector <= 6; sector++) {
			for (int layer = 1; layer <= 3; layer++) {
				int layer_index = layer - 1;
				for (int paddle = 1; paddle <= NUM_PADDLES[layer_index]; paddle++) {

					// create all the histograms
					H1F hist = new H1F("left_right","Left Right: Paddle "+paddle, 
							200, -960.0, 960.0);

					hist.setTitle("Left Right  : " + LAYER_NAME[layer_index] 
							+ " Sector "+sector+" Paddle "+paddle);
					
					// create all the functions
					F1D edgeToEdgeFunc = new F1D("edgeToEdgeFunc","[height]",
							-960.0, 960.0);

					DataGroup dg = new DataGroup(1,1);
					dg.addDataSet(hist, 0);
					dg.addDataSet(edgeToEdgeFunc, 0);
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

			dataGroups.getItem(sector,layer,component).getH1F("left_right").fill(
					paddle.leftRight());
		}
	}


	public void analyze() {
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
		
		H1F leftRightHist = dataGroups.getItem(sector,layer,paddle).getH1F("left_right");
		
		int nBin = leftRightHist.getXaxis().getNBins();

		// calculate the 'average' of all bins
		double averageAllBins=0;
		for(int i=1;i<=nBin;i++)
			averageAllBins+=leftRightHist.getBinContent(i);
		averageAllBins/=nBin;

		// find the first points left and right of centre with bin content < average
		int lowRangeFirstCut=0,highRangeFirstCut=0;
		for(int i=nBin/2;i>=1;i--){
			if(leftRightHist.getBinContent(i)<averageAllBins){
				lowRangeFirstCut=i;
				break;
			}
		}
		for(int i=nBin/2;i<=nBin;i++){
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
		for(int i=nBin/2;i>=1;i--){
			if(leftRightHist.getBinContent(i)<threshold){
				lowRangeSecondCut=i;
				break;
			}
		}
		for(int i=nBin/2;i<=nBin;i++){
			if(leftRightHist.getBinContent(i)<threshold){
				highRangeSecondCut=i;
				break;
			}
		}

		
		// find error
		// find the points left and right of centre with bin content < 0.3 * (average + sqrt of average)
		double errorThreshold = (averageCentralRange + Math.sqrt(averageCentralRange))*LEFT_RIGHT_RATIO;
		int lowRangeError=0, highRangeError=0;
		for(int i=nBin/2;i>=1;i--){
			if(leftRightHist.getBinContent(i)<errorThreshold){
				lowRangeError=i;
				break;
			}
		}
		for(int i=nBin/2;i<=nBin;i++){
			if(leftRightHist.getBinContent(i)<errorThreshold){
				highRangeError=i;
				break;
			}
		}
		
		// create the function showing the width of the spread
		F1D edgeToEdgeFunc = dataGroups.getItem(sector,layer,paddle).getF1D("edgeToEdgeFunc");
		edgeToEdgeFunc.setRange(leftRightHist.getAxis().getBinCenter(lowRangeSecondCut), 
							leftRightHist.getAxis().getBinCenter(highRangeSecondCut));

		edgeToEdgeFunc.setParameter(0, averageCentralRange*LEFT_RIGHT_RATIO); // height to draw line at

		// create the function with range = error values
		// not currently used for calibration
		F1D errorFunc = new F1D("p1","[height]",
				leftRightHist.getAxis().getBinCenter(lowRangeError) -
										 leftRightHist.getAxis().getBinCenter(lowRangeSecondCut),
										 leftRightHist.getAxis().getBinCenter(highRangeError) -
									     leftRightHist.getAxis().getBinCenter(highRangeSecondCut));
		errorFunc.setParameter(0, averageCentralRange*LEFT_RIGHT_RATIO); // height to draw line at
		
	}
	
	public Double getCentroid(int sector, int layer, int paddle) {
		
		double leftRight = 0.0;

		double min = dataGroups.getItem(sector,layer,paddle).getF1D("edgeToEdgeFunc").getMin(); 
		double max = dataGroups.getItem(sector,layer,paddle).getF1D("edgeToEdgeFunc").getMax();
		leftRight = (min+max)/2.0;
		
		return leftRight;
	}

	public void save() {

		for (int sector = 1; sector <= 6; sector++) {
			for (int layer = 1; layer <= 3; layer++) {
				int layer_index = layer - 1;
				for (int paddle = 1; paddle <= NUM_PADDLES[layer_index]; paddle++) {
					calib.addEntry(sector, layer, paddle);
					calib.setDoubleValue(getCentroid(sector,layer,paddle),
							"left_right", sector, layer, paddle);
				}
			}
		}
		calib.save("FTOF_CALIB_LEFTRIGHT.txt");
	}

	@Override
	public List<CalibrationConstants> getCalibrationConstants() {
		
		return Arrays.asList(calib);
	}

	@Override
	public IndexedList<DataGroup>  getDataGroup(){
		return dataGroups;
	}

	@Override
	public void drawPlots(int sector, int layer, int paddle, EmbeddedCanvas canvas) {

		canvas.draw(dataGroups.getItem(sector,layer,paddle).getH1F("left_right"));
		canvas.draw(dataGroups.getItem(sector,layer,paddle).getF1D("edgeToEdgeFunc"), "same");

	}
	
	@Override
	public DataGroup getSummary(int sector, int layer) {
				
		int layer_index = layer-1;
		double[] paddleNumbers = new double[NUM_PADDLES[layer_index]];
		double[] paddleUncs = new double[NUM_PADDLES[layer_index]];
		double[] values = new double[NUM_PADDLES[layer_index]];
		double[] valueUncs = new double[NUM_PADDLES[layer_index]];

		for (int p = 1; p <= NUM_PADDLES[layer_index]; p++) {

			paddleNumbers[p - 1] = (double) p;
			paddleUncs[p - 1] = 0.0;
			values[p - 1] = getCentroid(sector, layer, p);
			valueUncs[p - 1] = 0.0;
		}

		GraphErrors summ = new GraphErrors("summ", paddleNumbers,
				values, paddleUncs, valueUncs);
		
//		summary.setTitle("Left Right centroids: "
//				+ LAYER_NAME[layer - 1] + " Sector "
//				+ sector);
//		summary.setXTitle("Paddle Number");
//		summary.setYTitle("Centroid (cm)");
//		summary.setMarkerSize(5);
//		summary.setMarkerStyle(2);
		
		DataGroup dg = new DataGroup(1,1);
		dg.addDataSet(summ, 0);
		return dg;
		
	}
}
