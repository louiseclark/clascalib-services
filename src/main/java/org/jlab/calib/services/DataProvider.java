package org.jlab.calib.services;

import java.util.ArrayList;
import java.util.List;

import org.jlab.clas.physics.GenericKinematicFitter;
import org.jlab.clas.physics.Particle;
import org.jlab.clas.physics.PhysicsEvent;
import org.jlab.clas.physics.RecEvent;
import org.jlab.detector.base.DetectorType;
import org.jlab.detector.base.GeometryFactory;
import org.jlab.detector.calib.utils.CalibrationConstants;
import org.jlab.detector.decode.CodaEventDecoder;
import org.jlab.detector.decode.DetectorDataDgtz;
import org.jlab.detector.decode.DetectorEventDecoder;
import org.jlab.geom.base.ConstantProvider;
import org.jlab.geom.base.Detector;
import org.jlab.geom.component.ScintillatorMesh;
import org.jlab.geom.component.ScintillatorPaddle;
import org.jlab.geom.detector.ftof.FTOFDetector;
import org.jlab.geom.detector.ftof.FTOFDetectorMesh;
import org.jlab.geom.detector.ftof.FTOFFactory;
import org.jlab.geom.prim.Line3D;
import org.jlab.geom.prim.Path3D;
import org.jlab.geom.prim.Point3D;
import org.jlab.geom.prim.Vector3D;
import org.jlab.groot.group.DataGroup;
import org.jlab.io.base.DataBank;
//import org.jlab.calib.temp.DataGroup;
import org.jlab.io.base.DataEvent;
import org.jlab.io.evio.EvioDataBank;
import org.jlab.io.evio.EvioDataEvent;
import org.jlab.utils.groups.IndexedList;
import org.jlab.utils.groups.IndexedTable;


/**
 *
 * @author gavalian
 */
public class DataProvider {

	private static	boolean test = false;
	public static CodaEventDecoder codaDecoder;
	public static DetectorEventDecoder eventDecoder;
	public static List<DetectorDataDgtz> detectorData;

	public static void init() {

		codaDecoder = new CodaEventDecoder();
		eventDecoder = new DetectorEventDecoder();
		detectorData = new ArrayList<DetectorDataDgtz>();
	}

	public static List<TOFPaddle> getPaddleList(DataEvent event) {

		List<TOFPaddle>  paddleList = new ArrayList<TOFPaddle>();

		if (test) {
			System.out.println("New event - dgtz NEW");
			//event.show();

			//					if (event.hasBank("FTOF::dgtz")) {
			//						event.getBank("FTOF::dgtz").show();
			//					}
			//					if (event.hasBank("FTOF::hits")) {
			//						event.getBank("FTOF::hits").show();
			//					}
			//					if (event.hasBank("FTOF::rawhits")) {
			//						event.getBank("FTOF::rawhits").show();
			//					}
			//					if (event.hasBank("CTOF::dgtz")) {
			//						event.getBank("CTOF::dgtz").show();
			//					}
			//					if (event.hasBank("CTOF::hits")) {
			//						event.getBank("CTOF::hits").show();
			//					}
			//					if (event.hasBank("CTOF::rawhits")) {
			//						event.getBank("CTOF::rawhits").show();
			//					}
		}

		//		if (event.hasBank("FTOF1A::dgtz")||event.hasBank("FTOF1B::dgtz")||event.hasBank("FTOF2B::dgtz")) {
		//			paddleList = getPaddleListDgtz(event);
		//		}
		if (event.hasBank("FTOF::dgtz")) {
			paddleList = getPaddleListDgtzNew(event);
		}
		//		else { 
		//        	paddleList = getPaddleListRaw(event);
		//		}

		//paddleList = getPaddleListTWTest(event);
		return paddleList;

	}

