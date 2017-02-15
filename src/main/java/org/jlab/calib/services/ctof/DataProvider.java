package org.jlab.calib.services.ctof;

import java.util.ArrayList;
import java.util.List;

import org.jlab.calib.services.TOFCalibrationEngine;
import org.jlab.calib.services.TOFPaddle;
import org.jlab.detector.base.DetectorType;
import org.jlab.detector.base.GeometryFactory;
import org.jlab.detector.calib.utils.CalibrationConstants;
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
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.io.evio.EvioDataBank;
import org.jlab.io.evio.EvioDataEvent;
import org.jlab.utils.groups.IndexedTable;


/**
 *
 * @author gavalian
 */
public class DataProvider {
	
	private static	boolean test = true;
	
	public static List<TOFPaddle> getPaddleList(DataEvent event) {
		
		List<TOFPaddle>  paddleList = new ArrayList<TOFPaddle>();
		if (test) {
			//EvioDataEvent e = (EvioDataEvent) event;
			//e.show();
		}

		//if (event.hasBank("CTOF::dgtz")) {
        	//paddleList = getPaddleListDgtz(event);
		//}
		paddleList = getPaddleListHipo(event);
		
		return paddleList;
		
	}
	
	public static List<TOFPaddle> getPaddleListHipo(DataEvent event){

		if (test) {
			//event.show();
			if (event.hasBank("CTOF::adc")) {
				event.getBank("CTOF::adc").show();
			}
			if (event.hasBank("CTOF::tdc")) {
				event.getBank("CTOF::tdc").show();
			}
			if (event.hasBank("CTOF::hits")) {
				event.getBank("CTOF::hits").show();
			}
		}

		
		ArrayList<TOFPaddle>  paddleList = new ArrayList<TOFPaddle>();
		
		// Only continue if we have adc and tdc banks
		if (!event.hasBank("CTOF::adc") || !event.hasBank("CTOF::tdc")) {
			return paddleList;
		}

		DataBank  adcBank = event.getBank("CTOF::adc");
		DataBank  tdcBank = event.getBank("CTOF::tdc");
				
		// iterate through hits bank getting corresponding adc and tdc
		//if (event.hasBank("CTOF::hits")) {
		if (1==2) {
			DataBank  hitsBank = event.getBank("CTOF::hits");
			
			for (int hitIndex=0; hitIndex<hitsBank.rows(); hitIndex++) {

				TOFPaddle  paddle = new TOFPaddle(
						(int) hitsBank.getByte("sector", hitIndex),
						(int) hitsBank.getByte("layer", hitIndex),
						(int) hitsBank.getShort("component", hitIndex),
						adcBank.getInt("ADC", hitsBank.getShort("adc_idx1", hitIndex)),
						adcBank.getInt("ADC", hitsBank.getShort("adc_idx2", hitIndex)),
						tdcBank.getInt("TDC", hitsBank.getShort("tdc_idx1", hitIndex)),
						tdcBank.getInt("TDC", hitsBank.getShort("tdc_idx2", hitIndex)),
						hitsBank.getFloat("tx", hitIndex),
						hitsBank.getFloat("ty", hitIndex),
						0.0); 

				if (paddle.includeInCalib()) {
					paddleList.add(paddle);
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
								component,
								adcL, adcR, tdcL, tdcR);

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
	
	public static List<TOFPaddle> getPaddleListDgtz(DataEvent event){
	
		ArrayList<TOFPaddle>  paddleList = new ArrayList<TOFPaddle>();
		
		boolean show = false;
		int nonZeroHit = 0;
		if (test) {
			EvioDataEvent e = (EvioDataEvent) event;
			e.show();
			if (event.hasBank("CTOF::dgtz")) {
				event.getBank("CTOF::dgtz").show();
			}
			
			if (event.hasBank("CTOFRec::ctofhits")) {
				//event.getBank("CTOFRec::ctofhits").show();
				for (int i = 0; i < event.getBank("CTOFRec::ctofhits").rows(); i++) {
					
					if (event.getBank("CTOFRec::ctofhits").getFloat("tz", i) !=0) {
						show = true;
						nonZeroHit = i;
					}
					
				}
				if (show) {
					event.getBank("CTOF::dgtz").show();
					event.getBank("CTOFRec::ctofhits").show();
				}
			}
		}
		

		if (event.hasBank("CTOF::dgtz")) {
			EvioDataBank dgtzBank = (EvioDataBank) event.getBank("CTOF::dgtz");

			for (int dgtzIndex = 0; dgtzIndex < dgtzBank.rows(); dgtzIndex++) {

				int component = dgtzBank.getInt("paddle", dgtzIndex);
				float zpos = 0; // lab hit co ords from SVT projection
				float timeL = 0;
				float timeR = 0;
				
				if (show) {
					System.out.println("dgtz paddle_id "+component+" hitn "
							+dgtzBank.getInt("hitn",dgtzIndex));
				}
				
				if (event.hasBank("CTOFRec::ctofhits")) {
					
					// find the corresponding row in the ctofhits bank
					// to get the hit position from tracking
					EvioDataBank hitsBank = (EvioDataBank) event.getBank("CTOFRec::ctofhits");
					for (int hitIndex = 0; hitIndex < hitsBank.rows(); hitIndex++) {
						
						if (show) {
							System.out.println("hits paddle_id"+hitsBank.getInt("paddle_id", hitIndex));
						}
				
						if (component==hitsBank.getInt("paddle",hitIndex)
            				&&
            				dgtzBank.getInt("hitn",dgtzIndex)==hitsBank.getInt("id", hitIndex)
            				&&
            				hitsBank.getFloat("tz", hitIndex) != 0) {
							
							zpos = hitsBank.getFloat("tz", hitIndex);
							
							if (event.hasBank("CTOFRec::rawhits")) {
								// one to one correspondence between ftofhits and rawhits
								EvioDataBank rawBank = (EvioDataBank) event.getBank("CTOFRec::rawhits");
								timeL = rawBank.getFloat("time_up", hitIndex);
								timeR = rawBank.getFloat("time_down", hitIndex);
							}
						}
						
					}

				}

				if (test) {
					System.out.println("Creating paddle "+component);
					System.out.println("TDCU/TDCD "+dgtzBank.getInt("TDCU", dgtzIndex)+" "+
							dgtzBank.getInt("TDCD", dgtzIndex));
				}
				TOFPaddle  paddle = new TOFPaddle(
						1,
						1,
						component,
						dgtzBank.getInt("ADCU", dgtzIndex),
						dgtzBank.getInt("ADCD", dgtzIndex),
						dgtzBank.getInt("TDCU", dgtzIndex),
						dgtzBank.getInt("TDCD", dgtzIndex),
						0.0,
						0.0,
						zpos,
						timeL,timeR);
				
				if (show) {
					System.out.println("Paddle "+component+" zpos "+zpos+
							" tz "+event.getBank("CTOFRec::ctofhits").getFloat("tz", nonZeroHit));
				}
				
				// set status to ok if at least one reading
				if (paddle.ADCL!=0.0) {
					CTOFCalibrationEngine.adcLeftStatus.add(0, 1,1,component);
				}
				if (paddle.ADCR!=0.0) {
					CTOFCalibrationEngine.adcRightStatus.add(0, 1,1,component);
				}
				if (paddle.TDCL!=0.0) {
					CTOFCalibrationEngine.tdcLeftStatus.add(0, 1,1,component);
				}
				if (paddle.TDCR!=0.0) {
					CTOFCalibrationEngine.tdcRightStatus.add(0, 1,1,component);
				}

				if (paddle.includeInCalib()) {
					paddleList.add(paddle);
					//System.out.println("Writing paddle");
				}
			}
		}

		//		System.out.println("returning paddle list "+paddleList.size());
		return paddleList;
	}

}
