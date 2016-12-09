package org.jlab.calib.services;

import java.util.ArrayList;
import java.util.List;

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

//		if (test) {
//			EvioDataEvent e = (EvioDataEvent) event;
//	    	System.out.println("New event - dgtz NEW");
//			e.show();
//			
//			if (event.hasBank("FTOF::dgtz")) {
//				event.getBank("FTOF::dgtz").show();
//			}
//			if (event.hasBank("FTOFRec::ftofhits")) {
//				event.getBank("FTOFRec::ftofhits").show();
//			}
//			if (event.hasBank("FTOFRec::rawhits")) {
//				event.getBank("FTOFRec::rawhits").show();
//			}
//		}
		
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

	public static List<TOFPaddle> getPaddleListDgtzNew(DataEvent event){

		ArrayList<TOFPaddle>  paddleList = new ArrayList<TOFPaddle>();

		if (test) {
			EvioDataEvent e = (EvioDataEvent) event;
	    	System.out.println("New event - dgtz NEW");
			e.show();
			
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
