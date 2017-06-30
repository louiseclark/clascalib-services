package org.jlab.calib.services;

import org.jlab.calib.services.ctof.CTOFCalibration;
import org.jlab.calib.services.ctof.CTOFCalibrationEngine;
import org.jlab.detector.base.DetectorDescriptor;

/**
 *
 * @author louiseclark
 */
public class TOFPaddle {

	private static final int LEFT = 0;
	private static final int RIGHT = 1;
	public static String tof = "FTOF";

	private DetectorDescriptor desc = new DetectorDescriptor();

	public int ADCL = 0;
	public int ADCR = 0;
	public int TDCL = 0;
	public int TDCR = 0;
	public float ADC_TIMEL = 0;
	public float ADC_TIMER = 0;
	public double XPOS = 0.0;
	public double YPOS = 0.0;
	public double ZPOS = 0.0;
	public double PATH_LENGTH = 0.0;
	public double BETA = 0.0;
	public double P = 0.0;
	public int TRACK_ID = -1;
	public double VERTEX_Z = 0.0;
	public double TRACK_REDCHI2 = 0.0;
	public int CHARGE = 0;
	public double RF_TIME = 124.25;
	public int TRIGGER_BIT = 0;
	public double TOF_TIME = 0.0;
	public int PARTICLE_ID = -1;

	public static final int PID_ELECTRON = 11;
	public static final int PID_PION = 211;
	private final double C = 29.98;
	public static final double NS_PER_CH = 0.02345;

	public TOFPaddle(int sector, int layer, int paddle) {
		this.desc.setSectorLayerComponent(sector, layer, paddle);
	}

	public void setAdcTdc(int adcL, int adcR, int tdcL, int tdcR) {
		this.ADCL = adcL;
		this.ADCR = adcR;
		this.TDCL = tdcL;
		this.TDCR = tdcR;
	}

	public void setPos(double xPos, double yPos, double zPos) {
		this.XPOS = xPos;
		this.YPOS = yPos;
		this.ZPOS = zPos;
	}

	public int paddleNumber() {

		int p = 0;
		int[] paddleOffset = {0, 0, 23, 85};
		int sector = this.getDescriptor().getSector();
		int layer = this.getDescriptor().getLayer();
		int component = this.getDescriptor().getComponent();

		p = component + (sector-1)*90 + paddleOffset[layer]; 
		return p;
	}

	public double geometricMean() {
		return Math.sqrt(ADCL * ADCR);
	}

	public double logRatio() {
		return Math.log((double) ADCR / (double) ADCL);
	}

	public boolean isValidGeoMean() {
		// return includeInCalib();
		return (this.geometricMean() > 100.0);
	}

	public boolean includeInCalib() {
		//return (ADCR != 0 || ADCL != 0);
		return (this.geometricMean() > 100.0 && ADCR>0 && ADCL>0 && TDCL>0 && TDCR>0);
	}

	//	public boolean includeInCtofVeff() {
	//		// exclude if position is zero or veff is unrealistic
	//		return (this.ZPOS != 0)
	//				&& (Math.abs(position() - this.zPosCTOF()) < 20.0);
	//	}

	public boolean isValidLogRatio() {
		// only if geometric mean is over a minimum
		double[] minGM = {0.0, 300.0, 500.0, 300.0};
		int layer = this.getDescriptor().getLayer();

		return this.geometricMean() > minGM[layer];
	}

	public double veff() {
		double veff = 16.0;
		if (tof == "FTOF") {
			veff = TOFCalibrationEngine.veffValues.getDoubleValue("veff_left",
					desc.getSector(), desc.getLayer(), desc.getComponent());
			//System.out.println("veff "+desc.getSector()+desc.getLayer()+desc.getComponent()+" "+veff);
		} else {
			veff = CTOFCalibrationEngine.veffValues.getDoubleValue("veff_left",
					desc.getSector(), desc.getLayer(), desc.getComponent());
		}

		return veff;
	}

	//	public double p2p() {
	//		double p2p = 0.0;
	//		if (tof == "FTOF") {
	//			p2p = TOFCalibrationEngine.p2pValues.getDoubleValue("paddle2paddle",
	//					desc.getSector(), desc.getLayer(), desc.getComponent())
	//				+ TOFCalibrationEngine.rfpadValues.getDoubleValue("rfpad", desc.getSector(), desc.getLayer(), desc.getComponent());
	//			//System.out.println("p2p "+desc.getSector()+desc.getLayer()+desc.getComponent()+" "+p2p);
	//		} else {
	//			p2p = 0.0;
	//			//p2p = CTOFCalibrationEngine.p2pValues.getItem(desc.getSector(), desc.getLayer(), desc.getComponent());
	//		}
	//
	//		return p2p;
	//	}

