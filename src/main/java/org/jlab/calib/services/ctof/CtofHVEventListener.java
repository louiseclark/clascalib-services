package org.jlab.calib.services.ctof;


import java.util.Arrays;
import java.util.List;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JTabbedPane;

import org.jlab.calib.services.TOFCalibrationEngine;
import org.jlab.calib.services.TOFCustomFitPanel;
import org.jlab.calib.services.TOFH1F;
import org.jlab.calib.services.TOFPaddle;
import org.jlab.detector.calib.tasks.CalibrationEngine;
import org.jlab.detector.calib.utils.CalibrationConstants;
import org.jlab.groot.data.GraphErrors;
import org.jlab.groot.data.H1F;
import org.jlab.groot.fitter.DataFitter;
import org.jlab.groot.graphics.EmbeddedCanvas;
import org.jlab.groot.group.DataGroup;
import org.jlab.groot.math.F1D;
import org.jlab.groot.ui.TCanvas;
import org.jlab.io.base.DataEvent;
import org.jlab.io.base.DataEventType;
import org.jlab.utils.groups.IndexedList;

public class CtofHVEventListener extends TOFCalibrationEngine {

	// hists
	public final int GEOMEAN = 0;
	public final int LOGRATIO = 1;

	// consts
	public final int LR_CENTROID = 0;
	public final int LR_ERROR = 1;
	public final int GEOMEAN_OVERRIDE = 2;
	public final int GEOMEAN_UNC_OVERRIDE = 3;
	public final int LOGRATIO_OVERRIDE = 4;
	public final int LOGRATIO_UNC_OVERRIDE = 5;	

	// calibration values
	private final double[]		GM_HIST_MAX = {4000.0,8000.0,3000.0};
	private final int[]			GM_HIST_BINS = {200, 300, 150};
	private final double 		LR_THRESHOLD_FRACTION = 0.2;
	private final int			GM_REBIN_THRESHOLD = 50000;

	public final int[]		EXPECTED_MIP_CHANNEL = {800, 2000, 800};
	public final int		ALLOWED_MIP_DIFF = 50;

	public CtofHVEventListener() {

		calib = new CalibrationConstants(3,
				"mipa_left/F:mipa_right/F:mipa_left_err/F:mipa_right_err/F:logratio/F:logratio_err/F");
		calib.setName("/calibration/ftof/gain_balance");
		calib.setPrecision(3); // record calibration constants to 3 dp
		
		for (int i=0; i<3; i++) {
			
			int layer = i+1;
			calib.addConstraint(3, EXPECTED_MIP_CHANNEL[i]-ALLOWED_MIP_DIFF, 
								   EXPECTED_MIP_CHANNEL[i]+ALLOWED_MIP_DIFF, 1, layer);
			// calib.addConstraint(calibration column, min value, max value,
			// col to check if constraint should apply, value of col if constraint should be applied);
			// (omit last two if applying to all rows)
			calib.addConstraint(4, EXPECTED_MIP_CHANNEL[i]-ALLOWED_MIP_DIFF, 
					   EXPECTED_MIP_CHANNEL[i]+ALLOWED_MIP_DIFF, 1, layer);
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
					TOFH1F geoMeanHist = new TOFH1F("geomean",
							"Geometric Mean Sector "+sector+" Paddle "+paddle, 
							GM_HIST_BINS[layer_index], 0.0, GM_HIST_MAX[layer_index]);
					H1F logRatioHist = new TOFH1F("logratio",
							"Log Ratio Sector "+sector+" Paddle "+paddle, 75,-6.0,6.0);

					// create all the functions
					F1D gmFunc = new F1D("gmFunc", "[amp]*landau(x,[mean],[sigma]) +[exp_amp]*exp([p]*x)",
							 0.0, GM_HIST_MAX[layer_index]);
					F1D lrFunc = new F1D("lrFunc","[height]",-6.0,6.0);

					
					DataGroup dg = new DataGroup(2,1);
					dg.addDataSet(geoMeanHist, GEOMEAN);
					dg.addDataSet(logRatioHist, LOGRATIO);
					dg.addDataSet(gmFunc, GEOMEAN);
					dg.addDataSet(lrFunc, LOGRATIO);
					dataGroups.add(dg, sector,layer,paddle);

					// initialize the constants array
					Double[] consts = {0.0, 0.0, 0.0, 0.0, 0.0, 0.0};
					// Logratio, log ratio unc, override values
					
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

			if (paddle.isValidGeoMean()) {
				dataGroups.getItem(sector,layer,component).getH1F("geomean").fill(paddle.geometricMean());
			}

			if (paddle.isValidLogRatio()) {
				dataGroups.getItem(sector,layer,component).getH1F("logratio").fill(paddle.logRatio());
			}
		}
	}	

