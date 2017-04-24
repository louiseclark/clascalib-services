package org.jlab.calib.services;


import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JOptionPane;

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
import org.jlab.groot.ui.TCanvas;
import org.jlab.io.base.DataEvent;
import org.jlab.utils.groups.IndexedList;

public class TofP2PEventListener extends TOFCalibrationEngine {

	// indices for constants
	public final int OFFSET_OVERRIDE = 0;

	// Electron Reference paddle
	public final int REF_SECTOR = 2;
	public final int REF_LAYER = 1;
	public final int REF_PADDLE = 13; 

	// Pion reference paddle
	public final int PREF_SECTOR = 2;
	public final int PREF_LAYER = 1;
	public final int PREF_PADDLE = 20; 	
	
	ArrayList<Double> Ci0j0k0l0 = new ArrayList<Double>();
	IndexedList<ArrayList<Double>> Ci0j0kl = new IndexedList<ArrayList<Double>>(3);
			
	final double MAX_OFFSET = 10.0;
	
	private String showPlotType = "VERTEX_DT";

	public TofP2PEventListener() {

		stepName = "P2P";
		fileNamePrefix = "FTOF_CALIB_P2P_";
		// get file name here so that each timer update overwrites it
		filename = nextFileName();

		calib = 
				new CalibrationConstants(3,
						"paddle2paddle/F");

		calib.setName("/calibration/ftof/timing_offset/P2P");
		calib.setPrecision(3);

		// assign constraints
		calib.addConstraint(3, -MAX_OFFSET, MAX_OFFSET);

	}
	