	public static List<TOFPaddle> getPaddleListDgtzNew2(DataEvent event){

		ArrayList<TOFPaddle>  paddleList = new ArrayList<TOFPaddle>();

		if (event.hasBank("FTOF::dgtz")) {
			DataBank dgtzBank = event.getBank("FTOF::dgtz");
			DataBank hitsBank = event.getBank("FTOF::hits");
			DataBank rawhitsBank = event.getBank("FTOF::rawhits");

			TOFCalibration.hitsPerBankHist.fill(hitsBank.rows());

			systemOut("dgtz rows "+ dgtzBank.rows());
			for (int dgtzIndex = 0; dgtzIndex < dgtzBank.rows(); dgtzIndex++) {

				int sector = (int) dgtzBank.getByte("sector", dgtzIndex);
				int layer = (int) dgtzBank.getByte("layer", dgtzIndex);
				int component = (int) dgtzBank.getShort("component", dgtzIndex);
				float xpos = 0;
				float ypos = 0;
				float timeL = 0;
				float timeR = 0;

				systemOut("dgtz SLC "+sector+layer+component);
				if (event.hasBank("FTOF::hits")) {
					systemOut("Hits bank");

					// find the corresponding row in the ftofhits bank
					// to get the hit position from tracking

					//systemOut("Hits rows"+hitsBank.rows());

					for (int hitIndex = 0; hitIndex < hitsBank.rows(); hitIndex++) {

						int hitSector = (int) hitsBank.getByte("sector",hitIndex);
						int hitLayer = (int) hitsBank.getByte("layer",hitIndex);
						int hitComponent = (int) hitsBank.getShort("component",hitIndex);
						int hitID = (int) hitsBank.getShort("id",hitIndex);

						systemOut("Hits SLCID "+hitSector+hitLayer+hitComponent+hitID);

						if (sector==hitSector && layer==hitLayer && component==hitComponent 
								&& dgtzIndex+1==hitID) {

							if (hitsBank.getFloat("tx", hitIndex)!=0 && 
									hitsBank.getFloat("ty", hitIndex)!=0) {

								xpos = hitsBank.getFloat("tx", hitIndex);
								ypos = hitsBank.getFloat("ty", hitIndex);
								timeL = rawhitsBank.getFloat("time_left", hitIndex);
								timeR = rawhitsBank.getFloat("time_right", hitIndex);

							} // tracking non zero

						} // dgtz match to hit

					} // for hitsBank

				} // hasBank FTOF::hits

				systemOut("Creating paddle");
				systemOut("Created paddle SLC "+sector+layer+component);
				//dgtzBank.show();
				systemOut("ADCL "+dgtzBank.getInt("ADCL", dgtzIndex)+
						" ADCR "+dgtzBank.getInt("ADCR", dgtzIndex)+
						" TDCL "+dgtzBank.getInt("TDCL", dgtzIndex)+
						" TDCR "+dgtzBank.getInt("TDCR", dgtzIndex)+
						" xpos "+xpos+" ypos "+ypos);

				TOFPaddle  paddle = new TOFPaddle(
						sector,
						layer,
						component,
						dgtzBank.getInt("ADCL", dgtzIndex),
						dgtzBank.getInt("ADCR", dgtzIndex),
						dgtzBank.getInt("TDCL", dgtzIndex),
						dgtzBank.getInt("TDCR", dgtzIndex),
						xpos,
						ypos,
						timeL,
						timeR);

				// set status to ok if at least one reading
				if (paddle.ADCL!=0.0) {
					TOFCalibrationEngine.adcLeftStatus.add(0, sector,layer,component);
				}
				if (paddle.ADCR!=0.0) {
					TOFCalibrationEngine.adcRightStatus.add(0, sector,layer,component);
				}
				if (paddle.TDCL!=0.0) {
					TOFCalibrationEngine.tdcLeftStatus.add(0, sector,layer,component);
				}
				if (paddle.TDCR!=0) {
					TOFCalibrationEngine.tdcRightStatus.add(0, sector,layer,component);
				}

				if (paddle.includeInCalib()) {
					systemOut("Adding paddle");
					paddleList.add(paddle);

					systemOut("SLC "+sector+layer+component);
					systemOut("ADCL "+dgtzBank.getInt("ADCL", dgtzIndex)+
							" ADCR "+dgtzBank.getInt("ADCR", dgtzIndex)+
							" TDCL "+dgtzBank.getInt("TDCL", dgtzIndex)+
							" TDCR "+dgtzBank.getInt("TDCR", dgtzIndex)+
							" xpos "+xpos+" ypos "+ypos);
					systemOut("Louise test 5");
				}
				systemOut("Louise test 6");
			}
			systemOut("Louise test 7");
		}
		systemOut("Louise test 8");
		return paddleList;
	}

	public static void systemOut(String text) {
		boolean test = false;
		if (test) {
			System.out.println(text);
		}
	}