	@Override
	public void fit(int sector, int layer, int paddle,
			double minRange, double maxRange){
		fitGeoMean(sector, layer, paddle, minRange, maxRange);
		fitLogRatio(sector, layer, paddle, minRange, maxRange);
	}
	
	public void fitGeoMean(int sector, int layer, int paddle,
			double minRange, double maxRange){

		int layer_index = layer-1;

		TOFH1F h = (TOFH1F) dataGroups.getItem(sector,layer,paddle).getH1F("geomean");;
		
		// First rebin depending on number of entries
		int nEntries = h.getEntries(); 
		if ((nEntries != 0) && (h.getAxis().getNBins() == GM_HIST_BINS[layer_index])) {
			//   not empty      &&   hasn't already been rebinned
			int nRebin=(int) (GM_REBIN_THRESHOLD/nEntries);            
			if (nRebin>5) {
				nRebin=5;               
			}

			if(nRebin>0) {
				h.rebin(nRebin);
			}		
		}		
		// Work out the range for the fit
		double maxChannel = h.getAxis().getBinCenter(h.getAxis().getNBins()-1);
		double startChannelForFit = 0.0;
		double endChannelForFit = 0.0;
		if (minRange==0.0) {
			// default value
			startChannelForFit = EXPECTED_MIP_CHANNEL[layer-1] * 0.75;
		}
		else {
			// custom value
			startChannelForFit = minRange;
		}
		if (maxRange==0.0) {
			//default value
			endChannelForFit = maxChannel * 0.9;
		}
		else {
			// custom value
			endChannelForFit = maxRange;
		}

		// find the maximum bin after the start channel for the fit
		int startBinForFit = h.getxAxis().getBin(startChannelForFit);
		int endBinForFit = h.getxAxis().getBin(endChannelForFit);

		double maxCounts = 0;
		int maxBin = 0;
		for (int i=startBinForFit; i<=endBinForFit; i++) {
			if (h.getBinContent(i) > maxCounts) {
				maxBin = i;
				maxCounts = h.getBinContent(i);
			};
		}

		double maxPos = h.getAxis().getBinCenter(maxBin);

		// adjust the range now that the max has been found
		// unless it's been set to custom value
//		if (minRange == 0.0) {
//			startChannelForFit = maxPos*0.5;
//		}
		if (maxRange == 0.0) {
			endChannelForFit = maxPos+GM_HIST_MAX[layer_index]*0.4;
			if (endChannelForFit > 0.9*GM_HIST_MAX[layer_index]) {
				endChannelForFit = 0.9*GM_HIST_MAX[layer_index];
			}	
		}

		F1D gmFunc = dataGroups.getItem(sector,layer,paddle).getF1D("gmFunc");
		gmFunc.setRange(startChannelForFit, endChannelForFit);

		gmFunc.setParameter(0, maxCounts*0.5);
		gmFunc.setParameter(1, maxPos);
		gmFunc.setParameter(2, 200.0);
		gmFunc.setParLimits(2, 0.0,400.0);
		gmFunc.setParameter(3, maxCounts*0.5);
		gmFunc.setParameter(4, -0.001);

		try {	
			DataFitter.fit(gmFunc, h, "RNQ");

		} catch (Exception e) {
			System.out.println("Fit error with sector "+sector+" layer "+layer+" paddle "+paddle);
			e.printStackTrace();
		}

	}

