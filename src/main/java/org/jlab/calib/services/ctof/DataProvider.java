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
	
	private static	boolean test = false;
	
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

//		if (test) {
//			if (event.hasBank("CTOF::adc")) {
//				event.show();
//				event.getBank("CTOF::adc").show();
//			}
//			if (event.hasBank("CTOF::tdc")) {
//				event.getBank("CTOF::tdc").show();
//			}
//			if (event.hasBank("CTOF::hits")) {
//				event.getBank("CTOF::hits").show();
//			}
//		}

		
		ArrayList<TOFPaddle>  paddleList = new ArrayList<TOFPaddle>();
		
		// Only continue if we have adc and tdc banks
		if (!event.hasBank("CTOF::adc") || !event.hasBank("CTOF::tdc")) {
			return paddleList;
		}

		DataBank  adcBank = event.getBank("CTOF::adc");
		DataBank  tdcBank = event.getBank("CTOF::tdc");

		if (test) {
			//event.show();
			event.getBank("CTOF::adc").show();
			event.getBank("CTOF::tdc").show();
		}
				
		// iterate through hits bank getting corresponding adc and tdc
		//if (event.hasBank("CTOF::hits")) {
		if (1==2) {
			DataBank  hitsBank = event.getBank("CTOF::hits");
			
			for (int hitIndex=0; hitIndex<hitsBank.rows(); hitIndex++) {

				TOFPaddle  paddle = new TOFPaddle(
						(int) hitsBank.getByte("sector", hitIndex),
						(int) hitsBank.getByte("layer", hitIndex),
						(int) hitsBank.getShort("component", hitIndex));
				paddle.setAdcTdc(
						adcBank.getInt("ADC", hitsBank.getShort("adc_idx1", hitIndex)),
						adcBank.getInt("ADC", hitsBank.getShort("adc_idx2", hitIndex)),
						tdcBank.getInt("TDC", hitsBank.getShort("tdc_idx1", hitIndex)),
						tdcBank.getInt("TDC", hitsBank.getShort("tdc_idx2", hitIndex)));
				paddle.setPos(hitsBank.getFloat("tx", hitIndex),
							  hitsBank.getFloat("ty", hitIndex),
							  hitsBank.getFloat("tz", hitIndex)); 

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
			
			
			// data seems to have ADC order 1 then 0
			// TDC order 3 then 2
			
			for (int i = 0; i < adcBank.rows(); i++) {
				//System.out.println("Louise 1 i="+i);
				int order = adcBank.getByte("order", i);
				int adc = adcBank.getInt("ADC", i);
				
				//System.out.println("Louise 2 order="+order+" adc="+adc);
				
				if (order==1 && adc != 0) {
					
					int sector = adcBank.getByte("sector", i);
					int layer = adcBank.getByte("layer", i);
					int component = adcBank.getShort("component", i);
					int adcL = 0;
					int adcR = adc;
					int tdcL = 0;
					int tdcR = 0;
					
					//System.out.println("Louise 3 component="+component);
					
					// ADC Right at index i
					// ADC left at index i+1 probably, but just search forward til find it
					for (int j=i+1; j < adcBank.rows(); j++) {
						
						//System.out.println("Louise 4 j="+j);
						
						int s = adcBank.getByte("sector", j);
						int l = adcBank.getByte("layer", j);
						int c = adcBank.getShort("component", j);
						int o = adcBank.getByte("order", j);
						
						//System.out.println("Louise 5 c="+c+" o="+o);
						
						if (s==sector && l==layer && c==component && o == 0) {
							// matching adc L
							
							//System.out.println("Louise 5 break after matching adcL");
							adcL = adcBank.getInt("ADC", j);
							break;
						}
					}

					// Now get matching TDCs
					// can search whole bank as it has fewer rows (only hits)
					// break when you find so always take the first one found
					
					//System.out.println("Louise 6");
					
					for (int tdci=0; tdci < tdcBank.rows(); tdci++) {
						
						//System.out.println("Louise 7 tdci="+tdci);
						int s = tdcBank.getByte("sector", tdci);
						int l = tdcBank.getByte("layer", tdci);
						int c = tdcBank.getShort("component", tdci);
						int o = tdcBank.getByte("order", tdci);
						
						//System.out.println("Louise 8 c="+c+" o="+o);
						if (s==sector && l==layer && c==component && o == 2) {
							// matching tdc L
							//System.out.println("Louise 9 break after matching tdcL");
							tdcL = tdcBank.getInt("TDC", tdci);
							break;
						}
					}
					for (int tdci=0; tdci < tdcBank.rows(); tdci++) {

						//System.out.println("Louise 10 tdci="+tdci);
						int s = tdcBank.getByte("sector", tdci);
						int l = tdcBank.getByte("layer", tdci);
						int c = tdcBank.getShort("component", tdci);
						int o = tdcBank.getByte("order", tdci);

						//System.out.println("Louise 11 c="+c+" o="+o);

						if (s==sector && l==layer && c==component && o == 3) {
							// matching tdc R
							//System.out.println("Louise 12 break after matching tdcr");
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
								// are they back to front??
								//adcR, adcL, tdcR, tdcL);

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

}
