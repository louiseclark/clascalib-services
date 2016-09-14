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

			if (event.hasBank("BST::true")) {
				event.getBank("BST::true").show();
			}
		}
		

		if (event.hasBank("CTOF::dgtz")) {
			EvioDataBank dgtzBank = (EvioDataBank) event.getBank("CTOF::dgtz");

			for (int dgtzIndex = 0; dgtzIndex < dgtzBank.rows(); dgtzIndex++) {

				int component = dgtzBank.getInt("paddle", dgtzIndex);

				double zpos = 0; // lab hit co ords from SVT projection
				
				// get lab hit co ords from SVT
				// 
				if (event.hasBank("BST::true")) {
					
					EvioDataBank bankSVT = (EvioDataBank) event.getBank("BST::true");

					//if (bankDC.rows()==1) {

						double x = bankSVT.getDouble("avgX", 0); // Maybe this is the pos from SVT???
						double y = bankSVT.getDouble("avgY", 0);  
						double z = bankSVT.getDouble("avgZ", 0); 
						double px = bankSVT.getDouble("px", 0); // Use momentum vector for the moment
						double py = bankSVT.getDouble("py", 0); // as velocity seems to be always zero...
						double pz = bankSVT.getDouble("pz", 0); 

						// swim to CTOF radius 250mm
						Path3D path = new Path3D();
						// how far is path from SVT to CTOF???
						path.generate(new Point3D(x, y, z), new Vector3D(px, py, pz),  1.0, 2);

						zpos = path.getLine(0).end().z();
						
						if (test) {
							System.out.println("x "+x);
							System.out.println("y "+y);
							System.out.println("z "+z);
							System.out.println("px "+px);
							System.out.println("py "+py);
							System.out.println("pz "+pz);
							System.out.println("z pos "+zpos);
						}

					//}
				}
				// else don't set position for this event as can't match up multiple tracks right now

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
						zpos);

				if (paddle.includeInCalib()) {
					paddleList.add(paddle);
				}
			}
		}

		//		System.out.println("returning paddle list "+paddleList.size());
		return paddleList;
	}

}
