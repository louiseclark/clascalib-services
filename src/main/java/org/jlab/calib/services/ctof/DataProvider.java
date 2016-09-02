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

		if (event.hasBank("CTOF::dgtz")) {
        	paddleList = getPaddleListDgtz(event);
		}
		return paddleList;
		
	}
	
	public static List<TOFPaddle> getPaddleListDgtz(DataEvent event){
	
		ArrayList<TOFPaddle>  paddleList = new ArrayList<TOFPaddle>();
		
		if (test) {
			EvioDataEvent e = (EvioDataEvent) event;
			e.show();
			if (event.hasBank("CTOF::dgtz")) {
				event.getBank("CTOF::dgtz").show();
			}

			if (event.hasBank("TimeBasedTrkg::TBTracks")) {
				event.getBank("TimeBasedTrkg::TBTracks").show();
			}
		}
		

		if (event.hasBank("CTOF::dgtz")) {
			EvioDataBank dgtzBank = (EvioDataBank) event.getBank("CTOF::dgtz");

			for (int dgtzIndex = 0; dgtzIndex < dgtzBank.rows(); dgtzIndex++) {

				int component = dgtzBank.getInt("paddle", dgtzIndex);

				double xpos = 0; // lab hit co ords from SVT projection
				double ypos = 0;
				// get lab hit co ords from SVT
				// 
//				if (event.hasBank("TimeBasedTrkg::TBTracks")) {
//					
//					EvioDataBank bankDC = (EvioDataBank) event.getBank("TimeBasedTrkg::TBTracks");
//
//					if (bankDC.rows()==1) {
//
//						double x = bankDC.getDouble("c3_x", 0); // Region 3 cross x-position in the lab
//						double y = bankDC.getDouble("c3_y", 0); // Region 3 cross y-position in the lab
//						double z = bankDC.getDouble("c3_z", 0); // Region 3 cross z-position in the lab
//						double ux = bankDC.getDouble("c3_ux", 0); // Region 3 cross x-unit-dir in the lab
//						double uy = bankDC.getDouble("c3_uy", 0); // Region 3 cross y-unit-dir in the lab
//						double uz = bankDC.getDouble("c3_uz", 0); // Region 3 cross z-unit-dir in the lab
//
//						// swim to CTOF radius
//						xpos = intP.x();
//						ypos = intP.y();
//
//					}
//				}
				// else don't set position for this event as can't match up multiple tracks right now

				TOFPaddle  paddle = new TOFPaddle(
						1,
						1,
						component,
						dgtzBank.getInt("ADCU", dgtzIndex),
						dgtzBank.getInt("ADCD", dgtzIndex),
						dgtzBank.getInt("TDCU", dgtzIndex),
						dgtzBank.getInt("TDCD", dgtzIndex),
						xpos,
						ypos);

				if (paddle.includeInCalib()) {
					paddleList.add(paddle);
				}
			}
		}

		//		System.out.println("returning paddle list "+paddleList.size());
		return paddleList;
	}

}