	public double rfpad() {
		double rfpad = 0.0;
		if (tof == "FTOF") {
			rfpad = TOFCalibrationEngine.rfpadValues.getDoubleValue("rfpad", desc.getSector(), desc.getLayer(), desc.getComponent());
		}
		else {
			rfpad = CTOFCalibrationEngine.rfpadValues.getDoubleValue("rfpad", desc.getSector(), desc.getLayer(), desc.getComponent());
		}
		return rfpad;
	}

	public double combinedRes() {
		double tr = 0.0;

		double timeL = tdcToTime(TDCL);
		double timeR = tdcToTime(TDCR);
		double lr = leftRightAdjustment();

		tr = ((timeL - timeR - lr) / 2)
				- (paddleY() / veff());

		return tr;
	}

	// timeResidualsADC
	// rename to timeResiduals to use this version comparing TDC time to ADC time
	public double[] timeResidualsADC(double[] lambda, double[] order, int iter) {
		double[] tr = { 0.0, 0.0 };

		double timeL = tdcToTime(TDCL);
		double timeR = tdcToTime(TDCR);

		tr[LEFT] = timeL - this.ADC_TIMEL;
		tr[RIGHT] = timeR - this.ADC_TIMER;

		return tr;
	}	

	public double startTime() {
		double startTime = 0.0;

		double beta = 1.0;
		if (BETA != 0.0) {
			beta = BETA;
		}

		//startTime = TOF_TIME - (PATH_LENGTH/(beta*29.98));
		startTime = p2pAverageHitTime() - (PATH_LENGTH/(beta*29.98));
		return startTime;
	}

	public double startTimeRFCorr() {
		return startTime() + rfpad();
	}

	public double p2pAverageHitTime() {

		double lr = leftRightAdjustment();
		double tL = timeLeftAfterTW() - (lr/2) 
		- ((0.5*paddleLength() + paddleY())/this.veff());

		double tR = timeRightAfterTW() + (lr/2)
				- ((0.5*paddleLength() - paddleY())/this.veff());

		return (tL+tR)/2.0;

	}

	public double tofTimeRFCorr() {
		return p2pAverageHitTime() + rfpad();
	}


	public double refTime() {
		return this.RF_TIME - this.startTime();
	}

	public double refTimeCorr() {
		return refTime() - rfpad();
	}

	public double deltaTLeft(double offset) {

		double lr = leftRightAdjustment();
		//		System.out.println("deltaTLeft");
		//		System.out.println("S " + desc.getSector() + " L " + desc.getLayer() + " C "
		//				+ desc.getComponent() + " ADCR " + ADCR + " ADCL " + ADCL
		//				+ " TDCR " + TDCR + " TDCL " + TDCL);

		double beta = 1.0;
		if (BETA != 0.0) {
			beta = BETA;
		}
		//		System.out.println("TDCL is "+TDCL);
		//		System.out.println("tdcToTime is "+tdcToTime(TDCL));
		//		System.out.println("lr is "+lr);
		//		System.out.println("rfpad is "+rfpad());
		//		System.out.println("paddleLength is "+paddleLength());
		//		System.out.println("paddleY is "+paddleY());
		//		System.out.println("veff is "+veff());
		//		System.out.println("PATH_LENGTH is "+PATH_LENGTH);
		//		System.out.println("beta is "+beta);
		//		System.out.println("RF_TIME is "+RF_TIME);


		double dtL = tdcToTime(TDCL) - (lr/2) + rfpad() 
				- ((0.5*paddleLength() + paddleY())/this.veff())
				- (PATH_LENGTH/(beta*29.98))
				- this.RF_TIME;

		//		System.out.println("dtL is "+dtL);
		//		System.out.println("ADCL is "+ADCL);

		// subtract the value of the nominal function
		dtL = dtL - (40.0 / (Math.pow(ADCL,0.5)));
		//        System.out.println("dtL2 is "+dtL);
		//        System.out.println("offset is "+offset);
		dtL = dtL + offset;
		//        System.out.println("dtL3 is "+dtL);
		double bb = TOFCalibrationEngine.BEAM_BUCKET;
		dtL = (dtL+(1000*bb) + (0.5*bb))%bb - 0.5*bb;
		//        System.out.println("dtL4 is "+dtL);

		//dtL = ((dtL +120.0)%BEAM_BUCKET);

		return dtL;
	}