	public void fitLogRatio(int sector, int layer, int paddle,
			double minRange, double maxRange){

		H1F h = dataGroups.getItem(sector,layer,paddle).getH1F("logratio");

		// calculate the mean value using portion of the histogram where counts are > 0.2 * max counts
		
		double sum =0.0;
		double sumWeight =0.0;
		double sumSquare =0.0;
		int maxBin = h.getMaximumBin();
		double maxCounts = h.getBinContent(maxBin);
		int nBins = h.getAxis().getNBins();
		int lowThresholdBin = 0;
		int highThresholdBin = nBins-1;
		
		// find the bin left of max bin where counts drop to 0.2 * max
		for (int i=maxBin; i>0; i--) {

			if (h.getBinContent(i) < LR_THRESHOLD_FRACTION*maxCounts) {
				lowThresholdBin = i;
				break;
			}
		}

		// find the bin right of max bin where counts drop to 0.2 * max
		for (int i=maxBin; i<nBins; i++) {

			if (h.getBinContent(i) < LR_THRESHOLD_FRACTION*maxCounts) {
				highThresholdBin = i;
				break;
			}
		}

		// include the values in the sum if we're within the thresholds
		for (int i=lowThresholdBin; i<=highThresholdBin; i++) {

			double value=h.getBinContent(i);
			double middle=h.getAxis().getBinCenter(i);

			sum+=value;			
			sumWeight+=value*middle;
			sumSquare+=value*middle*middle;
		}			

		double logRatioMean = 0.0;
		double logRatioError = 0.0;

		if (sum>0) {
			logRatioMean=sumWeight/sum;
			logRatioError=(1/Math.sqrt(sum))*Math.sqrt((sumSquare/sum)-(logRatioMean*logRatioMean));
		}
		else {
			logRatioMean=0.0;
			logRatioError=0.0;
		}
		
		// store the function showing the width over which mean is calculated
		F1D lrFunc = dataGroups.getItem(sector,layer,paddle).getF1D("lrFunc");
		lrFunc.setRange(h.getAxis().getBinCenter(lowThresholdBin), h.getAxis().getBinCenter(highThresholdBin));

		lrFunc.setParameter(0, LR_THRESHOLD_FRACTION*maxCounts); // height to draw line at

		// put the constants in the list
		Double[] consts = constants.getItem(sector, layer, paddle);
		consts[LR_CENTROID] = logRatioMean;
		consts[LR_ERROR] = logRatioError;

	}

