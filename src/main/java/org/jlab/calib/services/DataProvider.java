package org.jlab.calib.services;

import java.util.ArrayList;
import java.util.List;

import org.jlab.detector.base.DetectorType;
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


/**
 *
 * @author gavalian
 */
public class DataProvider {

	public static List<TOFPaddle> getPaddleList(DataEvent event){
		ArrayList<TOFPaddle>  paddleList = new ArrayList<TOFPaddle>();

//		if (event.hasBank("FTOF::dgtz")) {
//			event.getBank("FTOF::dgtz").show();
//		}
//		
//		if (event.hasBank("TimeBasedTrkg::TBTracks")) {
//			event.getBank("TimeBasedTrkg::TBTracks").show();
//		}

		if (event.hasBank("FTOF::dgtz") && event.hasBank("TimeBasedTrkg::TBTracks")) {
			EvioDataBank bankDC = (EvioDataBank) event.getBank("TimeBasedTrkg::TBTracks");
			//ConstantProvider cp = DataBaseLoader.getGeometryConstants(DetectorType.FTOF);
//			FTOFFactory factory = new FTOFFactory();
//			FTOFDetectorMesh ftofDetector = factory.getDetectorGeant4(cp);
			
			if (bankDC.rows()==1) {

				double x = bankDC.getDouble("c3_x", 0); // Region 3 cross x-position in the lab
				double y = bankDC.getDouble("c3_y", 0); // Region 3 cross y-position in the lab
				double z = bankDC.getDouble("c3_z", 0); // Region 3 cross z-position in the lab
				double ux = bankDC.getDouble("c3_ux", 0); // Region 3 cross x-unit-dir in the lab
				double uy = bankDC.getDouble("c3_uy", 0); // Region 3 cross y-unit-dir in the lab
				double uz = bankDC.getDouble("c3_uz", 0); // Region 3 cross z-unit-dir in the lab

				EvioDataBank bankFTOF = (EvioDataBank) event.getBank("FTOF::dgtz");
				for(int k = 0; k < bankFTOF.rows(); k++)
				{
					int sector = bankFTOF.getInt("sector", k);
					int panel_id = bankFTOF.getInt("panel_id", k);
					int paddle_id = bankFTOF.getInt("paddle_id", k);
					float time_left = bankFTOF.getFloat("time_left", k);
					float time_right = bankFTOF.getFloat("time_right", k);

//					ScintillatorMesh geomPaddle = ftofDetector.getSector(sector).getSuperlayer(panel_id).getLayer(1).getComponent(paddle_id);
////					Line3D lineX = geomPaddle.getLineX(); // Line representing the paddle Length
//
//					Path3D path = new Path3D();
//					path.generate(new Point3D(x, y, z), new Vector3D(ux, uy, uz),  1500.0, 2);
//
//					Line3D intersect = path.distance(lineX); // intersection of the path with the paddle line
//					Point3D intP = intersect.end();
//
//					double veff = intP.y()*2/(time_left - time_right);
//					
				}
                
			}
			// else ignore event as can't match up multiple tracks right now
            	
		}
		
		float pos = 0;
		if (event.hasBank("FTOF::dgtz")) {
			EvioDataBank dgtzBank = (EvioDataBank) event.getBank("FTOF::dgtz");
//			dgtzBank.show();
			for (int dgtzIndex = 0; dgtzIndex < dgtzBank.rows(); dgtzIndex++) {

//				System.out.println("Creating paddle");
//				System.out.println(dgtzBank.getByte("sector", dgtzIndex) + " "+
//						dgtzBank.getByte("layer", dgtzIndex) + " "+
//						dgtzBank.getShort("component", dgtzIndex) + " "+
//						dgtzBank.getInt("ADCL", dgtzIndex) + " "+
//						dgtzBank.getInt("ADCR", dgtzIndex) + " "+
//						dgtzBank.getInt("TDCL", dgtzIndex) + " "+
//						dgtzBank.getInt("TDCR", dgtzIndex));
				TOFPaddle  paddle = new TOFPaddle(
						dgtzBank.getByte("sector", dgtzIndex),
						dgtzBank.getByte("layer", dgtzIndex),
						dgtzBank.getShort("component", dgtzIndex),
						dgtzBank.getInt("ADCL", dgtzIndex),
						dgtzBank.getInt("ADCR", dgtzIndex),
						dgtzBank.getInt("TDCL", dgtzIndex),
						dgtzBank.getInt("TDCR", dgtzIndex));
				
				if (paddle.includeInCalib()) {
					paddleList.add(paddle);
				}
			}
		}

		String[] bankName = {"zero", "FTOF1A::dgtz", "FTOF1B::dgtz", "FTOF2B::dgtz"};

		if (event.hasBank("FTOFRec::ftofhits")==true) {
			EvioDataBank hitsBank = (EvioDataBank) event.getBank("FTOFRec::ftofhits");

			for(int hitIndex = 0; hitIndex < hitsBank.rows(); hitIndex++){

				// Get corresponding dgtz bank
				String dgtzBankName = bankName[hitsBank.getInt("panel_id", hitIndex)];
				if (event.hasBank(dgtzBankName)) {
					EvioDataBank dgtzBank = (EvioDataBank) event.getBank(dgtzBankName);

					for (int dgtzIndex = 0; dgtzIndex < dgtzBank.rows(); dgtzIndex++) {

						if (dgtzBank.getInt("sector",dgtzIndex)==hitsBank.getInt("sector",hitIndex)
								&&
								dgtzBank.getInt("paddle",dgtzIndex)==hitsBank.getInt("paddle_id",hitIndex)) {

							// found a match on sector and paddle
							TOFPaddle  paddle = new TOFPaddle(
									dgtzBank.getInt("sector", dgtzIndex),
									hitsBank.getInt("panel_id", hitIndex),
									dgtzBank.getInt("paddle", dgtzIndex),
									dgtzBank.getInt("ADCL", dgtzIndex),
									dgtzBank.getInt("ADCR", dgtzIndex),
									dgtzBank.getInt("TDCL", dgtzIndex),
									dgtzBank.getInt("TDCR", dgtzIndex),
									hitsBank.getFloat("x", hitIndex),
									hitsBank.getFloat("y", hitIndex)
									);
							paddleList.add(paddle);

							break;
						}
					}
				}
				if (event.hasBank("FTOF::dgtz")) {
					EvioDataBank dgtzBank = (EvioDataBank) event.getBank("FTOF::dgtz");

					for (int dgtzIndex = 0; dgtzIndex < dgtzBank.rows(); dgtzIndex++) {

						if (dgtzBank.getInt("sector",dgtzIndex)==hitsBank.getInt("sector",hitIndex)
								&&
								dgtzBank.getInt("layer",dgtzIndex)==hitsBank.getInt("panel_id", hitIndex)
								&&
								dgtzBank.getInt("paddle",dgtzIndex)==hitsBank.getInt("paddle_id",hitIndex)) {

							// found a match on sector and paddle
							TOFPaddle  paddle = new TOFPaddle(
									dgtzBank.getInt("sector", dgtzIndex),
									hitsBank.getInt("panel_id", hitIndex),
									dgtzBank.getInt("paddle", dgtzIndex),
									dgtzBank.getInt("ADCL", dgtzIndex),
									dgtzBank.getInt("ADCR", dgtzIndex),
									dgtzBank.getInt("TDCL", dgtzIndex),
									dgtzBank.getInt("TDCR", dgtzIndex),
									hitsBank.getFloat("x", hitIndex),
									hitsBank.getFloat("y", hitIndex)
									);
							//if (paddle.includeInCalib()) {
								paddleList.add(paddle);
							//}

							break;
						}
					}
				}

			}
		}

//		System.out.println("returning paddle list "+paddleList.size());
		return paddleList;
	}

}
