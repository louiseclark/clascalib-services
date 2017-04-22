package org.jlab.calib.services;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jlab.calib.temp.BaseHit;
import org.jlab.calib.temp.DetectorLocation;
import org.jlab.calib.temp.IMatchedHit;
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
			//event.show();
		}
		//		EvioDataEvent e = (EvioDataEvent) event;
		//		e.show();

		paddleList = getPaddleListHipo(event);
		//paddleList = getPaddleListDgtzNew(event);

		return paddleList;

	}


	public static List<TOFPaddle> getPaddleListHipo(DataEvent event){

		boolean refPaddleFound = false;
		boolean testPaddleFound = false;
		
		if (test) {

			event.show();
			if (event.hasBank("FTOF::adc")) {
				event.getBank("FTOF::adc").show();
			}
			if (event.hasBank("FTOF::tdc")) {
				event.getBank("FTOF::tdc").show();
			}
			if (event.hasBank("FTOF::hits")) {
				event.getBank("FTOF::hits").show();
			}
			if (event.hasBank("HitBasedTrkg::HBTracks")) {
				event.getBank("HitBasedTrkg::HBTracks").show();
			}
			if (event.hasBank("TimeBasedTrkg::TBTracks")) {
				event.getBank("TimeBasedTrkg::TBTracks").show();
			}			
			if (event.hasBank("RUN::rf")) {
				event.getBank("RUN::rf").show();
			}
			if (event.hasBank("MC::Particle")) {
				event.getBank("MC::Particle").show();
			}
		}

		ArrayList<TOFPaddle>  paddleList = new ArrayList<TOFPaddle>();

		// Only continue if we have adc and tdc banks
		if (!event.hasBank("FTOF::adc") || !event.hasBank("FTOF::tdc")) {
			return paddleList;
		}

		DataBank  adcBank = event.getBank("FTOF::adc");
		DataBank  tdcBank = event.getBank("FTOF::tdc");

		// Get the generated electron
//		Particle electronGen = null;

		// loop over generated particles
//		DataBank genBank = event.getBank("MC::Particle");
//		if(genBank!=null) {
//			int nrows = genBank.rows();
//			for(int loop = 0; loop < nrows; loop++) {   
//				Particle genPart = new Particle(
//						genBank.getInt("pid", loop),
//						genBank.getFloat("px", loop),
//						genBank.getFloat("py", loop),
//						genBank.getFloat("pz", loop),
//						genBank.getFloat("vx", loop),
//						genBank.getFloat("vy", loop),
//						genBank.getFloat("vz", loop));
//				if(genPart.pid()==11) {
//					electronGen = genPart;
//				}
//			}
//		}		

		// iterate through hits bank getting corresponding adc and tdc
		if (event.hasBank("FTOF::hits")) {
			DataBank  hitsBank = event.getBank("FTOF::hits");

			for (int hitIndex=0; hitIndex<hitsBank.rows(); hitIndex++) {

				double tx     = hitsBank.getFloat("tx", hitIndex);
				double ty     = hitsBank.getFloat("ty", hitIndex);
				double tz     = hitsBank.getFloat("tz", hitIndex);
				//System.out.println("tx ty tz"+tx+" "+ty+" "+tz);

				TOFPaddle  paddle = new TOFPaddle(
						(int) hitsBank.getByte("sector", hitIndex),
						(int) hitsBank.getByte("layer", hitIndex),
						(int) hitsBank.getShort("component", hitIndex));
				paddle.setAdcTdc(
						adcBank.getInt("ADC", hitsBank.getShort("adc_idx1", hitIndex)),
						adcBank.getInt("ADC", hitsBank.getShort("adc_idx2", hitIndex)),
						tdcBank.getInt("TDC", hitsBank.getShort("tdc_idx1", hitIndex)),
						tdcBank.getInt("TDC", hitsBank.getShort("tdc_idx2", hitIndex)));
				paddle.setPos(tx,ty,tz); 
				paddle.ADC_TIMEL = adcBank.getFloat("time", hitsBank.getShort("adc_idx1", hitIndex));
				paddle.ADC_TIMER = adcBank.getFloat("time", hitsBank.getShort("adc_idx2", hitIndex));
				paddle.TOF_TIME = hitsBank.getFloat("time", hitIndex);
				
				//System.out.println("Paddle created "+paddle.getDescriptor().getSector()+paddle.getDescriptor().getLayer()+paddle.getDescriptor().getComponent());

				if (event.hasBank("TimeBasedTrkg::TBTracks") && event.hasBank("RUN::rf")) {

					DataBank  tbtBank = event.getBank("TimeBasedTrkg::TBTracks");
					DataBank  rfBank = event.getBank("RUN::rf");
					
					// get the RF time with id=1
					double trf = 0.0; 
					for (int rfIdx=0; rfIdx<rfBank.rows(); rfIdx++) {
						if (rfBank.getShort("id",rfIdx)==1) {
							trf = rfBank.getFloat("time",rfIdx);
						}
					}

					// Identify electrons and store path length etc for time walk
					int trkId = hitsBank.getShort("trackid", hitIndex);
					double energy = hitsBank.getFloat("energy", hitIndex);
					
					//System.out.println("trkId energy trf "+trkId+" "+energy+" "+trf);

					// only use hit with associated track and a minimum energy
					if (trkId!=-1 && energy>1.5) {
						double c3x  = tbtBank.getFloat("c3_x",trkId-1);
						double c3y  = tbtBank.getFloat("c3_y",trkId-1);
						double c3z  = tbtBank.getFloat("c3_z",trkId-1);
						double path = tbtBank.getFloat("pathlength",trkId-1) + Math.sqrt((tx-c3x)*(tx-c3x)+(ty-c3y)*(ty-c3y)+(tz-c3z)*(tz-c3z));
						paddle.PATH_LENGTH = path;
						paddle.RF_TIME = trf;
						
						// Get the momentum and record the beta (assuming every hit is a pion!)
						double px  = tbtBank.getFloat("p0_x",trkId-1);
						double py  = tbtBank.getFloat("p0_y",trkId-1);
						double pz  = tbtBank.getFloat("p0_z",trkId-1);
						double mom = Math.sqrt(px*px + py*py + pz*pz);
						double beta = mom/Math.sqrt(mom*mom+0.139*0.139);
						paddle.BETA = beta;
						paddle.P = mom;
						paddle.TRACK_ID = trkId;
						
						if (paddle.getDescriptor().getComponent()==9 &&
								paddle.getDescriptor().getLayer()== 1 && trkId !=-1) {
							refPaddleFound = true;
						}
						if (paddle.getDescriptor().getComponent()==35 &&
								paddle.getDescriptor().getLayer()== 2 && trkId !=-1) {
							testPaddleFound = true;
						}

						// check if it's an electron by matching to the generated particle
						int    q    = tbtBank.getByte("q",trkId-1);
						double p0x  = tbtBank.getFloat("p0_x",trkId-1);
						double p0y  = tbtBank.getFloat("p0_y",trkId-1);
						double p0z  = tbtBank.getFloat("p0_z",trkId-1);
						Particle recParticle = new Particle(11,p0x,p0y,p0z,0,0,0);

//						System.out.println("q "+q);
//						System.out.println("recParticle.p() "+recParticle.p());
//						System.out.println("electronGen.p() "+electronGen.p());
						// select negative tracks matching the generated electron as electron candidates
//						if(q==-1
//								&& Math.abs(recParticle.p()-electronGen.p())<0.5
//								&& Math.abs(Math.toDegrees(recParticle.theta()-electronGen.theta()))<2.0
//								&& Math.abs(Math.toDegrees(recParticle.phi()-electronGen.phi()))<8) {
//							paddle.PARTICLE_ID = TOFPaddle.PID_ELECTRON;
//						} 
//						else {
//							paddle.PARTICLE_ID = TOFPaddle.PID_PION;
//						}
					}
				}
				
				
				if (refPaddleFound && testPaddleFound) {

					event.show();
					if (event.hasBank("FTOF::adc")) {
						event.getBank("FTOF::adc").show();
					}
					if (event.hasBank("FTOF::tdc")) {
						event.getBank("FTOF::tdc").show();
					}
					if (event.hasBank("FTOF::hits")) {
						event.getBank("FTOF::hits").show();
					}
					if (event.hasBank("HitBasedTrkg::HBTracks")) {
						event.getBank("HitBasedTrkg::HBTracks").show();
					}
					if (event.hasBank("TimeBasedTrkg::TBTracks")) {
						event.getBank("TimeBasedTrkg::TBTracks").show();
					}
					if (event.hasBank("RUN::rf")) {
						event.getBank("RUN::rf").show();
					}
					if (event.hasBank("RUN::config")) {
						event.getBank("RUN::config").show();
					}					
					if (event.hasBank("MC::Particle")) {
						event.getBank("MC::Particle").show();
					}
					refPaddleFound = false;
					testPaddleFound = false;
				}				

				//System.out.println("Adding paddle to list");
				if (paddle.includeInCalib()) {
					paddleList.add(paddle);
//					System.out.println("Paddle added to list SLC "+paddle.getDescriptor().getSector()+paddle.getDescriptor().getLayer()+paddle.getDescriptor().getComponent());
//					System.out.println("Particle ID "+paddle.PARTICLE_ID);
//					System.out.println("position "+paddle.XPOS+" "+paddle.YPOS);
//					System.out.println("trackFound "+paddle.trackFound());
				}
			}
		}
		else {
			// no hits bank, so just use adc and tdc
			// making assumptions about the order of fields as can't get Veronique's matching to work
			//			for (int i = 0; i < adcBank.rows(); i++) {
			//				int order = adcBank.getByte("order", i);
			//				if (order==0) {
			//					// ADC Left at index i
			//					// ADC Right at index i+1
			//					int adcL = adcBank.getInt("ADC", i);
			//					int adcR = adcBank.getInt("ADC", i+1);
			//					if (adcL>100 && adcR>100) {
			//						int sector = adcBank.getByte("sector", i);
			//						int layer = adcBank.getByte("layer", i);
			//						int component = adcBank.getShort("component", i);
			//						int tdcL = tdcBank.getInt("TDC", i);
			//						int tdcR = tdcBank.getInt("TDC", i+1);
			//
			//						TOFPaddle  paddle = new TOFPaddle(
			//								sector,
			//								layer,
			//								component,
			//								adcL, adcR, tdcL, tdcR);
			//
			//						if (paddle.includeInCalib()) {
			//							paddleList.add(paddle);							
			//						}
			//					}
			//				}
			//			}


			// based on cosmic data
			// am getting entry for every PMT in ADC bank
			// ADC R two indices after ADC L (will assume right is always after left)
			// TDC bank only has actual hits, so can just search the whole bank for matching SLC

			for (int i = 0; i < adcBank.rows(); i++) {
				int order = adcBank.getByte("order", i);
				int adc = adcBank.getInt("ADC", i);
				if (order==0 && adc != 0) {

					int sector = adcBank.getByte("sector", i);
					int layer = adcBank.getByte("layer", i);
					int component = adcBank.getShort("component", i);
					int adcL = adc;
					int adcR = 0;
					float adcTimeL = adcBank.getFloat("time", i);
					float adcTimeR = 0;
					int tdcL = 0;
					int tdcR = 0;

					// ADC Left at index i
					// ADC Right at index i+2 probably, but just search forward til find it
					for (int j=i+1; j < adcBank.rows(); j++) {
						int s = adcBank.getByte("sector", j);
						int l = adcBank.getByte("layer", j);
						int c = adcBank.getShort("component", j);
						int o = adcBank.getByte("order", j);
						if (s==sector && l==layer && c==component && o == 1) {
							// matching adc R
							adcR = adcBank.getInt("ADC", j);
							adcTimeR = adcBank.getFloat("time", j);
							break;
						}
					}

					// Now get matching TDCs
					// can search whole bank as it has fewer rows (only hits)
					// break when you find so always take the first one found
					for (int tdci=0; tdci < tdcBank.rows(); tdci++) {
						int s = tdcBank.getByte("sector", tdci);
						int l = tdcBank.getByte("layer", tdci);
						int c = tdcBank.getShort("component", tdci);
						int o = tdcBank.getByte("order", tdci);
						if (s==sector && l==layer && c==component && o == 2) {
							// matching tdc L
							tdcL = tdcBank.getInt("TDC", tdci);
							break;
						}
					}
					for (int tdci=0; tdci < tdcBank.rows(); tdci++) {
						int s = tdcBank.getByte("sector", tdci);
						int l = tdcBank.getByte("layer", tdci);
						int c = tdcBank.getShort("component", tdci);
						int o = tdcBank.getByte("order", tdci);
						if (s==sector && l==layer && c==component && o == 3) {
							// matching tdc R
							tdcR = tdcBank.getInt("TDC", tdci);
							break;
						}
					}

					if (test) {
						System.out.println("Values found "+sector+layer+component);
						System.out.println(adcL+" "+adcR+" "+tdcL+" "+tdcR);
					}

					if (adcL>100 && adcR>100) {

						TOFPaddle  paddle = new TOFPaddle(
								sector,
								layer,
								component);
						paddle.setAdcTdc(adcL, adcR, tdcL, tdcR);
						paddle.ADC_TIMEL = adcTimeL;
						paddle.ADC_TIMER = adcTimeR;

						if (paddle.includeInCalib()) {

							if (test) {
								System.out.println("Adding paddle "+sector+layer+component);
								System.out.println(adcL + " "+adcR+" "+tdcL+" "+tdcR);
							}
							paddleList.add(paddle);							
						}
					}
				}
			}

		}
		//		else {
		//			// no hits bank, so just use adc and tdc
		//			HitReader hitReader = new HitReader(); 
		//			List<BaseHit> hitList = hitReader.fetch_Hits(event);
		//			
		//			for (int i = 0; i < hitList.size(); i++) {
		//				if (test) {
		//					System.out.println("hit " + hitList.get(i).get_Id()
		//							+ " sector " + hitList.get(i).get_Sector()
		//							+ " panel " + hitList.get(i).get_Layer()
		//							+ " paddle " + hitList.get(i).get_Component()
		//							+ " ADCL " + hitList.get(i).ADC1 + " ADCR "
		//							+ hitList.get(i).ADC2 + " TDCL "
		//							+ hitList.get(i).TDC1 + " TDCR "
		//							+ hitList.get(i).TDC2);
		//				}
		//
		//				TOFPaddle paddle = new TOFPaddle((int) hitList.get(i)
		//						.get_Sector(), (int) hitList.get(i).get_Layer(),
		//						(int) hitList.get(i).get_Component(),
		//						(int) hitList.get(i).ADC1, (int) hitList.get(i).ADC2,
		//						(int) hitList.get(i).TDC1, (int) hitList.get(i).TDC2);
		//
		//				if (paddle.includeInCalib()) {
		//					// System.out.println("Adding paddle");
		//					paddleList.add(paddle);
		//				}
		//			}
		//
		//		}

		return paddleList;
	}

	public static void systemOut(String text) {
		boolean test = false;
		if (test) {
			System.out.println(text);
		}
	}

}
