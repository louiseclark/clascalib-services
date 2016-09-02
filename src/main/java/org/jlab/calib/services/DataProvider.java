package org.jlab.calib.services;

import java.util.ArrayList;
import java.util.List;

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

	public static FTOFDetectorMesh ftofDetector;
	private static	boolean test = false;

	public static void getGeometry() {

		ConstantProvider cp = GeometryFactory.getConstants(DetectorType.FTOF);
		FTOFFactory factory = new FTOFFactory();
		ftofDetector = factory.getDetectorGeant4(cp);
	}

	public static List<TOFPaddle> getPaddleList(DataEvent event) {

		List<TOFPaddle>  paddleList = new ArrayList<TOFPaddle>();

				if (event.hasBank("FTOF1A::dgtz")||event.hasBank("FTOF1B::dgtz")||event.hasBank("FTOF2B::dgtz")) {
					paddleList = getPaddleListDgtzOld(event);
				}
//				else { 
//		        	paddleList = getPaddleListDgtzNew(event);
//				}

		//paddleList = getPaddleListTWTest(event);
		return paddleList;

	}

	public static List<TOFPaddle> getPaddleListDgtzNew(DataEvent event){

		ArrayList<TOFPaddle>  paddleList = new ArrayList<TOFPaddle>();

		if (test) {
			EvioDataEvent e = (EvioDataEvent) event;
			e.show();
			if (event.hasBank("FTOF::dgtz")) {
				event.getBank("FTOF::dgtz").show();
			}
			if (event.hasBank("FTOFRec::ftofhits")) {
				event.getBank("FTOFRec::ftofhits").show();
			}
			if (event.hasBank("TimeBasedTrkg::TBTracks")) {
				event.getBank("TimeBasedTrkg::TBTracks").show();
			}
		}

		if (event.hasBank("FTOF::dgtz")) {
			EvioDataBank dgtzBank = (EvioDataBank) event.getBank("FTOF::dgtz");

			for (int dgtzIndex = 0; dgtzIndex < dgtzBank.rows(); dgtzIndex++) {

				byte sector = dgtzBank.getByte("sector", dgtzIndex);
				byte layer = dgtzBank.getByte("layer", dgtzIndex);
				short component = dgtzBank.getShort("component", dgtzIndex);

				double xpos = 0; // lab hit co ords from DC
				double ypos = 0;
				// get lab hit co ords from DC
				if (event.hasBank("TimeBasedTrkg::TBTracks")) {

					EvioDataBank bankDC = (EvioDataBank) event.getBank("TimeBasedTrkg::TBTracks");

					if (bankDC.rows()==1) {

						double x = bankDC.getDouble("c3_x", 0); // Region 3 cross x-position in the lab
						double y = bankDC.getDouble("c3_y", 0); // Region 3 cross y-position in the lab
						double z = bankDC.getDouble("c3_z", 0); // Region 3 cross z-position in the lab
						double ux = bankDC.getDouble("c3_ux", 0); // Region 3 cross x-unit-dir in the lab
						double uy = bankDC.getDouble("c3_uy", 0); // Region 3 cross y-unit-dir in the lab
						double uz = bankDC.getDouble("c3_uz", 0); // Region 3 cross z-unit-dir in the lab

						ScintillatorMesh geomPaddle = ftofDetector.getSector(sector).getSuperlayer(layer).getLayer(1).getComponent(component);
						Line3D lineX = geomPaddle.getLineX(); // Line representing the paddle Length

						Path3D path = new Path3D();
						path.generate(new Point3D(x, y, z), new Vector3D(ux, uy, uz),  1500.0, 2);

						Line3D intersect = path.distance(lineX); // intersection of the path with the paddle line
						Point3D intP = intersect.end();

						xpos = intP.x();
						ypos = intP.y();

					}
				}
				// else don't set position for this event as can't match up multiple tracks right now

				TOFPaddle  paddle = new TOFPaddle(
						sector,
						layer,
						component,
						dgtzBank.getInt("ADCL", dgtzIndex),
						dgtzBank.getInt("ADCR", dgtzIndex),
						dgtzBank.getInt("TDCL", dgtzIndex),
						dgtzBank.getInt("TDCR", dgtzIndex),
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

	public static List<TOFPaddle> getPaddleListDgtzOld(DataEvent event){

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
			if (event.hasBank("FTOF1A::true")) {
				event.getBank("FTOF1A::true").show();
			}
			if (event.hasBank("FTOF1B::true")) {
				event.getBank("FTOF1B::true").show();
			}
			if (event.hasBank("FTOF2B::true")) {
				event.getBank("FTOF2B::true").show();
			}
			if (event.hasBank("FTOFRec::ftofhits")) {
				event.getBank("FTOFRec::ftofhits").show();
			}
			if (event.hasBank("TimeBasedTrkg::TBTracks")) {
				event.getBank("TimeBasedTrkg::TBTracks").show();
			}
		}

		String[] bankName = {"zero", "FTOF1A::dgtz", "FTOF1B::dgtz", "FTOF2B::dgtz"};

		for (int layer=1; layer<=3; layer++) {
			if (event.hasBank(bankName[layer])) {
				EvioDataBank dgtzBank = (EvioDataBank) event.getBank(bankName[layer]);

				for (int dgtzIndex = 0; dgtzIndex < dgtzBank.rows(); dgtzIndex++) {

					int sector = dgtzBank.getInt("sector", dgtzIndex);
					int component = dgtzBank.getInt("paddle", dgtzIndex);

					double xpos = 0; // lab hit co ords from DC
					double ypos = 0;
					// get lab hit co ords from DC
					if (event.hasBank("TimeBasedTrkg::TBTracks")) {

						EvioDataBank bankDC = (EvioDataBank) event.getBank("TimeBasedTrkg::TBTracks");

						if (bankDC.rows()==1) {

							double x = bankDC.getDouble("c3_x", 0); // Region 3 cross x-position in the lab
							double y = bankDC.getDouble("c3_y", 0); // Region 3 cross y-position in the lab
							double z = bankDC.getDouble("c3_z", 0); // Region 3 cross z-position in the lab
							double ux = bankDC.getDouble("c3_ux", 0); // Region 3 cross x-unit-dir in the lab
							double uy = bankDC.getDouble("c3_uy", 0); // Region 3 cross y-unit-dir in the lab
							double uz = bankDC.getDouble("c3_uz", 0); // Region 3 cross z-unit-dir in the lab

							ScintillatorMesh geomPaddle = ftofDetector.getSector(sector).getSuperlayer(layer).getLayer(1).getComponent(component);
							Line3D lineX = geomPaddle.getLineX(); // Line representing the paddle Length

							Path3D path = new Path3D();
							path.generate(new Point3D(x, y, z), new Vector3D(ux, uy, uz),  1500.0, 2);

							Line3D intersect = path.distance(lineX); // intersection of the path with the paddle line
							Point3D intP = intersect.end();

							xpos = intP.x();
							ypos = intP.y();

						}
					}
					// else don't set position for this event as can't match up multiple tracks right now

					TOFPaddle  paddle = new TOFPaddle(
							sector,
							layer,
							component,
							dgtzBank.getInt("ADCL", dgtzIndex),
							dgtzBank.getInt("ADCR", dgtzIndex),
							dgtzBank.getInt("TDCL", dgtzIndex),
							dgtzBank.getInt("TDCR", dgtzIndex),
							xpos,
							ypos);

					if (paddle.includeInCalib()) {
						paddleList.add(paddle);
					}
				}

			}
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
						ypos
						);
				paddleList.add(paddle);
			}
		}

		return paddleList;
	}	

}
