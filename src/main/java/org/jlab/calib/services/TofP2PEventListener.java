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


	// Test hists from Raffaella's macro
	// PID
	public static H1F hi_vertex_dt = new H1F("hi_vertex_dt", "hi_vertex_dt", 200, -3.0, 3.0); 
	public static H1F hi_elec_t0 = new H1F("hi_elec_t0", "hi_elec_t0", 200, -3.0, 3.0); 
	public static H1F hi_pion_t0 = new H1F("hi_pion_t0", "hi_pion_t0", 200, -3.0, 3.0); 

	public static H2F hPaddles = new H2F("hPaddles", "hPaddles", 600, 0.0, 600.0, 600, 0.0, 600.0);
	public static H1F eSect1 = new H1F("eSect1","eSect1",6,0.5,6.5);
	public static H1F eSect = new H1F("eSect","eSect",6, 0.5,6.5);
	public static H1F pSect = new H1F("pSect","pSect",6, 0.5,6.5);

	public static H1F eventTracks = new H1F("eventTracks","Number of tracks per event",
			20,-0.5, 20.5);

	private static final int ELECTRON = 11;
	private static final int PION = 211;

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
	
	private String showPlotType = "VERTEX_RF";

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

		// Raffaellas test hists
		hi_vertex_dt.setOptStat(1111);       
		hi_vertex_dt.setTitleX("#DeltaT (ns)"); 
		hi_vertex_dt.setTitleY("Counts");
		hi_elec_t0.setOptStat(1111);       hi_elec_t0.setTitleX("#DeltaT (ns)"); hi_elec_t0.setTitleY("Counts");
		hi_pion_t0.setOptStat(1111);       hi_pion_t0.setTitleX("#DeltaT (ns)"); hi_pion_t0.setTitleY("Counts");

		// LC perform init processing
		for (int sector = 1; sector <= 6; sector++) {
			for (int layer = 1; layer <= 3; layer++) {
				int layer_index = layer - 1;
				for (int paddle = 1; paddle <= NUM_PADDLES[layer_index]; paddle++) {

					DataGroup dg = new DataGroup(2,3);

					// create all the histograms and functions
					H1F fineHistRaw = 
							new H1F("fineHistRaw","Fine offset Sector "+sector+" Layer "+" Paddle "+paddle,
									100, -2.0, 2.0);
					fineHistRaw.setTitleX("RF time - vertex time modulo beam bucket (ns)");
					dg.addDataSet(fineHistRaw, 0);

					H1F fineHist = 
							new H1F("fineHist","Fine offset Sector "+sector+" Layer "+" Paddle "+paddle,
									100, -2.0, 2.0);
					fineHist.setTitleX("RF time - vertex time modulo beam bucket (ns)");
					dg.addDataSet(fineHist, 1);

					// create a dummy function in case there's no data to fit 
					F1D fineFunc = new F1D("fineFunc","[amp]*gaus(x,[mean],[sigma] + [a])", -1.0, 1.0);
					fineFunc.setLineColor(FUNC_COLOUR);
					fineFunc.setLineWidth(FUNC_LINE_WIDTH);
					dg.addDataSet(fineFunc, 1);

					H1F refHistAll = 
							new H1F("refHistAll","Offset to reference paddle Sector "+sector+" Paddle "+paddle, 
									99,-49.5*BEAM_BUCKET,49.5*BEAM_BUCKET);
					refHistAll.setTitleX("#Delta t (TOF) (ns)");
					dg.addDataSet(refHistAll, 2);

					H1F refHist = 
							new H1F("refHist","Offset to reference paddle Sector "+sector+" Paddle "+paddle, 
									99,-49.5*BEAM_BUCKET,49.5*BEAM_BUCKET);
					refHist.setTitleX("#Delta t (vertex) (ns)");
					dg.addDataSet(refHist, 3);

					H1F statHist = 
							new H1F("statHist","Number of coincidences", 540,0.5,540.5);
					statHist.setTitleX("Number of coincidences - Paddle number");
					dg.addDataSet(statHist, 4);

					H1F Deltai0j0kl = 
							new H1F("Deltai0j0kl","Delta i0,j0, "+sector+" Paddle "+paddle, 
									99,-49.5*BEAM_BUCKET,49.5*BEAM_BUCKET);
					Deltai0j0kl.setTitleX("#Delta i0, j0, "+sector+" "+paddle+" (ns)");
					dg.addDataSet(Deltai0j0kl, 5);
					
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

			// fill the fine hists
			if (pad.trackFound()) {
				dataGroups.getItem(sector,layer,component).getH1F("fineHistRaw").fill(
						(pad.refTime()+(1000*BEAM_BUCKET) + (0.5*BEAM_BUCKET))%BEAM_BUCKET - 0.5*BEAM_BUCKET);
				dataGroups.getItem(sector,layer,component).getH1F("fineHist").fill(
						(pad.refTime()+(1000*BEAM_BUCKET) + (0.5*BEAM_BUCKET))%BEAM_BUCKET - 0.5*BEAM_BUCKET);
				numTracks++;
			}

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
							jpad.getDescriptor().getComponent()).getH1F("refHistAll").fill(pad.TOF_TIME - jpad.TOF_TIME);
					
					if (jpad.trackFound() && pad.trackFound() && pad.TRACK_ID != jpad.TRACK_ID) {

						System.out.println("pad");
						pad.show();
						System.out.println("jpad");
						jpad.show();
						dataGroups.getItem(jpad.getDescriptor().getSector(),
								jpad.getDescriptor().getLayer(),
								jpad.getDescriptor().getComponent()).getH1F("refHist").fill(pad.startTime() - jpad.startTime());
						
						// Store the Ci0j0k0l0
						if (jSector==PREF_SECTOR && jLayer==PREF_LAYER && jComponent==PREF_PADDLE) {
							System.out.println("Storing Ci0j0k0l0");
							Ci0j0k0l0.add(pad.startTime() - jpad.startTime());
						}
						// Store the Ci0j0kl
						System.out.println("Storing Ci0j0"+jSector+jLayer+jComponent); 
						Ci0j0kl.getItem(jSector,jLayer,jComponent).add(pad.startTime()-jpad.startTime());
					}

				}
			}
		}

		if (numTracks > 1) {
			eventTracks.fill(numTracks);
		}

		// fill the histograms for the pions if an electron in the reference paddle is contained in the event
		//        if (electronFound) {
		//            for (TOFPaddle pad : paddleList) {
		//
		//                if (pad.PARTICLE_ID == 211) {
		//                    dataGroups.getItem(pad.getDescriptor().getSector(),
		//                    				   pad.getDescriptor().getLayer(),
		//                    				   pad.getDescriptor().getComponent()).getH1F("refHist").fill(eRefPaddle.startTime() - pad.startTime());                                    
		//                }
		//            }
		//        }
	}    


	@Override
	public void timerUpdate() {
		// don't analyze until the end or it will mess up the fine hists
		save();
		calib.fireTableDataChanged();
	}
		
