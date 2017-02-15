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


	private static final int ELECTRON = 0;
	private static final int PION = 1;

	public final int NUM_ITERATIONS = 5;

	public static List<TOFPaddlePair>     allPaddleList = new ArrayList<TOFPaddlePair>();

	// constants for indexing the histograms
	//	public final int FINE = 0;
	//	public final int CRUDE_FIRST = 1;
	//	public final int SECTOR = 1;
	//	public final int CRUDE_LAST = 1;
	//	public final int CRUDE_FIRST_AGAIN = 1;

	//	// indices for constants
	public final int CUMUL_OFFSET = 0;
	public final int OFFSET_OVERRIDE = 1;

	public final double TARGET_CENTRE = -25.0;
	public final double RF_STRUCTURE = 2.004;

	// *** using 1b so I can use same reference paddle as Haiyun while testing
	// *** change to 1a later
	public final int REF_PADDLE = 28;
	public final int REF_LAYER = 2;
	public final int NUM_FIRST_PADDLES = 25;

	final double MAX_OFFSET = 0.1;

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

		// read in the time walk values from the text file
		String inputFile = "/home/louise/workspace/clascalib-services/ftof.timing_offset.smeared.txt";

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
				double p2p = Double.parseDouble(lineValues[4]);

				p2pValues.add(p2p, sector, layer, paddle);

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

	@Override
	public void timerUpdate() {
		// only analyze at end of events for pP2P

	}

	@Override
	public void resetEventListener() {

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

					// data group format is

					DataGroup dg = new DataGroup(3,2);

					// create all the histograms
					// create hist for fine offset for every paddle
					H1F fineHist = 
							new H1F("fineHist","Fine Offset Sector "+sector+" Paddle "+paddle, 
									100, -2.0, 2.0);
					dg.addDataSet(fineHist,0);

					// create a dummy function in case there's no data to fit 
					F1D fineFunc = new F1D("fineFunc","[amp]*gaus(x,[mean],[sigma])", -1.0, 1.0);
					dg.addDataSet(fineFunc, 0);

					// crudeFirst offset hists for first 10 paddles in each sector in reference sector
					// these will be corrected to one reference paddle
					// crudeFirstAgain hists also for first 10 paddles in each sector in reference sector
					//if (layer==REF_LAYER && paddle <= NUM_FIRST_PADDLES) {
					H1F crudeFirstHist = 
							new H1F("crudeFirstHist","Crude First Offset Sector "+sector+" Paddle "+paddle, 
									99,-49.5*RF_STRUCTURE,49.5*RF_STRUCTURE);
					crudeFirstHist.setTitleX("Offset (ns)");
					dg.addDataSet(crudeFirstHist,1);

					H1F crudeFirstAgainHist = 
							new H1F("crudeFirstAgainHist","Crude First Again Offset Sector "+sector+" Paddle "+paddle, 
									99,-49.5*RF_STRUCTURE,49.5*RF_STRUCTURE);
					crudeFirstAgainHist.setTitleX("Offset (ns)");
					dg.addDataSet(crudeFirstAgainHist, 4);

					//}

					// crudeLast offset hists for paddles other than first 10 in each sector for layer 1
					// these will be corrected to one reference paddle
					//if (paddle > NUM_FIRST_PADDLES || layer != REF_LAYER) {
					H1F crudeLastHist = 
							new H1F("crudeLastHist","Crude Last Offset Sector "+sector+" Paddle "+paddle, 
									99,-49.5*RF_STRUCTURE,49.5*RF_STRUCTURE);
					crudeLastHist.setTitleX("Offset (ns)");
					dg.addDataSet(crudeLastHist, 3);

					//}

					// create the sector histograms
					if (paddle==1 && layer==1) {
						H1F sectorHist = 
								new H1F("sectorHist","Sector Offset: Sector "+sector, 
										99,-49.5*RF_STRUCTURE,49.5*RF_STRUCTURE);
						sectorHist.setTitleX("Offset (ns)");

						dg.addDataSet(sectorHist, 2);
					}
					else {
						// set the sector hist point to the paddle 1 layer 1 hist
						H1F sectorHist = dataGroups.getItem(sector,1,1).getH1F("sectorHist");
						dg.addDataSet(sectorHist, 2);
					}

					dataGroups.add(dg,sector,layer,paddle);	

					// initialize the constants array
					Double[] consts = {0.0, UNDEFINED_OVERRIDE};
					// override values

					constants.add(consts, sector, layer, paddle);
				}
			}
		}
	}

	@Override
	public void processEvent(DataEvent event) {

		// do nothing for the moment
		// will read text file at analyze step
		//		List<TOFPaddle> paddleList = DataProvider.getPaddleList(event);
		//		processPaddleList(paddleList);

	}

	@Override
	public void processPaddleList(List<TOFPaddle> paddleList) {

		// store the paddle pairs so we can iterate through them
		//		System.out.println("processPaddleList start");
		//		for (TOFPaddle pad : paddleList) {
		//			System.out.println("SLC "+pad.getDescriptor().getSector()+pad.getDescriptor().getLayer()+pad.getDescriptor().getComponent()+
		//								" particle_id "+pad.PARTICLE_ID);
		//		}

		//		for (TOFPaddle ePaddle : paddleList) {
		//			if (ePaddle.PARTICLE_ID == 0) {
		//				// create a pair with each of the pions
		//				for (TOFPaddle pPaddle : paddleList) {
		//					if (pPaddle.PARTICLE_ID ==1) {
		//						TOFPaddlePair paddlePair = new TOFPaddlePair();
		//						paddlePair.electronPaddle = ePaddle;
		//						paddlePair.pionPaddle = pPaddle;
		//
		//						allPaddleList.add(paddlePair);
		//						
		//					}
		//				}
		//			}
		//		}

		//		int eIndex = -1;
		//		int pIndex = -1;
		//
		//		for (TOFPaddle paddle : paddleList) {
		//
		//			if (paddle.PARTICLE_ID == 0) {
		//				//				System.out.println("Found electron");
		//				eIndex = paddleList.indexOf(paddle);
		//			}
		//			if (paddle.PARTICLE_ID == 1) {
		//				//				System.out.println("Found pion");
		//				pIndex = paddleList.indexOf(paddle);			
		//			}
		//		}
		//
		//		if (eIndex != -1 && pIndex != -1) {
		//			// we have an electron and pion, therefore store the pair
		//
		//			TOFPaddlePair paddlePair = new TOFPaddlePair();
		//			paddlePair.electronPaddle = paddleList.get(eIndex);
		//			paddlePair.pionPaddle = paddleList.get(pIndex);
		//
		//			allPaddleList.add(paddlePair);
		//		}
	}	

	@Override
	// non-standard analyze step for P2P
	// require to iterate through all events several times
	public void analyze() {

		//processTextFile();
		fitAll();

		// Raffaella's test hists
//		TCanvas vt = new TCanvas("vt", 700, 1000);
//		vt.divide(1,3);
//		vt.getCanvas().setGridX(false); vt.getCanvas().setGridY(false);
//		vt.getCanvas().setAxisFontSize(18);
//		vt.getCanvas().setAxisTitleSize(24);
//		vt.cd(0);
//		F1D f1 = new F1D("f1","[amp]*gaus(x,[mean],[sigma])", -1.0, 1.0);
//		f1.setParameter(0, 100.0);
//		f1.setParameter(1, 0.0);
//		f1.setParameter(2, 0.3);
//		f1.setLineWidth(2);
//		f1.setLineColor(2);
//		f1.setOptStat("1111");
//		DataFitter.fit(f1, hi_vertex_dt, "Q"); //No options uses error for sigma
//		vt.draw(hi_vertex_dt);
//		vt.cd(1);
//		vt.draw(hi_elec_t0);
//		vt.cd(2);
//		vt.draw(hi_pion_t0);
//
//		TCanvas paddles = new TCanvas("paddles", 800, 800);
//		paddles.cd(0);
//		paddles.draw(hPaddles);
//
//		TCanvas sects = new TCanvas("sects", 800, 800);
//		sects.divide(1, 3);
//		sects.cd(0);
//		sects.draw(eSect);
//		sects.cd(1);
//		sects.draw(pSect);
//		sects.cd(2);
//		sects.draw(eSect1);


		save();
		calib.fireTableDataChanged();
	}

	private void processTextFile() {

		String inputFile = "/home/louise/FTOF_calib_rewrite/input_files/p2p_run37665.txt";
		// store list of paddle pairs for P2P step

		String line = null;
		int maxLines=0;   

		System.out.println("Opening text file");

		try { 

			// Open the file
			FileReader fileReader = 
					new FileReader(inputFile);

			// Always wrap FileReader in BufferedReader
			BufferedReader bufferedReader = 
					new BufferedReader(fileReader);            

			// Read each line of the file up to a maximum count
			int lineNum=0;
			line = bufferedReader.readLine();
			line = bufferedReader.readLine(); // skip the header line

			while ((maxLines==0 || lineNum<maxLines) && (line != null)) {

				//                    0       1             2                 3                    4                 5
				// Each line contains RF_time target_center electron_TOF_time electron_flight_time electron_vertex_z electron_paddle_index 
				//											pion_TOF_time pion_flight_time pion_vertex_z pion_paddle_index
				//                                          6             7                8             9

				if (line.contains("inf")) {
					line = bufferedReader.readLine();
					lineNum++;
					continue;
				}

				String[] lineValues;
				lineValues = line.split(" ");

				int electronSector = Integer.parseInt(lineValues[5])/100;
				int electronPaddle = Integer.parseInt(lineValues[5])%100;
				double RFTime = Double.parseDouble(lineValues[0]);
				double electronTOFTime = Double.parseDouble(lineValues[2]);
				double electronFlightTime = Double.parseDouble(lineValues[3]);
				double electronVertexZ = Double.parseDouble(lineValues[4]);

				int pionSector = Integer.parseInt(lineValues[9])/100;
				int pionPaddle = Integer.parseInt(lineValues[9])%100;
				double pionTOFTime = Double.parseDouble(lineValues[6]);
				double pionFlightTime = Double.parseDouble(lineValues[7]);
				double pionVertexZ = Double.parseDouble(lineValues[8]);

				TOFPaddle  electronTOFPaddle = new TOFPaddle(electronSector, 2, electronPaddle);
				electronTOFPaddle.RF_TIME = RFTime;
				electronTOFPaddle.TOF_TIME = electronTOFTime;
				electronTOFPaddle.FLIGHT_TIME = electronFlightTime;
				electronTOFPaddle.VERTEX_Z = electronVertexZ;

				TOFPaddle  pionTOFPaddle = new TOFPaddle(pionSector, 2, pionPaddle);
				pionTOFPaddle.RF_TIME = RFTime;
				pionTOFPaddle.TOF_TIME = pionTOFTime;
				pionTOFPaddle.FLIGHT_TIME = pionFlightTime;
				pionTOFPaddle.VERTEX_Z = pionVertexZ;

				TOFPaddlePair paddlePair = new TOFPaddlePair();
				paddlePair.electronPaddle = electronTOFPaddle;
				paddlePair.pionPaddle = pionTOFPaddle;

				allPaddleList.add(paddlePair);

				line = bufferedReader.readLine();
				lineNum++;
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



	public void fitAll() {

		System.out.println("fitAll for allPaddleList "+allPaddleList.size());
		// fill the fineHists
		boolean doFineHists = true;
		if (doFineHists) {
			for(TOFPaddlePair paddlePair : allPaddleList){

				int eSector = paddlePair.electronPaddle.getDescriptor().getSector();
				int eLayer = paddlePair.electronPaddle.getDescriptor().getLayer();
				int eComponent = paddlePair.electronPaddle.getDescriptor().getComponent();
				int pSector = paddlePair.pionPaddle.getDescriptor().getSector();
				int pLayer = paddlePair.pionPaddle.getDescriptor().getLayer();
				int pComponent = paddlePair.pionPaddle.getDescriptor().getComponent();

				// Fill the first set of histograms
				dataGroups.getItem(eSector,eLayer,eComponent).getH1F("fineHist").fill(
						(paddlePair.electronPaddle.refTime(TARGET_CENTRE) + (1000*RF_STRUCTURE) + (0.5*RF_STRUCTURE))%RF_STRUCTURE - (0.5*RF_STRUCTURE));

				// Fill the first set of histograms
				dataGroups.getItem(pSector,pLayer,pComponent).getH1F("fineHist").fill(
						(paddlePair.pionPaddle.refTime(TARGET_CENTRE) + (1000*RF_STRUCTURE) + (0.5*RF_STRUCTURE))%RF_STRUCTURE - (0.5*RF_STRUCTURE));
						
				// find the "fine" offset within the RF pulse width

			}

			// calculate the fine offsets
			for(int sector = 1; sector <= 6; sector++){
				for (int layer = 1; layer <= 3; layer++) {
					int layer_index = layer-1;
					for(int paddle = 1; paddle <= NUM_PADDLES[layer_index]; paddle++){
						fitFineHist(sector, layer, paddle);
					}
				}
			}
		}

		// fill the crudeFirst histograms 
		boolean doCrudeFirst = true;
		if (doCrudeFirst) {		
			for(TOFPaddlePair paddlePair : allPaddleList){

				// Fill the crude first set of histograms
				// electron hit in paddle 1-10 of layer 1
				// pion hit in ref paddle of layer 1 for electron sector+4
				// find offset of paddles 1-10 using these data

				int eComp = paddlePair.electronPaddle.getDescriptor().getComponent();
				int piComp = paddlePair.pionPaddle.getDescriptor().getComponent();
				int eSect = paddlePair.electronPaddle.getDescriptor().getSector();
				int piSect = paddlePair.pionPaddle.getDescriptor().getSector();
				int eLayer = paddlePair.electronPaddle.getDescriptor().getLayer();
				int piLayer = paddlePair.pionPaddle.getDescriptor().getLayer();

				if (eLayer == REF_LAYER && eComp <= NUM_FIRST_PADDLES && piLayer == REF_LAYER && piComp == REF_PADDLE && (piSect+6 - eSect)%6 == 4) {

					// correct the electron and pion time with the fine offset
					double eCorrTime = paddlePair.electronPaddle.refTime(TARGET_CENTRE) - getOffset(eSect, eLayer, eComp);
					double piCorrTime = paddlePair.pionPaddle.refTime(TARGET_CENTRE) - getOffset(piSect, piLayer, piComp);

					dataGroups.getItem(eSect,eLayer,eComp).getH1F("crudeFirstHist").fill(eCorrTime - piCorrTime);

				}
			}

			// calculate the crudeFirst offsets
			for(int sector = 1; sector <= 6; sector++){
				for(int paddle = 1; paddle <= NUM_FIRST_PADDLES; paddle++){

					H1F crudeFirstHist = 
							dataGroups.getItem(sector,REF_LAYER,paddle).getH1F("crudeFirstHist");

					int maxBin = crudeFirstHist.getMaximumBin();
					double offset = crudeFirstHist.getXaxis().getBinCenter(maxBin);
					crudeFirstHist.setTitle(crudeFirstHist.getTitle() + " Offset = " + formatDouble(offset));

					double newOffset = getOffset(sector, REF_LAYER, paddle) + offset;

					Double[] consts = constants.getItem(sector, REF_LAYER, paddle);
					consts[CUMUL_OFFSET] = newOffset;

				}
			}	
		}

		// fill the crudeSect histograms 
		for(TOFPaddlePair paddlePair : allPaddleList){

			// Sector to sector corrections
			// electron hit in paddle 1-10 of layer 1
			// pion hit in sector 1 paddle 1-10

			int eComp = paddlePair.electronPaddle.getDescriptor().getComponent();
			int piComp = paddlePair.pionPaddle.getDescriptor().getComponent();
			int eSect = paddlePair.electronPaddle.getDescriptor().getSector();
			int piSect = paddlePair.pionPaddle.getDescriptor().getSector();
			int eLayer = paddlePair.electronPaddle.getDescriptor().getLayer();
			int piLayer = paddlePair.pionPaddle.getDescriptor().getLayer();

			if (eComp <= NUM_FIRST_PADDLES && piSect == 1 && piComp <= NUM_FIRST_PADDLES) {

				// correct the electron and pion time with the current offset
				double eCorrTime = paddlePair.electronPaddle.refTime(TARGET_CENTRE) - getOffset(eSect, eLayer, eComp);
				double piCorrTime = paddlePair.pionPaddle.refTime(TARGET_CENTRE) - getOffset(piSect, piLayer, piComp);

				dataGroups.getItem(eSect,1,1).getH1F("sectorHist").fill(eCorrTime - piCorrTime);

			}
		}

		// calculate the crudeSect offsets and then apply them to all paddles in each sector
		for(int sector = 1; sector <= 6; sector++){

			H1F sectorHist = 
					dataGroups.getItem(sector,1,1).getH1F("sectorHist");
			int maxBin = sectorHist.getMaximumBin();
			double offset = sectorHist.getXaxis().getBinCenter(maxBin);

			sectorHist.setTitle(sectorHist.getTitle() + " Sector offset = " + formatDouble(offset));

			for (int layer = 1; layer <= 3; layer++) {
				int layer_index = layer-1;
				for(int paddle = 1; paddle <= NUM_PADDLES[layer_index]; paddle++){
					double newOffset = getOffset(sector, layer, paddle) + offset;

					Double[] consts = constants.getItem(sector, layer, paddle);
					consts[CUMUL_OFFSET] = newOffset;

				}
			}
		}	

		// fill the crudeLast histograms 
		for(TOFPaddlePair paddlePair : allPaddleList){

			// Fill the crude last set of histograms
			// electron hit in paddle 1-10 of layer 1
			// pion hit in any other paddle

			int eComp = paddlePair.electronPaddle.getDescriptor().getComponent();
			int piComp = paddlePair.pionPaddle.getDescriptor().getComponent();
			int eSect = paddlePair.electronPaddle.getDescriptor().getSector();
			int piSect = paddlePair.pionPaddle.getDescriptor().getSector();
			int eLayer = paddlePair.electronPaddle.getDescriptor().getLayer();
			int piLayer = paddlePair.pionPaddle.getDescriptor().getLayer();

			if (eLayer == REF_LAYER && eComp <= NUM_FIRST_PADDLES && (piLayer != REF_LAYER || piComp > NUM_FIRST_PADDLES)) {

				// correct the electron and pion time with the current offset
				double eCorrTime = paddlePair.electronPaddle.refTime(TARGET_CENTRE) - getOffset(eSect, eLayer, eComp);
				double piCorrTime = paddlePair.pionPaddle.refTime(TARGET_CENTRE) - getOffset(piSect, piLayer, piComp);

				dataGroups.getItem(piSect,piLayer,piComp).getH1F("crudeLastHist").fill(piCorrTime - eCorrTime);
				// note change in order as we are now using
				// pion for correction

			}
		}

		// calculate the crudeLast offsets
		for(int sector = 1; sector <= 6; sector++){
			for (int layer = 1; layer <= 3; layer++) {
				int layer_index = layer-1;
				for(int paddle = 1; paddle <= NUM_PADDLES[layer_index]; paddle++){

					if (layer != REF_LAYER || paddle > NUM_FIRST_PADDLES) {

						H1F crudeLastHist = 
								dataGroups.getItem(sector,layer,paddle).getH1F("crudeLastHist");
						int maxBin = crudeLastHist.getMaximumBin();
						double offset = crudeLastHist.getXaxis().getBinCenter(maxBin);
						crudeLastHist.setTitle(crudeLastHist.getTitle() + " Offset = " + formatDouble(offset));

						double newOffset = getOffset(sector, layer, paddle) + offset;

						Double[] consts = constants.getItem(sector, layer, paddle);
						consts[CUMUL_OFFSET] = newOffset;
					}
				}
			}
		}	

		// fill the crudeFirstAgain histograms 
		for(TOFPaddlePair paddlePair : allPaddleList){

			// Fill the crude first set of histograms
			// electron hit in paddle 1-10 of layer 1
			// pion hit in ref paddle of layer 1 for electron sector+4
			// find offset of paddles 1-10 using these data

			int eComp = paddlePair.electronPaddle.getDescriptor().getComponent();
			int piComp = paddlePair.pionPaddle.getDescriptor().getComponent();
			int eSect = paddlePair.electronPaddle.getDescriptor().getSector();
			int piSect = paddlePair.pionPaddle.getDescriptor().getSector();
			int eLayer = paddlePair.electronPaddle.getDescriptor().getLayer();
			int piLayer = paddlePair.pionPaddle.getDescriptor().getLayer();

			if (eLayer == REF_LAYER && eComp <= NUM_FIRST_PADDLES && piComp > NUM_FIRST_PADDLES) {

				// correct the electron and pion time with the current offset
				double eCorrTime = paddlePair.electronPaddle.refTime(TARGET_CENTRE) - getOffset(eSect, eLayer, eComp);
				double piCorrTime = paddlePair.pionPaddle.refTime(TARGET_CENTRE) - getOffset(piSect, piLayer, piComp);

				dataGroups.getItem(eSect,eLayer,eComp).getH1F("crudeFirstAgainHist").fill(eCorrTime - piCorrTime);

			}
		}

		// calculate the crudeFirstAgain offsets
		for(int sector = 1; sector <= 6; sector++){
			for(int paddle = 1; paddle <= NUM_FIRST_PADDLES; paddle++){

				H1F crudeFirstAgainHist = 
						dataGroups.getItem(sector,REF_LAYER,paddle).getH1F("crudeFirstAgainHist");
				int maxBin = crudeFirstAgainHist.getMaximumBin();
				double offset = crudeFirstAgainHist.getXaxis().getBinCenter(maxBin);
				crudeFirstAgainHist.setTitle(crudeFirstAgainHist.getTitle() + " Offset = " + formatDouble(offset));

				double newOffset = getOffset(sector, REF_LAYER, paddle) + offset;

				Double[] consts = constants.getItem(sector, REF_LAYER, paddle);
				consts[CUMUL_OFFSET] = newOffset;

			}
		}			
	}

	public void fitFineHist(int sector, int layer, int paddle) {

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
			Double[] consts = constants.getItem(sector, layer, paddle);
			consts[CUMUL_OFFSET] = fineFunc.getParameter(1);

			fineHist.setTitle(fineHist.getTitle() + " Fine offset = " + formatDouble(fineFunc.getParameter(1)));
		}
		catch(Exception ex) {
			ex.printStackTrace();
		}	
	}	

	private Double formatDouble(double val) {
		return Double.parseDouble(new DecimalFormat("0.000").format(val));
	}

	@Override
	public void customFit(int sector, int layer, int paddle){

		String[] fields = { "Override offset:"};
		TOFCustomFitPanel panel = new TOFCustomFitPanel(fields);

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
			offset = constants.getItem(sector, layer, paddle)[CUMUL_OFFSET];
		}
		return offset;
	}	

	@Override
	public void saveRow(int sector, int layer, int paddle) {

		calib.setDoubleValue(getOffset(sector,layer,paddle),
				"paddle2paddle", sector, layer, paddle);

	}

	@Override
	public void drawPlots(int sector, int layer, int paddle, EmbeddedCanvas canvas) {

		// TO DO

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

		//		summary.setTitle("Left Right centroids: "
		//				+ LAYER_NAME[layer - 1] + " Sector "
		//				+ sector);
		//		summary.setTitleX("Paddle Number");
		//		summary.setYTitle("Centroid (cm)");
		//		summary.setMarkerSize(5);
		//		summary.setMarkerStyle(2);

		DataGroup dg = new DataGroup(1,1);
		dg.addDataSet(summ, 0);
		return dg;

	}


}