	public double deltaTRight(double offset) {

		double lr = leftRightAdjustment();

		double beta = 1.0;
		if (BETA != 0.0) {
			beta = BETA;
		}

		double dtR = tdcToTime(TDCR) + (lr/2) + rfpad()
				- ((0.5*paddleLength() - paddleY())/this.veff())
				- (PATH_LENGTH/(beta*29.98))
				- this.RF_TIME;

		dtR = dtR - (40.0 / (Math.pow(ADCR,0.5)));
		dtR = dtR + offset;
		double bb = TOFCalibrationEngine.BEAM_BUCKET;
		dtR = (dtR+(1000*bb) + (0.5*bb))%bb - 0.5*bb;
		//dtR = ((dtR +120.0)%BEAM_BUCKET);

		return dtR;
	}

	public double paddleLength() {

		double len = 0.0;

		if (tof=="FTOF") {
			int layer = this.getDescriptor().getLayer();
			int paddle = this.getDescriptor().getComponent();

			if (layer == 1 && paddle <= 5) {
				len = (15.85 * paddle) + 16.43;
			} else if (layer == 1 && paddle > 5) {
				len = (15.85 * paddle) + 11.45;
			} else if (layer == 2) {
				len = (6.4 * paddle) + 10.84;
			} else if (layer == 3) {
				len = (13.73 * paddle) + 357.55;
			}
		}
		else {
			len = 100.0;
		}

		return len;

	}

	public double leftRight() {
		return (timeLeftAfterTW() - timeRightAfterTW());
	}

	public double timeLeftAfterTW() {
		if (tof=="FTOF") {
			return tdcToTime(TDCL) - (lamL() / Math.pow(ADCL, ordL()));
		}
		else {
			return tdcToTime(TDCL);
		}
	}

	public double timeRightAfterTW() {
		if (tof=="FTOF") {
			return tdcToTime(TDCR) - (lamR() / Math.pow(ADCR, ordR()));
		}
		else {
			return tdcToTime(TDCR);
		}
	}

	public boolean isValidLeftRight() {
		return (tdcToTime(TDCL) != tdcToTime(TDCR));
	}

	double tdcToTime(double value) {
		return NS_PER_CH * value;
	}

	public double halfTimeDiff() {

		double timeL = timeLeftAfterTW();
		double timeR = timeRightAfterTW();
		return (timeL - timeR - leftRightAdjustment()) / 2;
	}

	public double leftRightAdjustment() {

		double lr = 0.0;

		if (tof == "FTOF") {
			lr = TOFCalibrationEngine.leftRightValues.getDoubleValue("left_right", 
					desc.getSector(), desc.getLayer(), desc.getComponent());
			//System.out.println("lr "+desc.getSector()+desc.getLayer()+desc.getComponent()+" "+lr);

		} else {
			lr = CTOFCalibrationEngine.leftRightValues.getDoubleValue("left_right", 
					desc.getSector(), desc.getLayer(), desc.getComponent());
			//lr = -25.0;
		}

		return lr;
	}

	public double lamL() {
		double lamL = 0.0;
		if (tof == "FTOF") {
			lamL = TOFCalibrationEngine.timeWalkValues.getDoubleValue("tw0_left",
					desc.getSector(),desc.getLayer(),desc.getComponent());
		}
		return lamL;			
	}

	public double ordL() {
		double ordL = 0.5;
		if (tof == "FTOF") {
			ordL = TOFCalibrationEngine.timeWalkValues.getDoubleValue("tw1_left",
					desc.getSector(),desc.getLayer(),desc.getComponent());
		}
		return ordL;			
	}

	public double lamR() {
		double lamR = 0.0;
		if (tof == "FTOF") {
			lamR = TOFCalibrationEngine.timeWalkValues.getDoubleValue("tw0_right",
					desc.getSector(),desc.getLayer(),desc.getComponent());
		}
		return lamR;			
	}