	public static List<TOFPaddle> getPaddleListDgtzNew(DataEvent event){

		ArrayList<TOFPaddle>  paddleList = new ArrayList<TOFPaddle>();

		if (test) {
			DataEvent e = (DataEvent) event;
			System.out.println("New event - dgtz NEW");
			//e.show();

			if (event.hasBank("FTOF::dgtz")) {
				event.getBank("FTOF::dgtz").show();
			}
			if (event.hasBank("FTOFRec::ftofhits")) {
				event.getBank("FTOFRec::ftofhits").show();
			}
			if (event.hasBank("FTOFRec::rawhits")) {
				event.getBank("FTOFRec::rawhits").show();
			}
		}

		if (event.hasBank("FTOF::dgtz")) {
			EvioDataBank dgtzBank = (EvioDataBank) event.getBank("FTOF::dgtz");

			for (int dgtzIndex = 0; dgtzIndex < dgtzBank.rows(); dgtzIndex++) {

				int sector = dgtzBank.getInt("sector", dgtzIndex);
				int layer = dgtzBank.getInt("layer", dgtzIndex);
				int component = dgtzBank.getInt("paddle", dgtzIndex);
				float xpos = 0;
				float ypos = 0;
				float timeL = 0;
				float timeR = 0;

				if (event.hasBank("FTOFRec::ftofhits")) {

					// find the corresponding row in the ftofhits bank
					// to get the hit position from tracking
					EvioDataBank hitsBank = (EvioDataBank) event.getBank("FTOFRec::ftofhits");
					TOFCalibration.hitsPerBankHist.fill(hitsBank.rows());

					for (int hitIndex = 0; hitIndex < hitsBank.rows(); hitIndex++) {

						if (sector==hitsBank.getInt("sector",hitIndex)
								&&
								layer==hitsBank.getInt("panel_id",hitIndex)
								&&
								component==hitsBank.getInt("paddle_id",hitIndex)
								&&
								dgtzBank.getInt("hitn",dgtzIndex)==hitsBank.getInt("id", hitIndex)) {

							if (hitsBank.getFloat("tx", hitIndex)!=0 && 
									hitsBank.getFloat("ty", hitIndex)!=0) {

								xpos = hitsBank.getFloat("tx", hitIndex);
								ypos = hitsBank.getFloat("ty", hitIndex);

								if (event.hasBank("FTOFRec::rawhits")) {
									// one to one correspondence between ftofhits and rawhits
									EvioDataBank rawBank = (EvioDataBank) event.getBank("FTOFRec::rawhits");
									timeL = rawBank.getFloat("time_left", hitIndex);
									timeR = rawBank.getFloat("time_right", hitIndex);
								}								
							}
						}
					}
				}

				TOFPaddle  paddle = new TOFPaddle(
						sector,
						layer,
						component,
						dgtzBank.getInt("ADCL", dgtzIndex),
						dgtzBank.getInt("ADCR", dgtzIndex),
						dgtzBank.getInt("TDCL", dgtzIndex),
						dgtzBank.getInt("TDCR", dgtzIndex),
						xpos,
						ypos,
						timeL,
						timeR);

				// set status to ok if at least one reading
				if (paddle.ADCL!=0.0) {
					TOFCalibrationEngine.adcLeftStatus.add(0, sector,layer,component);
				}
				if (paddle.ADCR!=0.0) {
					TOFCalibrationEngine.adcRightStatus.add(0, sector,layer,component);
				}
				if (paddle.TDCL!=0.0) {
					TOFCalibrationEngine.tdcLeftStatus.add(0, sector,layer,component);
				}
				if (paddle.TDCR!=0) {
					TOFCalibrationEngine.tdcRightStatus.add(0, sector,layer,component);
				}

				//				System.out.println("SLC "+sector+layer+component);
				//				System.out.println("TDCL "+paddle.TDCL+" ADCL "+paddle.ADCL);
				//				System.out.println("lamL "+paddle.lamL()+" ordL "+paddle.ordL());
				//				System.out.println("timeL tw "+paddle.timeLeftAfterTW());
				//				System.out.println("TDCR "+paddle.TDCR+" ADCR "+paddle.ADCR);
				//				System.out.println("lamR "+paddle.lamR()+" ordR "+paddle.ordR());
				//				System.out.println("timeR tw "+paddle.timeRightAfterTW());
				//				System.out.println("leftRight "+paddle.leftRight());

				if (paddle.includeInCalib()) {
					paddleList.add(paddle);

					if (layer==1) {
						TOFCalibration.adcLeftHist1A.fill(dgtzBank.getInt("ADCL", dgtzIndex));
						TOFCalibration.adcRightHist1A.fill(dgtzBank.getInt("ADCR", dgtzIndex));
						if (xpos!=0.0 || ypos!=0.0) {
							TOFCalibration.trackingAdcLeftHist1A.fill(dgtzBank.getInt("ADCL", dgtzIndex));
							TOFCalibration.trackingAdcRightHist1A.fill(dgtzBank.getInt("ADCR", dgtzIndex));
						}
					}
					if (layer==2) {
						TOFCalibration.adcLeftHist1B.fill(dgtzBank.getInt("ADCL", dgtzIndex));
						TOFCalibration.adcRightHist1B.fill(dgtzBank.getInt("ADCR", dgtzIndex));
						if (xpos!=0.0 || ypos!=0.0) {
							TOFCalibration.trackingAdcLeftHist1B.fill(dgtzBank.getInt("ADCL", dgtzIndex));
							TOFCalibration.trackingAdcRightHist1B.fill(dgtzBank.getInt("ADCR", dgtzIndex));
						}
					}

					TOFCalibration.paddleHist.fill(((layer-1)*100)+component);
					if (xpos!=0.0 || ypos!=0.0) {
						TOFCalibration.trackingPaddleHist.fill(((layer-1)*100)+component);
					}


					if (test) {
						System.out.println("SLC "+sector+layer+component);
						System.out.println("ADCL "+dgtzBank.getInt("ADCL", dgtzIndex)+
								" ADCR "+dgtzBank.getInt("ADCR", dgtzIndex)+
								" TDCL "+dgtzBank.getInt("TDCL", dgtzIndex)+
								" TDCR "+dgtzBank.getInt("TDCR", dgtzIndex)+
								" xpos "+xpos+" ypos "+ypos);
					}

				}
			}
		}

		if ((event.hasBank("FTOFRec::ftofhits"))) {

			EvioDataBank hitsBank = (EvioDataBank) event.getBank("FTOFRec::ftofhits");
			for (int hitIndex = 0; hitIndex < hitsBank.rows(); hitIndex++) {

				int l = hitsBank.getInt("panel_id",hitIndex);
				int s = hitsBank.getInt("sector",hitIndex);

				TOFCalibration.totalStatHist.fill(((l-1)*10)+s);
				if ((hitsBank.getFloat("tx", hitIndex) == 0 ) &&
						(hitsBank.getFloat("ty", hitIndex) == 0))
					TOFCalibration.trackingZeroStatHist.fill(((l-1)*10)+s);
				else {
					TOFCalibration.trackingStatHist.fill(((l-1)*10)+s);
				}
			}
		}

		// Paddle to paddle


		// useful parameters
		int    pidCode = 11;
		double[] ftofThickness = new double[3];
		ftofThickness[0]=5;
		ftofThickness[1]=6;
		ftofThickness[2]=5;

		GenericKinematicFitter fitter = new GenericKinematicFitter(11.0);

		PhysicsEvent genEvent = fitter.getGeneratedEvent(event);
		RecEvent recEvent = fitter.getRecEvent(event);

		Particle electronGen = null;
		Particle electronRec = null;
		Particle pionRec = null;
		Particle partRecHB = null;

		// loop over generated particles
		for(int iGenPart = 0; iGenPart < genEvent.countByPid(pidCode); iGenPart++)
		{   
			Particle genPart = genEvent.getParticleByPid(pidCode, iGenPart);
			if(electronGen == null) {
				electronGen = genPart;
			}
		}        

		// loop over reconstructed tracks
		EvioDataBank recBankHB = (EvioDataBank) event.getBank("EVENTHB::particle");
		EvioDataBank recDeteHB = (EvioDataBank) event.getBank("EVENTHB::detector");
		if(recBankHB!=null) {
			int nrows = recBankHB.rows();
			for(int loop = 0; loop < nrows; loop++){
				Particle recParticle = new Particle();
				recParticle.initParticleWithMass(
						0,
						recBankHB.getFloat("px", loop),
						recBankHB.getFloat("py", loop),
						recBankHB.getFloat("pz", loop),
						recBankHB.getFloat("vx", loop),
						recBankHB.getFloat("vy", loop),
						recBankHB.getFloat("vz", loop));
				recParticle.setProperty("q",recBankHB.getInt("charge", loop));
				recParticle.setProperty("id",loop*1.0);
				recParticle.setProperty("det",recBankHB.getInt("status", loop)*1.0);
				if(recBankHB.getInt("charge", loop)!=0 && recDeteHB!=null) {
					for(int j=0; j<recDeteHB.rows(); j++) {
						if(recDeteHB.getInt("pindex",j)==loop && recDeteHB.getInt("detector",j)==17 && recDeteHB.getFloat("energy",j)>1.5) {
							int sector  = recDeteHB.getInt("sector",j);
							int layer   = recDeteHB.getInt("layer",j);
							double tof  = recDeteHB.getFloat("time",j);
							double edep = recDeteHB.getFloat("energy",j);
							double path = recDeteHB.getFloat("path",j);
							double beta = path/(tof-124.25)/29.97;
							double mass2 = Math.pow(recParticle.p(),2.0)*(1/beta/beta-1);
							recParticle.setProperty("tof",tof);
							recParticle.setProperty("edep",edep);
							recParticle.setProperty("path",path);
							recParticle.setProperty("m2",mass2);
						}
					}
				}
				if(partRecHB==null && recBankHB.getInt("charge", loop)==-1) partRecHB=recParticle;
				if(electronRec==null && electronGen!=null) {
					if(Math.abs(recParticle.p()-electronGen.p())<0.5
							&& Math.abs(Math.toDegrees(recParticle.theta()-electronGen.theta()))<2.0
							&& Math.abs(Math.toDegrees(recParticle.phi()-electronGen.phi()))<8) {
						electronRec = recParticle;
						if(electronRec.getProperty("tof")>0) {
							TofP2PEventListener.hi_vertex_dt.fill(electronRec.getProperty("tof")-electronRec.getProperty("path")/29.97-124.25);
						}
					}
				}
			}
		}
		// loop over Ftof reconstructed hits
		// get FTOFRec and Tracking banks
		EvioDataBank recFTOF = (EvioDataBank) event.getBank("FTOFRec::ftofhits");
		EvioDataBank recHBTR = (EvioDataBank) event.getBank("HitBasedTrkg::HBTracks");
		if(recFTOF!=null && recHBTR!=null) {
			int nrows = recFTOF.rows();
			int ntrks = recHBTR.rows();
			if(ntrks>=2 && electronRec!= null) {				// use only events with at least two tracks and the electron reconstructed				
				// first find the electron to get the startime
				double startTime  = 0;
				int eSector = 0;
				int eLayer = 0;
				int eComponent = 0;
				for(int loop = 0; loop < nrows; loop++){
					int panel     = recFTOF.getInt("panel_id", loop);
					int trk_id    = recFTOF.getInt("trkId", loop);
					double energy = recFTOF.getFloat("energy", loop);
					double time   = recFTOF.getFloat("time", loop);
					double tx     = recFTOF.getFloat("tx", loop);
					double ty     = recFTOF.getFloat("ty", loop);
					double tz     = recFTOF.getFloat("tz", loop);
					if(trk_id!=-1 && energy>1.5) {                             // select FTOF hits that were associated to a track
						double c3x  = recHBTR.getDouble("c3_x",trk_id);
						double c3y  = recHBTR.getDouble("c3_y",trk_id);
						double c3z  = recHBTR.getDouble("c3_z",trk_id);
						double path = recHBTR.getDouble("pathlength",trk_id) + Math.sqrt((tx-c3x)*(tx-c3x)+(ty-c3y)*(ty-c3y)+(tz-c3z)*(tz-c3z));
						if(trk_id == ((int) electronRec.getProperty("id"))) {		// use the hit associated to the electron track to get the start time
							startTime  = time-path/29.97;
							TofP2PEventListener.hi_elec_t0.fill(startTime-124.25);
							eSector = recFTOF.getInt("sector", loop);
							eLayer = recFTOF.getInt("panel_id", loop);
							eComponent = recFTOF.getInt("paddle_id", loop);
						}
					}
				}
				// then look for pions
				for(int loop = 0; loop < nrows; loop++){
					int pSector   = recFTOF.getInt("sector", loop);
					int pPanel    = recFTOF.getInt("panel_id", loop);
					int pComponent = recFTOF.getInt("paddle_id", loop);
					int trk_id    = recFTOF.getInt("trkId", loop);
					double energy = recFTOF.getFloat("energy", loop);
					double time   = recFTOF.getFloat("time", loop);
					double tx     = recFTOF.getFloat("tx", loop);
					double ty     = recFTOF.getFloat("ty", loop);
					double tz     = recFTOF.getFloat("tz", loop);
					if(trk_id!=-1 && energy>1.5) {                             // select FTOF hits that were associated to a track
						double c3x  = recHBTR.getDouble("c3_x",trk_id);
						double c3y  = recHBTR.getDouble("c3_y",trk_id);
						double c3z  = recHBTR.getDouble("c3_z",trk_id);
						int    q    = recHBTR.getInt("q",trk_id);
						double mom  = recHBTR.getDouble("p",trk_id);
						double path = recHBTR.getDouble("pathlength",trk_id) + Math.sqrt((tx-c3x)*(tx-c3x)+(ty-c3y)*(ty-c3y)+(tz-c3z)*(tz-c3z));
						if(trk_id != ((int) electronRec.getProperty("id"))) {		// use the hit associated to the electron track to get the start time
							double beta = mom/Math.sqrt(mom*mom+0.139*0.139);
							//				   System.out.println(mom + " " + beta + " " + pionTime );
							if(energy>1.5*ftofThickness[pPanel-1] &&energy<3*ftofThickness[pPanel-1]) {
								double pionTime    = time - path/beta/29.97;
								if(startTime>0 && pionTime>0) {
									TofP2PEventListener.hi_pion_t0.fill(pionTime-startTime);
									//System.out.println("Creating electron and pion paddles");
									TOFPaddle  ePaddle = new TOFPaddle(
									eSector,
									eLayer,
									eComponent);
									ePaddle.START_TIME = startTime;
									ePaddle.PARTICLE_ID = 0;
									//System.out.println("Creating electron paddle");
									//paddleList.add(ePaddle);
									
									TOFPaddle  pPaddle = new TOFPaddle(
									pSector,
									pPanel,
									pComponent);
									pPaddle.START_TIME = pionTime;
									pPaddle.PARTICLE_ID = 1;
//									System.out.println("Creating pion paddle");
									//paddleList.add(pPaddle);
									
									TOFPaddlePair paddlePair = new TOFPaddlePair();
									paddlePair.electronPaddle = ePaddle;
									paddlePair.pionPaddle = pPaddle;

									TofP2PEventListener.allPaddleList.add(paddlePair);
									
									double[] padShift = {0.0, 0.0, 23.0, 85.0};
									TofP2PEventListener.hPaddles.fill(((eSector-1)*100)+eComponent+padShift[eLayer], 
																	  ((pSector-1)*100)+pComponent+padShift[pPanel]);
									TofP2PEventListener.eSect.fill(eSector);
									TofP2PEventListener.pSect.fill(pSector);

								}
							}
						}
					}
				}
			}
		}

		return paddleList;
	}

