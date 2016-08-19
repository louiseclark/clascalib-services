package org.jlab.calib.services; 

import java.awt.BorderLayout;
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
import org.jlab.io.base.DataEvent;
import org.jlab.io.base.DataEventType;
import org.jlab.io.evio.EvioDataEvent;
import org.jlab.io.task.DataSourceProcessorPane;
import org.jlab.io.task.IDataEventListener;
import org.jlab.utils.groups.IndexedList;

public class TofVeffEventListener extends TOFCalibrationEngine {

	public final int VEFF_OVERRIDE = 0;
	public final int VEFF_UNC_OVERRIDE = 1;
	
	public final double EXPECTED_VEFF = 16.0;
	public final double ALLOWED_VEFF_DIFF = 0.1;
	
	public TofVeffEventListener() {

		calib = new CalibrationConstants(3,
				"veff_left/F:veff_right/F:veff_left_err/F:veff_right_err/F");
		calib.setName("/calibration/ftof/effective_velocity");
		calib.setPrecision(3);
		
		// assign constraints to all paddles
		// effective velocity to be within 10% of 16.0 cm/ns
		calib.addConstraint(3, EXPECTED_VEFF*(1-ALLOWED_VEFF_DIFF),
							   EXPECTED_VEFF*(1+ALLOWED_VEFF_DIFF));
		calib.addConstraint(4, EXPECTED_VEFF*(1-ALLOWED_VEFF_DIFF),
							   EXPECTED_VEFF*(1+ALLOWED_VEFF_DIFF));
		
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
					
					H2F hist = 
					new H2F("veff",
							"veff",
							numBins, min, max, 
							200, -10.0, 10.0);
					
					hist.setName("veff");
					hist.setTitle("Half Time Diff vs Position : " + LAYER_NAME[layer_index] 
							+ " Sector "+sector+" Paddle "+paddle);
					hist.setXTitle("Position (cm)");
					hist.setYTitle("Half Time Diff (ns)");

					// create all the functions and graphs
					F1D veffFunc = new F1D("veffFunc", "[a]+[b]*x", -250.0, 250.0);
					GraphErrors veffGraph = new GraphErrors();
					veffGraph.setName("veffGraph");
					
					DataGroup dg = new DataGroup(2,1);
					dg.addDataSet(hist, 0);
					dg.addDataSet(veffGraph, 1);
					dg.addDataSet(veffFunc, 1);
					dataGroups.add(dg, sector,layer,paddle);
					
					// initialize the constants array
					Double[] consts = {0.0, 0.0};
					// override values
					
					constants.add(consts, sector, layer, paddle);
					
				}
			}
		}
	}

	@Override
	public void processEvent(DataEvent event) {

		List<TOFPaddle> paddleList = DataProvider.getPaddleList(event);
		for (TOFPaddle paddle : paddleList) {

			int sector = paddle.getDescriptor().getSector();
			int layer = paddle.getDescriptor().getLayer();
			int component = paddle.getDescriptor().getComponent();

			if (paddle.includeInVeff()) {
				dataGroups.getItem(sector,layer,component).getH2F("veff").fill(
					paddle.paddleY(), paddle.halfTimeDiff());
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
		
		if (minRange != 0.0 && maxRange != 0.0) {

			// use custom values for fit
			lowLimit = minRange;
			highLimit = maxRange;
		}
		else {
			
			lowLimit = paddleLength(sector,layer,paddle) * -0.4;
			highLimit = paddleLength(sector,layer,paddle) * 0.4;
		}

		F1D veffFunc = dataGroups.getItem(sector,layer,paddle).getF1D("veffFunc");
		veffFunc.setRange(lowLimit, highLimit);
		
		veffFunc.setParameter(0, 0.0);
		veffFunc.setParameter(1, 1.0/16.0);
		veffFunc.setParLimits(0, -5.0, 5.0);
		veffFunc.setParLimits(1, 1.0/20.0, 1.0/12.0);
		
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
		double gradient = dataGroups.getItem(sector,layer,paddle).getF1D("veffFunc") 
				.getParameter(1);
		if (gradient==0.0) {
			veff=0.0;
		}
		else {
			veff = 1/gradient;
		}
		return veff;
	}
	
	public Double getVeffError(int sector, int layer, int paddle){
		
		double veffError = 0.0;
		
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

		return veffError;
	}	

	private void saveRow(int sector, int layer, int paddle) {
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
		calib.save("FTOF_CALIB_VEFF.txt");
	}

	@Override
	public List<CalibrationConstants> getCalibrationConstants() {
		
		return Arrays.asList(calib);
	}

	@Override
	public IndexedList<DataGroup>  getDataGroup(){
		return dataGroups;
	}
//
//	public JPanel getView(CalibrationConstantsListener listener, EmbeddedCanvas canvas) {
//		
//		JPanel panel = new JPanel();
//		
//	    JSplitPane          splitPane = null;
//	    CalibrationConstantsView ccview = null;
//	    
//	    panel.setLayout(new BorderLayout());
//        splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
//        ccview = new CalibrationConstantsView();
//        ccview.addConstants(this.getCalibrationConstants().get(0),listener);
//        splitPane.setTopComponent(canvas);
//        splitPane.setBottomComponent(ccview);
//        panel.add(splitPane,BorderLayout.CENTER);
//        splitPane.setDividerLocation(0.5);
//		
//		return panel;
//	}
}