	public double ordR() {
		double ordR = 0.5;
		if (tof == "FTOF") {
			ordR = TOFCalibrationEngine.timeWalkValues.getDoubleValue("tw1_right",
					desc.getSector(),desc.getLayer(),desc.getComponent());
		}
		return ordR;			
	}

	public double position() {

		return halfTimeDiff() * veff();
	}

	public double paddleY() {

		double y = 0.0;
		if (tof=="FTOF") {
			int sector = desc.getSector();
			double rotation = Math.toRadians((sector - 1) * 60);
			y= YPOS * Math.cos(rotation) - XPOS * Math.sin(rotation);
		}
		else {
			y = zPosCTOF();
		}
		return y;
	}

	public boolean trackFound() {
		return TRACK_ID != -1;
	};

	public boolean goodTrackFound() {

		double maxRcs = 75.0;
		double minV = -10.0;
		double maxV = 10.0;
		double minP = 1.0;
		if (tof=="FTOF") {
			maxRcs = TOFCalibration.maxRcs;
			minV = TOFCalibration.minV;
			maxV = TOFCalibration.maxV;
			minP = TOFCalibration.minP;
		}
		else {
			maxRcs = CTOFCalibration.maxRcs;
			minV = CTOFCalibration.minV;
			maxV = CTOFCalibration.maxV;
			minP = CTOFCalibration.minP;
		}


		return (trackFound() && 
				TRACK_REDCHI2 < maxRcs &&		
				VERTEX_Z > minV &&
				VERTEX_Z < maxV &&
				P > minP &&
				chargeMatch()); 
	}	

	public boolean chargeMatch() {

		int trackCharge = 0;
		if (tof=="FTOF") {
			trackCharge = TOFCalibration.trackCharge;
		}
		else {
			trackCharge = CTOFCalibration.trackCharge;
		}		

		return (trackCharge == TOFCalibration.TRACK_BOTH ||
				trackCharge == TOFCalibration.TRACK_NEG && CHARGE == -1 ||
				trackCharge == TOFCalibration.TRACK_POS && CHARGE == 1);
	}

	public double zPosCTOF() {
		return ZPOS + 19.3;
	}

	public DetectorDescriptor getDescriptor() {
		return this.desc;
	}

	public String toString() {
		return "S " + desc.getSector() + " L " + desc.getLayer() + " C "
				+ desc.getComponent() + " ADCR " + ADCR + " ADCL " + ADCL
				+ " TDCR " + TDCR + " TDCL " + TDCL + " Geometric Mean "
				+ geometricMean() + " Log ratio " + logRatio();
	}

	public void show() {
		System.out.println("");
		System.out.println("S " + desc.getSector() + " L " + desc.getLayer() + " C "
				+ desc.getComponent() + " ADCR " + ADCR + " ADCL " + ADCL
				+ " TDCR " + TDCR + " TDCL " + TDCL);
		System.out.println("XPOS "+XPOS+" YPOS "+YPOS+" ZPOS "+ZPOS+" PATH_LENGTH "+PATH_LENGTH+" TRACK_ID "+TRACK_ID);
		System.out.println("BETA "+BETA+" P "+P+" RF_TIME "+RF_TIME+" TOF_TIME "+TOF_TIME);
		System.out.println("VERTEX_Z "+VERTEX_Z+" TRACK_REDCHI2 "+TRACK_REDCHI2+" CHARGE "+CHARGE+" TRIGGER_BIT "+TRIGGER_BIT);
		System.out.println("goodTrackFound "+goodTrackFound()+" chargeMatch "+chargeMatch());
		System.out.println("refTime "+refTime()+" startTime "+startTime()+" averageHitTime "+p2pAverageHitTime());
		System.out.println("tofTimeRFCorr "+tofTimeRFCorr()+" startTimeRFCorr "+startTimeRFCorr());
		System.out.println("rfpad "+rfpad()+" lamL "+lamL()+" ordL "+ordL()+" lamR "+lamR()+" ordR "+ordR()+" LR "+leftRightAdjustment()+" veff "+veff());
		System.out.println("paddleLength "+paddleLength()+" paddleY "+paddleY());
		System.out.println("timeLeftAfterTW "+timeLeftAfterTW()+" timeRightAfterTW "+timeRightAfterTW());
		System.out.println("deltaTLeft "+this.deltaTLeft(0.0)+ " deltaTRight "+this.deltaTRight(0.0));

	}

}