	public static List<TOFPaddle> getPaddleListRaw(DataEvent event){

		ArrayList<TOFPaddle>  paddleList = new ArrayList<TOFPaddle>();

		List<DetectorDataDgtz> dataSet = codaDecoder.getDataEntries((EvioDataEvent) event);
		eventDecoder.translate(dataSet);
		eventDecoder.fitPulses(dataSet);
		detectorData.clear();
		detectorData.addAll(dataSet);

		IndexedList<IndexedList<Integer>> rawPaddles = new IndexedList<IndexedList<Integer>>(3);

		if (test) {
			System.out.println("New event - raw");
		}

		for (DetectorDataDgtz bank : detectorData) {

			if(bank.getDescriptor().getType().getName()=="FTOF") {

				int sector = bank.getDescriptor().getSector();
				int layer = bank.getDescriptor().getLayer(); 
				int component = bank.getDescriptor().getComponent();
				int order = bank.getDescriptor().getOrder(); // 0=ADCL 1=ADCR 2=TDCL 3=TDCR	    		

				if (test) {
					//System.out.println("New FTOF bank");
					System.out.println(order+" "+sector+" "+layer+" "+component);

					//					System.out.println("ORDER "+order);
					//					System.out.println("SLC "+sector+layer+component);
					//					System.out.println("ADCSize "+bank.getADCSize());
					//					System.out.println("TDCSize "+bank.getTDCSize());
					if (bank.getADCSize()>0) {
						System.out.println("getADC0 "+bank.getADCData(0).getADC());
						System.out.println("getIntegral0 "+bank.getADCData(0).getIntegral());
					}
					if (bank.getTDCSize()>0) {
						System.out.println("getTDC0 "+bank.getTDCData(0).getTime());
					}
					//					System.out.println("TDCL "+bank.getTDCData(0).getTime());
					//					System.out.println("TDCR "+bank.getTDCData(1).getTime());

				}

				IndexedList<Integer> rawPaddle = new IndexedList<Integer>(1);
				if (rawPaddles.hasItem(sector,layer,component)) {
					rawPaddle = rawPaddles.getItem(sector,layer,component);
				}
				else {
					rawPaddles.add(rawPaddle, sector,layer,component);
				}

				if (order==0 || order==1) {
					rawPaddle.add(bank.getADCData(0).getIntegral(), order);
				}

			}

			// for rawPaddle in raw Paddles

			// check for 1 ADC hit and 1 TDC hit (1A and 2)
			// check for 1 ADC hist and at least 1 TDC hit (1B)
			//if ((layer==2) && (bank.getADCSize()==1) && (bank.getTDCSize()>0)
			//	||
			//	(layer!=2) && (bank.getADCSize()==1) && (bank.getTDCSize()==1)) {

			//    		if (bank.getADCSize()>0) {
			//    			TOFPaddle  paddle = new TOFPaddle(
			//						sector,
			//						layer,
			//						component,
			//						bank.getADCData(0).getIntegral(),
			//						0, //bank.getADCData(1).getIntegral(),
			//						0, //bank.getTDCData(0).getTime(),
			//						0); //bank.getTDCData(1).getTime());
			//    			
			//				if (paddle.includeInCalib()) {
			//					paddleList.add(paddle);
			//				}
			//    		}
		}

		return paddleList;
	}	