	public void customFit(int sector, int layer, int paddle){

		String[] fields = {"Min range for geometric mean fit:", "Max range for geometric mean fit:", "SPACE",
						   "Override MIP channel:", "Override MIP channel uncertainty:","SPACE",
						   "Override Log ratio:", "Override Log ratio uncertainty:"};
		TOFCustomFitPanel panel = new TOFCustomFitPanel(fields);

		int result = JOptionPane.showConfirmDialog(null, panel, 
				"Adjust Fit / Override for paddle "+paddle, JOptionPane.OK_CANCEL_OPTION);
		if (result == JOptionPane.OK_OPTION) {

			double minRange = toDouble(panel.textFields[0].getText());
			double maxRange = toDouble(panel.textFields[1].getText());
			double overrideGM = toDouble(panel.textFields[2].getText());
			double overrideGMUnc = toDouble(panel.textFields[3].getText());			
			double overrideLR = toDouble(panel.textFields[4].getText());
			double overrideLRUnc = toDouble(panel.textFields[5].getText());			
			
			// save the override values
			Double[] consts = constants.getItem(sector, layer, paddle);
			consts[GEOMEAN_OVERRIDE] = overrideGM;
			consts[GEOMEAN_UNC_OVERRIDE] = overrideGMUnc;
			consts[LOGRATIO_OVERRIDE] = overrideLR;
			consts[LOGRATIO_UNC_OVERRIDE] = overrideLRUnc;

			fitGeoMean(sector, layer, paddle, minRange, maxRange);

			// update the table
			saveRow(sector,layer,paddle);
			calib.fireTableDataChanged();
			
		}	 
	}

	
	public double getMipChannel(int sector, int layer, int paddle) {

		double mipChannel = 0.0;
		double overrideVal = 0.0;

		// has the value been overridden?
		if (constants.hasItem(sector,layer,paddle)) {
			overrideVal = constants.getItem(sector, layer, paddle)[GEOMEAN_OVERRIDE];
		}

		if (overrideVal != 0.0) {
			mipChannel = overrideVal;
		}
		else {
			if (dataGroups.hasItem(sector, layer, paddle)) {
				F1D f = dataGroups.getItem(sector,layer,paddle).getF1D("gmFunc");
				return f.getParameter(1);
			}
		}
		return mipChannel;
	}
	
	public double getMipChannelUnc(int sector, int layer, int paddle) {

		double mipChannelUnc = 0.0;
		double overrideVal = 0.0;

		// has the value been overridden?
		if (constants.hasItem(sector,layer,paddle)) {
			overrideVal = constants.getItem(sector, layer, paddle)[GEOMEAN_UNC_OVERRIDE];
		}

		if (overrideVal != 0.0) {
			mipChannelUnc = overrideVal;
		}
		else {
			if (dataGroups.hasItem(sector, layer, paddle)) {
				F1D f = dataGroups.getItem(sector,layer,paddle).getF1D("gmFunc");
				mipChannelUnc = f.parameter(1).error();
				if (Double.isInfinite(mipChannelUnc)){
					mipChannelUnc = 9999.0;
				}
			}
			else {
				mipChannelUnc = 0.0;
			}
		}
		return mipChannelUnc;

	}	
	
	public double getLogRatio(int sector, int layer, int paddle) {
	
		double logRatio = 0.0;
		// has the value been overridden?
		double overrideVal = constants.getItem(sector, layer, paddle)[LOGRATIO_OVERRIDE];
		
		if (overrideVal != 0.0) {
			logRatio = overrideVal;
		}
		else if (constants.hasItem(sector, layer, paddle)) {
			
			logRatio = constants.getItem(sector, layer, paddle)[LR_CENTROID];
		}
		else {
			logRatio = 0.0;
		}
				
		return logRatio;
		
	}
	
	public double getLogRatioUnc(int sector, int layer, int paddle) {
		
		double logRatioUnc= 0.0;
		// has the value been overridden?
		double overrideVal = constants.getItem(sector, layer, paddle)[LOGRATIO_UNC_OVERRIDE];
		
		if (overrideVal != 0.0) {
			logRatioUnc = overrideVal;
		}
		else if (constants.hasItem(sector, layer, paddle)) {
			
			logRatioUnc = constants.getItem(sector, layer, paddle)[LR_ERROR];
		}
		else {
			logRatioUnc = 0.0;
		}
				
		return logRatioUnc;
	}	
	
	private void saveRow(int sector, int layer, int paddle) {
		calib.setDoubleValue(getMipChannel(sector,layer,paddle),
				"mipa_left", sector, layer, paddle);
		calib.setDoubleValue(getMipChannel(sector,layer,paddle),
				"mipa_right", sector, layer, paddle);
		calib.setDoubleValue(getMipChannelUnc(sector,layer,paddle),
				"mipa_left_err", sector, layer, paddle);
		calib.setDoubleValue(getMipChannelUnc(sector,layer,paddle),
				"mipa_right_err", sector, layer, paddle);
		calib.setDoubleValue(getLogRatio(sector,layer,paddle),
				"logratio", sector, layer, paddle);
		calib.setDoubleValue(getLogRatioUnc(sector,layer,paddle),
				"logratio_err", sector, layer, paddle);
		
	}

