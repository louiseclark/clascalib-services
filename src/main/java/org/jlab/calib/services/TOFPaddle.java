package org.jlab.calib.services;

import org.jlab.calib.services.ctof.CTOFCalibrationEngine;
import org.jlab.detector.base.DetectorDescriptor;

/**
 *
 * @author gavalian
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
	public double TIMEL = 0.0;
	public double TIMER = 0.0;
	public double XPOS = 0.0;
	public double YPOS = 0.0;
	public double ZPOS = 0.0;
	public double PATH_LENGTH = 0.0;
	public double RF_TIME = 124.25;
	public double TOF_TIME = 0.0;
	public double FLIGHT_TIME = 0.0;
	public double VERTEX_Z = 0.0;
	public int PARTICLE_ID = -1;
	public double START_TIME = 0.0;

	public final int ELECTRON = 0;
	public final int PION = 1;
	private final double C = 29.98;

	public TOFPaddle(int sector, int layer, int paddle) {
		this.desc.setSectorLayerComponent(sector, layer, paddle);
	}

	public TOFPaddle(int sector, int layer, int paddle, int adcL, int adcR,
			int tdcL, int tdcR) {
		this.desc.setSectorLayerComponent(sector, layer, paddle);
		this.ADCL = adcL;
		this.ADCR = adcR;
		this.TDCL = tdcL;
		this.TDCR = tdcR;
	}

	public TOFPaddle(int sector, int layer, int paddle, int adcL, int adcR,
			int tdcL, int tdcR, double xpos, double ypos, double pathlength) {
		this.desc.setSectorLayerComponent(sector, layer, paddle);
		this.ADCL = adcL;
		this.ADCR = adcR;
		this.TDCL = tdcL;
		this.TDCR = tdcR;
		this.XPOS = xpos;
		this.YPOS = ypos;
		this.PATH_LENGTH = pathlength;
	}

	public TOFPaddle(int sector, int layer, int paddle, int adcL, int adcR,
			int tdcL, int tdcR, double xpos, double ypos, double zpos,
			double timeL, double timeR) {
		this.desc.setSectorLayerComponent(sector, layer, paddle);
		this.ADCL = adcL;
		this.ADCR = adcR;
		this.TDCL = tdcL;
		this.TDCR = tdcR;
		this.XPOS = xpos;
		this.YPOS = ypos;
		this.ZPOS = zpos;
		this.TIMEL = timeL;
		this.TIMER = timeR;
	}

	public final void setData(int adcL, int adcR, int tdcL, int tdcR,
			double xpos, double ypos) {
		this.ADCL = adcL;
		this.ADCR = adcR;
		this.TDCL = tdcL;
		this.TDCR = tdcR;
		this.XPOS = xpos;
		this.YPOS = ypos;
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

	public boolean includeInVeff() {
		// exclude if position is zero or veff is unrealistic
		return (this.XPOS != 0 || this.YPOS != 0 || this.ZPOS != 0);
		//				&& (Math.abs(position() - paddleY()) < 40.0);
		// &&
		// (this.paddleY()/this.halfTimeDiff() > 4.0)
		// &&
		// (this.paddleY()/this.halfTimeDiff() < 28.0);
	}

	public boolean includeInCtofVeff() {
		// exclude if position is zero or veff is unrealistic
		return (this.ZPOS != 0)
				&& (Math.abs(position() - this.zPosCTOF()) < 20.0);
	}

	public boolean includeInTimeWalk() {
		// exclude if position is zero or veff is unrealistic
		int layer = this.getDescriptor().getLayer();
		int[] TW_ADC_MIN = {0,450,1250,450};
		int[] TW_ADC_MAX = {0,1550,3050,1550};
		
		double[] MIP = {0.0, 800.0, 2000.0, 800.0};
		double[] MIP_DELTA = {0.0, 200.0, 500.0, 200.0};
		double mip = MIP[this.getDescriptor().getLayer()];
		double mipDelta = MIP_DELTA[this.getDescriptor().getLayer()];

		return ((this.XPOS != 0 || this.YPOS != 0 || this.ZPOS != 0) &&
				(this.geometricMean() > mip - mipDelta) &&
				(this.geometricMean() < mip + mipDelta)
			    );
//		return (
//				(this.XPOS != 0 || this.YPOS != 0 || this.ZPOS != 0) &&
//				(ADCL > TW_ADC_MIN[layer]) &&
//				(ADCL < TW_ADC_MAX[layer]) &&
//				(ADCR > TW_ADC_MIN[layer]) &&
//				(ADCR < TW_ADC_MAX[layer])
//				);
		//				&& (this.paddleY() / this.halfTimeDiff() > 8.0)
		//				&& (this.paddleY() / this.halfTimeDiff() < 24.0);
	}

	public boolean isValidLogRatio() {
		// only if geometric mean is over a minimum
		// only if both TDCs are non-zero - otherwise ADCs are equal and log
		// ratio is always 0
		// return (this.geometricMean() > 500.0) && (TDCL != 0) && (TDCR != 0);
		return isValidGeoMean();
	}

	public boolean includeInTimeWalkTest() {
		// return true if x and y value is in certain range depending on paddle
		// number
		// and ADC is positive (only needed for testing where I'm manually
		// subtracting the pedestal
		// hard coded for s1p10
		// set cutx and findrange functions in c++
		int paddle = this.getDescriptor().getComponent() - 1;
		double x = this.XPOS;
		return !(paddle < 24 && x > 62.8 + 13.787 * paddle - 5 && x < 62.8 + 13.787 * paddle + 5)
				// && (this.ADCL- getPedestalL() > 0.1 && this.ADCR -
				// getPedestalR() > 0.1)
				&& (this.ADCL > 0.1 && this.ADCR > 0.1)
				&& (this.YPOS > -85 && this.YPOS < 85);

	}

	public double getPedestalL() {
		// only needed for test data
		// real data will have pedestal subtracted
		// uses values from Haiyun's caldb test files
		// hardcoded for S1 P10 as that's the data I have
		return 442.0;
	}

	public double getPedestalR() {
		// only needed for test data
		// real data will have pedestal subtracted
		// uses values from Haiyun's caldb test files
		// hardcoded for S1 P10 as that's the data I have
		return 410.0;
	}

	public double veff() {
		double veff = 16.0;
		if (tof == "FTOF") {
			if (TOFCalibrationEngine.calDBSource==TOFCalibrationEngine.CAL_FILE) {
				veff = TOFCalibrationEngine.veffValues.getItem(desc.getSector(),
						desc.getLayer(), desc.getComponent());
			}
		} else {
			veff = CTOFCalibrationEngine.veffValues.getItem(desc.getSector(),
					desc.getLayer(), desc.getComponent());
		}

		return veff;
	}

	// timeResidualsOrig
	// rename to timeResiduals to use the original time walk algorithm
	// also need to change number of iterations in TofTimeWalkEventListener to 5 (or however many iterations required)
	public double[] timeResidualsOrig(double[] lambda, double[] order, int iter) {
		double[] tr = { 0.0, 0.0 };

		double timeL = tdcToTime(TDCL);
		double timeR = tdcToTime(TDCR);
		// double timeL = getTWTimeL();
		// double timeR = getTWTimeR();

		//double corrFrac = 1.0;
		//if (iter<3) corrFrac = 0.5;
		//if (iter<7) corrFrac = 0.75;

		double timeLCorr = timeL - (lambda[LEFT] / Math.pow(ADCL, order[LEFT]));
		double timeRCorr = timeR
				- (lambda[RIGHT] / Math.pow(ADCR, order[RIGHT]));

		tr[LEFT] = ((timeL - timeRCorr - leftRightAdjustment(desc.getSector(),
				desc.getLayer(), desc.getComponent())) / 2)
				- (paddleY() / veff());
		tr[RIGHT] = -(((timeLCorr - timeR - leftRightAdjustment(
				desc.getSector(), desc.getLayer(), desc.getComponent())) / 2) - (paddleY() / veff()));

		return tr;
	}

	// timeResidualsVertex
	// rename to timeResiduals to use this version using the TDC time - RF start time - flight and path
	public double[] timeResiduals(double[] lambda, double[] order, int iter) {
		double[] tr = { 0.0, 0.0 };

		double lr = leftRightAdjustment(desc.getSector(), desc.getLayer(), desc.getComponent());
		double p2p = TOFCalibrationEngine.p2pValues.getItem(desc.getSector(), desc.getLayer(), desc.getComponent());
		double timeL = tdcToTime(TDCL) - (lr/2) - p2p 
				- ((0.5*paddleLength() + YPOS)/this.veff())
				- (PATH_LENGTH/29.98)
				- this.RF_TIME ; 
		double timeR = tdcToTime(TDCR) + (lr/2) - p2p
				- ((0.5*paddleLength() - YPOS)/this.veff())
				- (PATH_LENGTH/29.98)
				- this.RF_TIME;

		tr[LEFT] = timeL;
		tr[RIGHT] = timeR;

		return tr;
	}
	
	public double paddleLength() {
		int layer = this.getDescriptor().getLayer();
		int paddle = this.getDescriptor().getComponent();
		double len = 0.0;

		if (layer == 1 && paddle <= 5) {
			len = (15.85 * paddle) + 16.43;
		} else if (layer == 1 && paddle > 5) {
			len = (15.85 * paddle) + 11.45;
		} else if (layer == 2) {
			len = (6.4 * paddle) + 10.84;
		} else if (layer == 3) {
			len = (13.73 * paddle) + 357.55;
		}

		return len;

	}

	// timeResidualsCheat
	// rename to timeResiduals to use this version which uses the known correction for the opposite TDC
	// may as well also change number of iterations to 1 in TofTimeWalkEventListener
	public double[] timeResidualsCheat(double[] lambda, double[] order) {
		double[] tr = { 0.0, 0.0 };

		double timeL = tdcToTime(TDCL);
		double timeR = tdcToTime(TDCR);
		// double timeL = getTWTimeL();
		// double timeR = getTWTimeR();

		double timeLCorr = timeLeftAfterTW();
		double timeRCorr = timeRightAfterTW();

		tr[LEFT] = ((timeL - timeRCorr - leftRightAdjustment(desc.getSector(),
				desc.getLayer(), desc.getComponent())) / 2)
				- (paddleY() / veff());
		tr[RIGHT] = -(((timeLCorr - timeR - leftRightAdjustment(
				desc.getSector(), desc.getLayer(), desc.getComponent())) / 2) - (paddleY() / veff()));

		return tr;
	}

	public double[] timeResidualsTest(double[] lambda, double[] order) {
		double[] tr = { 0.0, 0.0 };

		double timeL = tdcToTime(TDCL);
		double timeR = tdcToTime(TDCR);

		double timeLCorr = timeL - (lambda[LEFT] / Math.pow(ADCL, order[LEFT]));
		double timeRCorr = timeR
				- (lambda[RIGHT] / Math.pow(ADCR, order[RIGHT]));

		tr[LEFT] = ((timeL - timeR) / 2) - (paddleY() / veff());
		tr[RIGHT] = ((timeLCorr - timeRCorr) / 2) - (paddleY() / veff());

		return tr;
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

	public double timeTWCorr(double time, double adc) {

		return time - (40.0 / Math.pow(adc, 0.5));

	}

	public boolean isValidLeftRight() {
		return (tdcToTime(TDCL) != tdcToTime(TDCR));
	}

	public double getTWTimeL() {
		// uses values from Haiyun's caldb test files
		// hardcoded for S1 P10 as that's the data I have
		double c1 = -0.0981149;
		double c0 = 433.845;
		return c0 + c1 * this.TDCL;
	}

	public double getTWTimeR() {
		// hardcoded for S1 P10 as that's the data I have
		double c1 = -0.0981185;
		double c0 = 437.063;
		return c0 + c1 * this.TDCR;
	}

	double clas6TdcToTime(double value) {
		double c1 = 0.0009811; // average value from CLAS
		double c0 = 0;
		return c0 + c1 * value;
	}

	double tdcToTime(double value) {
		double c1 = 0.024; // constant value for CLAS12
		double c0 = 0;
		return c0 + c1 * value;
	}

	double clas6HalfTimeDiff() {

		double timeL = getTWTimeL();
		double timeR = getTWTimeR();
		return (timeL - timeR) / 2;
	}

	public double halfTimeDiff() {

		double timeL = timeLeftAfterTW();
		double timeR = timeRightAfterTW();
		return (timeL - timeR - leftRightAdjustment(desc.getSector(),
				desc.getLayer(), desc.getComponent())) / 2;
	}

	public double halfTimeDiffWithTW() {

		double timeL = timeTWCorr(tdcToTime(TDCL), ADCL);
		double timeR = timeTWCorr(tdcToTime(TDCR), ADCR);
		return (timeL - timeR) / 2;
	}

	public double recHalfTimeDiff() {

		//		return (TIMEL - TIMER - leftRightAdjustment(desc.getSector(),
		//				desc.getLayer(), desc.getComponent())) / 2;
		return (TIMEL - TIMER) / 2;
	}

	public double leftRightAdjustment(int sector, int layer, int paddle) {

		double lr = 0.0;

		if (tof == "FTOF") {
			//if (TOFCalibrationEngine.calDBSource==TOFCalibrationEngine.CAL_DEFAULT) {
			//	lr = 0.0;
			//}
			//else {
			lr = TOFCalibrationEngine.leftRightValues.getItem(sector, layer,
					paddle);				
			//}

		} else {
			lr = CTOFCalibrationEngine.leftRightValues.getItem(sector, layer,
					paddle);
		}

		return lr;

	}

	public double lamL() {
		double lamL = 40.0;
		if (TOFCalibrationEngine.calDBSource == TOFCalibrationEngine.CAL_FILE) {
			lamL = 
					TOFCalibrationEngine.timeWalkValues.getItem(desc.getSector(),desc.getLayer(),desc.getComponent())[0];
		}
		return lamL;			
	}

	public double ordL() {
		double ordL = 0.5;
		if (TOFCalibrationEngine.calDBSource == TOFCalibrationEngine.CAL_FILE) {
			ordL = 
					TOFCalibrationEngine.timeWalkValues.getItem(desc.getSector(),desc.getLayer(),desc.getComponent())[1];
		}			
		return ordL;			
	}

	public double lamR() {
		double lamR = 40.0;
		if (TOFCalibrationEngine.calDBSource == TOFCalibrationEngine.CAL_FILE) {
			lamR = 
					TOFCalibrationEngine.timeWalkValues.getItem(desc.getSector(),desc.getLayer(),desc.getComponent())[2];
		}
		return lamR;			
	}

	public double ordR() {
		double ordR = 0.5;
		if (TOFCalibrationEngine.calDBSource == TOFCalibrationEngine.CAL_FILE) {
			ordR = 
					TOFCalibrationEngine.timeWalkValues.getItem(desc.getSector(),desc.getLayer(),desc.getComponent())[3];
		}
		return ordR;			
	}

	public double position() {

		return halfTimeDiff() * veff();
	}

	public double recPosition() {
		return recHalfTimeDiff() * veff();
	} 

	public double paddleY() {

		int sector = desc.getSector();
		double rotation = Math.toRadians((sector - 1) * 60);
		return YPOS * Math.cos(rotation) - XPOS * Math.sin(rotation);
	}

	public double zPosCTOF() {
		return ZPOS + 10.0;
	}

	public double refTime(double targetCentre) {
		return this.START_TIME - this.RF_TIME;
		//		return (this.TOF_TIME - this.FLIGHT_TIME
		//				- ((this.VERTEX_Z - targetCentre) / C) - this.RF_TIME);
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

}