	public static List<TOFPaddle> getPaddleListTWTest(DataEvent event){

		ArrayList<TOFPaddle>  paddleList = new ArrayList<TOFPaddle>();

		if (test) {
			EvioDataEvent e = (EvioDataEvent) event;
			e.show();
			if (event.hasBank("FTOF1A::dgtz")) {
				event.getBank("FTOF1A::dgtz").show();
			}
			if (event.hasBank("FTOF1B::dgtz")) {
				event.getBank("FTOF1B::dgtz").show();
			}
			if (event.hasBank("FTOF2B::dgtz")) {
				event.getBank("FTOF2B::dgtz").show();
			}		
			if (event.hasBank("FTOFRec::ftofhits")) {
				event.getBank("FTOFRec::ftofhits").show();
			}
		}

		float xpos = 0;
		float ypos = 0;
		if (event.hasBank("FTOFRec::ftofhits")) {
			EvioDataBank recBank = (EvioDataBank) event.getBank("FTOFRec::ftofhits");
			xpos = recBank.getFloat("x",0);
			ypos = recBank.getFloat("y",0);
		}

		if(event.hasBank("FTOF1A::dgtz")==true){
			EvioDataBank bank = (EvioDataBank) event.getBank("FTOF1A::dgtz");
			for(int loop = 0; loop < bank.rows(); loop++){
				TOFPaddle  paddle = new TOFPaddle(
						bank.getInt("sector", loop),
						1,
						bank.getInt("paddle", loop),
						bank.getInt("ADCL", loop),
						bank.getInt("ADCR", loop),
						bank.getInt("TDCL", loop),
						bank.getInt("TDCR", loop),
						xpos,
						ypos, 0.0, 0.0
						);
				paddleList.add(paddle);
			}
		}

		return paddleList;
	}	