	@Override
	public void save() {

		for (int sector = 1; sector <= 6; sector++) {
			for (int layer = 1; layer <= 3; layer++) {
				int layer_index = layer - 1;
				for (int paddle = 1; paddle <= NUM_PADDLES[layer_index]; paddle++) {
					calib.addEntry(sector, layer, paddle);
					saveRow(sector,layer,paddle);
				}
			}
		}
		calib.save("FTOF_CALIB_HV.txt");
	}
	
	@Override
	public DataGroup getSummary(int sector, int layer) {
				
		int layer_index = layer-1;
		double[] paddleNumbers = new double[NUM_PADDLES[layer_index]];
		double[] paddleUncs = new double[NUM_PADDLES[layer_index]];
		double[] MIPChannels = new double[NUM_PADDLES[layer_index]];
		double[] MIPChannelUncs = new double[NUM_PADDLES[layer_index]];
		double[] LogRatios = new double[NUM_PADDLES[layer_index]];
		double[] LogRatioUncs = new double[NUM_PADDLES[layer_index]];

		for (int p = 1; p <= NUM_PADDLES[layer_index]; p++) {

			paddleNumbers[p - 1] = (double) p;
			paddleUncs[p - 1] = 0.0;
			MIPChannels[p - 1] = getMipChannel(sector, layer, p);
			MIPChannelUncs[p - 1] = getMipChannelUnc(sector, layer, p);
			LogRatios[p - 1] = getLogRatio(sector, layer, p);
			LogRatioUncs[p - 1] = getLogRatioUnc(sector, layer, p);
		}

		GraphErrors gmSumm = new GraphErrors("gmSumm", paddleNumbers,
				MIPChannels, paddleUncs, MIPChannelUncs);
		
//		summary.setTitle("MIP Channel: "
//				+ LAYER_NAME[paddle - 1] + " Sector "
//				+ sector);
//		summary.setXTitle("Paddle Number");
//		summary.setYTitle("MIP Channel");
//		summary.setMarkerSize(5);
//		summary.setMarkerStyle(2);
//		

		GraphErrors lrSumm = new GraphErrors("lrSumm", paddleNumbers,
				LogRatios, paddleUncs, LogRatioUncs);
//		summary.setTitle("Log ratio: "
//				+ TOFCalibration.LAYER_NAME[paddle - 1] + " Sector "
//				+ sector);
//		summary.setXTitle("Paddle Number");
//		summary.setYTitle("Log ratio");
//		summary.setMarkerSize(5);
//		summary.setMarkerStyle(2);

		DataGroup dg = new DataGroup(2,1);
		dg.addDataSet(gmSumm, GEOMEAN);
		dg.addDataSet(lrSumm, LOGRATIO);
		
		return dg;
		
	}
	
	@Override
	public boolean isGoodPaddle(int sector, int layer, int paddle) {

		return (getMipChannel(sector,layer,paddle) >= EXPECTED_MIP_CHANNEL[layer-1]-ALLOWED_MIP_DIFF
			&&
			getMipChannel(sector,layer,paddle) <= EXPECTED_MIP_CHANNEL[layer-1]+ALLOWED_MIP_DIFF);

	}

	@Override
	public void drawPlots(int sector, int layer, int paddle, EmbeddedCanvas canvas) {

		H1F fitHist = dataGroups.getItem(sector,layer,paddle).getH1F("geomean");
		canvas.draw(fitHist);
		
		F1D fitFunc = dataGroups.getItem(sector,layer,paddle).getF1D("gmFunc");
		canvas.draw(fitFunc, "same");

	}

}