//	@Override
//	public void analyze() {
//		super.analyze();
//		
//		// show stats on number of tracks
//		TCanvas tracks = new TCanvas("tracks", 700, 1000);
//		tracks.cd(0);
//		tracks.draw(eventTracks);
//	}

//	@Override
//	public void analyze() {
		
	// Raffaella''s test hists
	//        TCanvas vt = new TCanvas("vt", 700, 1000);
	//        vt.divide(1,3);
	//        vt.getCanvas().setGridX(false); vt.getCanvas().setGridY(false);
	//        vt.getCanvas().setAxisFontSize(18);
	//        vt.getCanvas().setAxisTitleSize(24);
	//        vt.cd(0);
	//        F1D f1 = new F1D("f1","[amp]*gaus(x,[mean],[sigma])", -1.0, 1.0);
	//        f1.setParameter(0, 100.0);
	//        f1.setParameter(1, 0.0);
	//        f1.setParameter(2, 0.3);
	//        f1.setLineWidth(2);
	//        f1.setLineColor(2);
	//        f1.setOptStat("1111");
	//        DataFitter.fit(f1, hi_vertex_dt, "Q"); //No options uses error for sigma
	//        vt.draw(hi_vertex_dt);
	//        vt.cd(1);
	//        vt.draw(hi_elec_t0);
	//        vt.cd(2);
	//        vt.draw(hi_pion_t0);
	//
	//        TCanvas paddles = new TCanvas("paddles", 800, 800);
	//        paddles.cd(0);
	//        paddles.draw(hPaddles);
	//
	//        TCanvas sects = new TCanvas("sects", 800, 800);
	//        sects.divide(1, 3);
	//        sects.cd(0);
	//        sects.draw(eSect);
	//        sects.cd(1);
	//        sects.draw(pSect);
	//        sects.cd(2);
	//        sects.draw(eSect1);
	//
	//        save();
	//        calib.fireTableDataChanged();
	//    }

	@Override
	public void fit(int sector, int layer, int paddle, double minRange, double maxRange) {
		
		H1F fineHist = dataGroups.getItem(sector,layer,paddle).getH1F("fineHist");
		
		int maxBin = fineHist.getMaximumBin();
		double maxPos = fineHist.getxAxis().getBinCenter(maxBin);

		// arrangeFine

		// if maxPos > 0.65 move bin contents of (-1,0) to (1,2)
		if (maxPos > 0.65) {
			int iBin=fineHist.getxAxis().getBin(-1.0+1.0e-10);
			int jBin=fineHist.getxAxis().getBin(1.0+1.0e-10);
			do {
				fineHist.setBinContent(jBin, fineHist.getBinContent(iBin));
				fineHist.setBinContent(iBin,0);
				iBin++;
				jBin++;
			}
			while (fineHist.getXaxis().getBinCenter(iBin) < 0);
		}

		// if maxPos < -0.65 move bin contents of (0,1) to (-2,-1)
		if (maxPos < -0.65) {
			int iBin=fineHist.getxAxis().getBin(0.0+1.0e-10);
			int jBin=fineHist.getxAxis().getBin(-2.0+1.0e-10);
			do {
				fineHist.setBinContent(jBin, fineHist.getBinContent(iBin));
				fineHist.setBinContent(iBin,0);
				iBin++;
				jBin++;
			}
			while (fineHist.getXaxis().getBinCenter(iBin) < 1);
		}

		// fit gaussian
		F1D fineFunc = dataGroups.getItem(sector,layer,paddle).getF1D("fineFunc");

		fineFunc.setRange(maxPos-0.5, maxPos+0.5);
		fineFunc.setParameter(0, fineHist.getBinContent(maxBin));
		fineFunc.setParameter(1, maxPos);
		fineFunc.setParameter(2, 0.5);

		try {
			DataFitter.fit(fineFunc, fineHist, "RNQ");
			fineHist.setTitle(fineHist.getTitle() + " Fine offset = " + formatDouble(fineFunc.getParameter(1)));
		}
		catch(Exception ex) {
			ex.printStackTrace();
		}	
		
		fineFunc.setOptStat(1001);
		
		// Fill the Deltai0j0kl histogram		
		System.out.println("SLC "+sector+layer+paddle);
		System.out.println("Ci0j0k0l0 size "+Ci0j0k0l0.size());
		System.out.println("Ci0j0kl size "+Ci0j0kl.getItem(sector,layer,paddle).size());
		
		for (double c1: Ci0j0k0l0) {
			for (double c2: Ci0j0kl.getItem(sector,layer,paddle)) {
				dataGroups.getItem(sector,layer,paddle).getH1F("Deltai0j0kl").fill(c1-c2);
			}
		}
		
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
			H1F refHist = dataGroups.getItem(sector,layer,paddle).getH1F("refHist");			
			int maxBin = refHist.getMaximumBin();
			double refOffset = 0.0;
			if (refHist.getEntries() != 0) {
				refOffset = refHist.getXaxis().getBinCenter(maxBin);
			}
			
			F1D fineFunc = dataGroups.getItem(sector,layer,paddle).getF1D("fineFunc");
			H1F fineHist = dataGroups.getItem(sector,layer,paddle).getH1F("fineHist");
			double fineOffset = 0.0;
			if (fineHist.getEntries() != 0){
				fineOffset= fineFunc.getParameter(1);
			}
			//offset = refOffset + fineOffset;
			offset = fineOffset;

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

		showPlotType = "VERTEX_RF";
		stepName = "Vertex time - RF";
		super.showPlots(sector, layer);
		showPlotType = "DT_VERTEX";
		stepName = "delta t (vertex)";
		super.showPlots(sector, layer);

	}

	@Override
	public void drawPlots(int sector, int layer, int paddle, EmbeddedCanvas canvas) {

		H1F hist = new H1F();
		F1D func = new F1D("fineFunc");
		if (showPlotType == "VERTEX_RF") { 
			hist = dataGroups.getItem(sector,layer,paddle).getH1F("fineHist");
			func = dataGroups.getItem(sector,layer,paddle).getF1D("fineFunc");
			hist.setTitle("Paddle "+paddle);
			hist.setTitleX("");
			hist.setTitleY("");
			canvas.draw(hist); 
			canvas.draw(func, "same");

		}
		else {
			hist = dataGroups.getItem(sector,layer,paddle).getH1F("refHist");
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