	public static List<TOFPaddle> getPaddleListDgtz(DataEvent event){

		ArrayList<TOFPaddle>  paddleList = new ArrayList<TOFPaddle>();

		if (test) {
			//EvioDataEvent e = (EvioDataEvent) event;
			System.out.println("New event - dgtz");
			//e.show();

			if (event.hasBank("FTOF1A::dgtz")) {
				event.getBank("FTOF1A::dgtz").show();
			}
			if (event.hasBank("FTOF1B::dgtz")) {
				event.getBank("FTOF1B::dgtz").show();
			}
			if (event.hasBank("FTOF2B::dgtz")) {
				event.getBank("FTOF2B::dgtz").show();
			}		
			if (event.hasBank("FTOFRec::ftofhits")) {
				event.getBank("FTOFRec::ftofhits").show();
			}
			if (event.hasBank("FTOFRec::rawhits")) {
				event.getBank("FTOFRec::rawhits").show();
			}
		}

		String[] bankName = {"zero", "FTOF1A::dgtz", "FTOF1B::dgtz", "FTOF2B::dgtz"};

		for (int layer=1; layer<=3; layer++) {
			if (event.hasBank(bankName[layer])) {
				EvioDataBank dgtzBank = (EvioDataBank) event.getBank(bankName[layer]);

				for (int dgtzIndex = 0; dgtzIndex < dgtzBank.rows(); dgtzIndex++) {

					int sector = dgtzBank.getInt("sector", dgtzIndex);
					int component = dgtzBank.getInt("paddle", dgtzIndex);
					float xpos = 0;
					float ypos = 0;
					float timeL = 0;
					float timeR = 0;
					int rawIndex =0;
					TOFCalibration.totalStatHist.fill(((layer-1)*10)+sector);

					if (event.hasBank("FTOFRec::ftofhits")) {

						// find the corresponding row in the ftofhits bank
						// to get the hit position from tracking
						EvioDataBank hitsBank = (EvioDataBank) event.getBank("FTOFRec::ftofhits");
						for (int hitIndex = 0; hitIndex < hitsBank.rows(); hitIndex++) {

							if (sector==hitsBank.getInt("sector",hitIndex)
									&&
									layer==hitsBank.getInt("panel_id",hitIndex)
									&&
									component==hitsBank.getInt("paddle_id",hitIndex)
									&&
									dgtzBank.getInt("hitn",dgtzIndex)==hitsBank.getInt("id", hitIndex)) {

								if (hitsBank.getFloat("tx", hitIndex)!=0 && 
										hitsBank.getFloat("ty", hitIndex)!=0) {

									xpos = hitsBank.getFloat("tx", hitIndex);
									ypos = hitsBank.getFloat("ty", hitIndex);

									if (event.hasBank("FTOFRec::rawhits")) {
										// one to one correspondence between ftofhits and rawhits
										EvioDataBank rawBank = (EvioDataBank) event.getBank("FTOFRec::rawhits");
										timeL = rawBank.getFloat("time_left", hitIndex);
										timeR = rawBank.getFloat("time_right", hitIndex);
									}								
								}
							}
						}
					}

					TOFPaddle  paddle = new TOFPaddle(
							sector,
							layer,
							component,
							dgtzBank.getInt("ADCL", dgtzIndex),
							dgtzBank.getInt("ADCR", dgtzIndex),
							dgtzBank.getInt("TDCL", dgtzIndex),
							dgtzBank.getInt("TDCR", dgtzIndex),
							xpos,
							ypos,
							timeL,
							timeR);

					if (paddle.includeInCalib()) {
						paddleList.add(paddle);

						if (layer==1) {
							TOFCalibration.adcLeftHist1A.fill(dgtzBank.getInt("ADCL", dgtzIndex));
							TOFCalibration.adcRightHist1A.fill(dgtzBank.getInt("ADCR", dgtzIndex));
							if (xpos!=0.0 || ypos!=0.0) {
								TOFCalibration.trackingAdcLeftHist1A.fill(dgtzBank.getInt("ADCL", dgtzIndex));
								TOFCalibration.trackingAdcRightHist1A.fill(dgtzBank.getInt("ADCR", dgtzIndex));
							}
						}
						if (layer==2) {
							TOFCalibration.adcLeftHist1B.fill(dgtzBank.getInt("ADCL", dgtzIndex));
							TOFCalibration.adcRightHist1B.fill(dgtzBank.getInt("ADCR", dgtzIndex));
							if (xpos!=0.0 || ypos!=0.0) {
								TOFCalibration.trackingAdcLeftHist1B.fill(dgtzBank.getInt("ADCL", dgtzIndex));
								TOFCalibration.trackingAdcRightHist1B.fill(dgtzBank.getInt("ADCR", dgtzIndex));
							}
						}

						TOFCalibration.paddleHist.fill(((layer-1)*100)+component);
						if (xpos!=0.0 || ypos!=0.0) {
							TOFCalibration.trackingPaddleHist.fill(((layer-1)*100)+component);
						}

						if (test) {
							System.out.println("SLC "+sector+layer+component);
							System.out.println("ADCL "+dgtzBank.getInt("ADCL", dgtzIndex)+
									" ADCR "+dgtzBank.getInt("ADCR", dgtzIndex)+
									" TDCL "+dgtzBank.getInt("TDCL", dgtzIndex)+
									" TDCR "+dgtzBank.getInt("TDCR", dgtzIndex)+
									" xpos "+xpos+" ypos "+ypos);
						}
					}
				}

			}
		}

		if ((event.hasBank("FTOFRec::ftofhits")) && (test)) {

			EvioDataBank hitsBank = (EvioDataBank) event.getBank("FTOFRec::ftofhits");
			for (int hitIndex = 0; hitIndex < hitsBank.rows(); hitIndex++) {

				int l = hitsBank.getInt("panel_id",hitIndex);
				int s = hitsBank.getInt("sector",hitIndex);

				if ((hitsBank.getFloat("tx", hitIndex) == 0 ) &&
						(hitsBank.getFloat("ty", hitIndex) == 0))
					TOFCalibration.trackingZeroStatHist.fill(((l-1)*10)+s);
				else {
					TOFCalibration.trackingStatHist.fill(((l-1)*10)+s);
				}
			}
		}

		return paddleList;
	}


}
