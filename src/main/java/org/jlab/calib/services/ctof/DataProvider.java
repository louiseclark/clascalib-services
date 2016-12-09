package org.jlab.calib.services.ctof;

import java.util.ArrayList;
import java.util.List;

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
			EvioDataEvent e = (EvioDataEvent) event;
			//e.show();
		}

		if (event.hasBank("CTOF::dgtz")) {
        	paddleList = getPaddleListDgtz(event);
		}
		return paddleList;
		
	}
	
	public static List<TOFPaddle> getPaddleListDgtz(DataEvent event){
	
		ArrayList<TOFPaddle>  paddleList = new ArrayList<TOFPaddle>();
		
		boolean show = false;
		int nonZeroHit = 0;
		if (test) {
			EvioDataEvent e = (EvioDataEvent) event;
//			e.show();
//			if (event.hasBank("CTOF::dgtz")) {
//				event.getBank("CTOF::dgtz").show();
//			}
			
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
						}
						
					}

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
						0.0,0.0);
				
				if (show) {
					System.out.println("Paddle "+component+" zpos "+zpos+
							" tz "+event.getBank("CTOFRec::ctofhits").getFloat("tz", nonZeroHit));
				}
				
				if (paddle.includeInCalib()) {
					paddleList.add(paddle);
				}
			}
		}

		//		System.out.println("returning paddle list "+paddleList.size());
		return paddleList;
	}

}