	public void populatePrevCalib() {

		if (calDBSource==CAL_FILE) {

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
					double p2p = Double.parseDouble(lineValues[4]);

					p2pValues.addEntry(sector, layer, paddle);
					p2pValues.setDoubleValue(p2p,
							"paddle2paddle", sector, layer, paddle);
					
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
						p2pValues.addEntry(sector, layer, paddle);
						p2pValues.setDoubleValue(0.0,
								"paddle2paddle", sector, layer, paddle);
						System.out.println("p2p check "+sector+layer+paddle+" "+
								TOFCalibrationEngine.p2pValues.getDoubleValue("paddle2paddle",
								sector,layer,paddle));						
					}
				}
			}			
		}
		else if (calDBSource==CAL_DB) {
			DatabaseConstantProvider dcp = new DatabaseConstantProvider(prevCalRunNo, "default");
			p2pValues = dcp.readConstants("/calibration/ftof/timing_offset");
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
		for (int i=0; i<p2pValues.getRowCount(); i++) {
			String line = new String();
			for (int j=0; j<p2pValues.getColumnCount(); j++) {
				line = line+p2pValues.getValueAt(i, j);
				if (j<p2pValues.getColumnCount()-1) {
					line = line+" ";
				}
			}
			System.out.println(line);
		}
		
		// create the histograms for the first iteration
		createHists();
	}

	public void createHists() {

		for (int sector = 1; sector <= 6; sector++) {
			for (int layer = 1; layer <= 3; layer++) {
				int layer_index = layer - 1;
				for (int paddle = 1; paddle <= NUM_PADDLES[layer_index]; paddle++) {

					DataGroup dg = new DataGroup(2,2);

					// create all the histograms and functions
					H1F refHistAll = 
							new H1F("refHistAll","Offset to reference paddle Sector "+sector+" Paddle "+paddle, 
									99,-49.5*BEAM_BUCKET,49.5*BEAM_BUCKET);
					refHistAll.setTitleX("#Delta t (TOF) (ns)");
					dg.addDataSet(refHistAll, 0);

					H1F refHist = 
							new H1F("refHist","Offset to reference paddle Sector "+sector+" Paddle "+paddle, 
									99,-49.5*BEAM_BUCKET,49.5*BEAM_BUCKET);
					refHist.setTitleX("#Delta t (vertex) (ns)");
					dg.addDataSet(refHist, 1);

					H1F statHist = 
							new H1F("statHist","Number of coincidences", 540,0.5,540.5);
					statHist.setTitleX("Number of coincidences - Paddle number");
					dg.addDataSet(statHist, 2);

//					H1F Deltai0j0kl = 
//							new H1F("Deltai0j0kl","Delta i0,j0, "+sector+" Paddle "+paddle, 
//									99,-49.5*BEAM_BUCKET,49.5*BEAM_BUCKET);
//					Deltai0j0kl.setTitleX("#Delta i0, j0, "+sector+" "+paddle+" (ns)");
//					dg.addDataSet(Deltai0j0kl, 3);
					
					dataGroups.add(dg,sector,layer,paddle);    

					// initialize the constants array
					Double[] consts = {UNDEFINED_OVERRIDE};
					// override values
					constants.add(consts, sector, layer, paddle);
					
					// create array to store Ci0j0kl
					ArrayList<Double> entry = new ArrayList<Double>();
					Ci0j0kl.add(entry, sector,layer,paddle);
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

		int numTracks = 0;

		for (TOFPaddle pad : paddleList) {
			
			// only include p >1.0 so that the beta=1 assumption is reasonable
            if (pad.P < 1.0) continue;

			int sector = pad.getDescriptor().getSector();
			int layer = pad.getDescriptor().getLayer();
			int component = pad.getDescriptor().getComponent();

			for (TOFPaddle jpad : paddleList) {
				
                // only include p >1.0 so that the beta=1 assumption is reasonable
                if (jpad.P < 1.0) continue;
                
                int jSector = jpad.getDescriptor().getSector();
                int jLayer = jpad.getDescriptor().getLayer();
                int jComponent = jpad.getDescriptor().getComponent();

				// fill hist of number of coincidences with other paddles
				if (pad.trackFound() && jpad.trackFound()
						&& pad.paddleNumber() != jpad.paddleNumber()
						&& pad.TRACK_ID != jpad.TRACK_ID) {
					dataGroups.getItem(sector,layer,component).getH1F("statHist").fill(jpad.paddleNumber());
				}

				// Look for electron in reference paddle
				if (sector==REF_SECTOR && layer==REF_LAYER && component==REF_PADDLE) { 

					dataGroups.getItem(jpad.getDescriptor().getSector(),
							jpad.getDescriptor().getLayer(),
							jpad.getDescriptor().getComponent()).getH1F("refHistAll").fill(pad.tofTimeRFCorr() - jpad.tofTimeRFCorr());
					
					if (jpad.trackFound() && pad.trackFound() && pad.TRACK_ID != jpad.TRACK_ID) {

//						System.out.println("pad");
//						pad.show();
//						System.out.println("jpad");
//						jpad.show();
						dataGroups.getItem(jpad.getDescriptor().getSector(),
								jpad.getDescriptor().getLayer(),
								jpad.getDescriptor().getComponent()).getH1F("refHist").fill(pad.startTimeRFCorr() - jpad.startTimeRFCorr());
						
						// Store the Ci0j0k0l0
//						if (jSector==PREF_SECTOR && jLayer==PREF_LAYER && jComponent==PREF_PADDLE) {
//							System.out.println("Storing Ci0j0k0l0");
//							Ci0j0k0l0.add(pad.startTime() - jpad.startTime());
//						}
//						// Store the Ci0j0kl
//						System.out.println("Storing Ci0j0"+jSector+jLayer+jComponent); 
//						Ci0j0kl.getItem(jSector,jLayer,jComponent).add(pad.startTime()-jpad.startTime());
					}

				}
			}
		}
	}    


	@Override
	public void timerUpdate() {
		// don't analyze until the end or it will mess up the fine hists
		save();
		calib.fireTableDataChanged();
	}
		
	@Override
	public void fit(int sector, int layer, int paddle, double minRange, double maxRange) {
		
		// Fill the Deltai0j0kl histogram		
//		System.out.println("SLC "+sector+layer+paddle);
//		System.out.println("Ci0j0k0l0 size "+Ci0j0k0l0.size());
//		System.out.println("Ci0j0kl size "+Ci0j0kl.getItem(sector,layer,paddle).size());
//		
//		for (double c1: Ci0j0k0l0) {
//			for (double c2: Ci0j0kl.getItem(sector,layer,paddle)) {
//				dataGroups.getItem(sector,layer,paddle).getH1F("Deltai0j0kl").fill(c1-c2);
//			}
//		}
		
	}

	private Double formatDouble(double val) {
		return Double.parseDouble(new DecimalFormat("0.000").format(val));
	}

	@Override
	public void customFit(int sector, int layer, int paddle){

		String[] fields = { "Override offset:"};
		TOFCustomFitPanel panel = new TOFCustomFitPanel(fields,sector,layer);

		int result = JOptionPane.showConfirmDialog(null, panel, 
				"Adjust Fit / Override for paddle "+paddle, JOptionPane.OK_CANCEL_OPTION);
		if (result == JOptionPane.OK_OPTION) {

			double override = toDouble(panel.textFields[0].getText());

			// save the override values
			Double[] consts = constants.getItem(sector, layer, paddle);
			consts[OFFSET_OVERRIDE] = override;

			// update the table
			saveRow(sector,layer,paddle);
			calib.fireTableDataChanged();

		}     
	}

	public Double getOffset(int sector, int layer, int paddle) {

		double offset = 0.0;
		double overrideVal = constants.getItem(sector, layer, paddle)[OFFSET_OVERRIDE];

		if (overrideVal != UNDEFINED_OVERRIDE) {
			offset = overrideVal;
		}
		else {
			H1F tofHist = dataGroups.getItem(sector,layer,paddle).getH1F("refHistAll");			
			int maxBin = tofHist.getMaximumBin();
			double tofOffset = 0.0;
			if (tofHist.getEntries() != 0) {
				tofOffset = tofHist.getXaxis().getBinCenter(maxBin);
			}
			
			offset = tofOffset;

		}
		return offset;
	}    

	@Override
	public void saveRow(int sector, int layer, int paddle) {

		calib.setDoubleValue(getOffset(sector,layer,paddle),
				"paddle2paddle", sector, layer, paddle);

	}
	
	@Override
	public void showPlots(int sector, int layer) {

		showPlotType = "TOF_DT";
		stepName = "Delta t (TOF)";
		super.showPlots(sector, layer);
		showPlotType = "VERTEX_DT";
		stepName = "Delta t (vertex)";
		super.showPlots(sector, layer);

	}

	@Override
	public void drawPlots(int sector, int layer, int paddle, EmbeddedCanvas canvas) {

		if (showPlotType == "TOF_DT") {
			H1F hist = dataGroups.getItem(sector,layer,paddle).getH1F("refHistAll");
			hist.setTitle("Paddle "+paddle);
			hist.setTitleX("");
			hist.setTitleY("");
			canvas.draw(hist); 
		}
		else {
			H1F hist = dataGroups.getItem(sector,layer,paddle).getH1F("refHist");
			hist.setTitle("Paddle "+paddle);
			hist.setTitleX("");
			hist.setTitleY("");
			canvas.draw(hist); 
		}
	}

	@Override
	public boolean isGoodPaddle(int sector, int layer, int paddle) {

		return (getOffset(sector,layer,paddle) >= -MAX_OFFSET
				&&
				getOffset(sector,layer,paddle) <= MAX_OFFSET);

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
			values[p - 1] = getOffset(sector, layer, p);
			valueUncs[p - 1] = 0.0;
		}

		GraphErrors summ = new GraphErrors("summ", paddleNumbers,
				values, paddleUncs, valueUncs);
		summ.setMarkerSize(MARKER_SIZE);
		summ.setLineThickness(MARKER_LINE_WIDTH);

		DataGroup dg = new DataGroup(1,1);
		dg.addDataSet(summ, 0);
		return dg;

	}


}