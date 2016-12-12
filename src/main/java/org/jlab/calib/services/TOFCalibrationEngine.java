package org.jlab.calib.services;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import javax.swing.JFrame;
import javax.swing.JTabbedPane;

import org.jlab.detector.calib.tasks.CalibrationEngine;
import org.jlab.detector.calib.utils.CalibrationConstants;
import org.jlab.groot.data.H1F;
import org.jlab.groot.graphics.EmbeddedCanvas;
import org.jlab.groot.group.DataGroup;
//import org.jlab.calib.temp.DataGroup;
import org.jlab.groot.math.F1D;
import org.jlab.io.base.DataEvent;
import org.jlab.io.base.DataEventType;
import org.jlab.utils.groups.IndexedList;

public class TOFCalibrationEngine extends CalibrationEngine {

	public final static int[] NUM_PADDLES = { 23, 62, 5 };
	public final static int 	NUM_LAYERS = 3;
	public final static String[] LAYER_NAME = { "FTOF1A", "FTOF1B", "FTOF2" };
	public final static double UNDEFINED_OVERRIDE = Double.NEGATIVE_INFINITY;
	
	// plot settings
	public final static int		FUNC_COLOUR = 2;
	public final static int		MARKER_SIZE = 3;
	public final static int		FUNC_LINE_WIDTH = 2;
	public final static int		MARKER_LINE_WIDTH = 1;
	
	public IndexedList<Double[]> constants = new IndexedList<Double[]>(3);

	public CalibrationConstants calib;
	public IndexedList<DataGroup> dataGroups = new IndexedList<DataGroup>(3);

	public String stepName = "Unknown";
	public String fileNamePrefix = "Unknown";
	public String filename = "Unknown.txt";
	
	// Left right values from text file
	public static IndexedList<Double> leftRightValues = new IndexedList<Double>(3);

	public TOFCalibrationEngine() {
		// controlled by calibration step class
		
	}

	@Override
	public void dataEventAction(DataEvent event) {

		if (event.getType() == DataEventType.EVENT_START) {
			resetEventListener();
			processEvent(event);
		} else if (event.getType() == DataEventType.EVENT_ACCUMULATE) {
			processEvent(event);
		} else if (event.getType() == DataEventType.EVENT_STOP) {
			analyze();
		}
	}

	public void processPaddleList(List<TOFPaddle> paddleList) {
		// overridden in calibration step classes
	}

	@Override
	public void timerUpdate() {
		analyze();
	}

	public void processEvent(DataEvent event) {
		// overridden in calibration step classes

	}

	public void analyze() {
		for (int sector = 1; sector <= 6; sector++) {
			for (int layer = 1; layer <= 3; layer++) {
				int layer_index = layer - 1;
				for (int paddle = 1; paddle <= NUM_PADDLES[layer_index]; paddle++) {
					fit(sector, layer, paddle);
				}
			}
		}
		save();
		calib.fireTableDataChanged();
	}

	public void fit(int sector, int layer, int paddle) {
		// fit to default range
		fit(sector, layer, paddle, UNDEFINED_OVERRIDE, UNDEFINED_OVERRIDE);
	}

	public void fit(int sector, int layer, int paddle, double minRange,
			double maxRange) {
		// overridden in calibration step class
	}

	public void saveRow(int sector, int layer, int paddle) {
		// overridden in calibration step class
	}

	public void save() {

		for (int sector = 1; sector <= 6; sector++) {
			for (int layer = 1; layer <= 3; layer++) {
				int layer_index = layer - 1;
				for (int paddle = 1; paddle <= NUM_PADDLES[layer_index]; paddle++) {
					calib.addEntry(sector, layer, paddle);
					saveRow(sector, layer, paddle);
				}
			}
		}
		calib.save(filename);
	}

	public String nextFileName() {

		// Get the next file name
		Date today = new Date();
		DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
		String todayString = dateFormat.format(today);
		String filePrefix = fileNamePrefix + todayString;
		int newFileNum = 0;

		File dir = new File(".");
		File[] filesList = dir.listFiles();

		for (File file : filesList) {
			if (file.isFile()) {
				String fileName = file.getName();
				if (fileName.matches(filePrefix + "[.]\\d+[.]txt")) {
					String fileNumString = fileName.substring(
							fileName.indexOf('.') + 1,
							fileName.lastIndexOf('.'));
					int fileNum = Integer.parseInt(fileNumString);
					if (fileNum >= newFileNum)
						newFileNum = fileNum + 1;

				}
			}
		}

		return filePrefix + "." + newFileNum + ".txt";
	}

	public void customFit(int sector, int layer, int paddle) {
		// overridden in calibration step class
	}

	@Override
	public List<CalibrationConstants> getCalibrationConstants() {
		return Arrays.asList(calib);
	}

	@Override
	public IndexedList<DataGroup> getDataGroup() {
		return dataGroups;
	}

	public boolean isGoodPaddle(int sector, int layer, int paddle) {
		// Overridden in calibration step class
		return true;
	}

	public DataGroup getSummary(int sector, int layer) {
		// Overridden in calibration step class
		DataGroup dg = null;
		return dg;
	}

	public double paddleLength(int sector, int layer, int paddle) {
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

	public static double toDouble(String stringVal) {

		double doubleVal;
		try {
			doubleVal = Double.parseDouble(stringVal);
		} catch (NumberFormatException e) {
			doubleVal = UNDEFINED_OVERRIDE;
		}
		return doubleVal;
	}

	public void setPlotTitle(int sector, int layer, int paddle) {
		// Overridden in calibration step classes
	}
	
	public void drawPlots(int sector, int layer, int paddle,
			EmbeddedCanvas canvas) {
		// Overridden in calibration step classes
	}

	public void showPlots(int sector, int layer) {

		int layer_index = layer - 1;
		EmbeddedCanvas[] fitCanvases;
		fitCanvases = new EmbeddedCanvas[3];
		fitCanvases[0] = new EmbeddedCanvas();
		fitCanvases[0].divide(6, 4);

		int canvasNum = 0;
		int padNum = 0;

		for (int paddleNum = 1; paddleNum <= NUM_PADDLES[layer_index]; paddleNum++) {
    		
			fitCanvases[canvasNum].cd(padNum);
			fitCanvases[canvasNum].getPad(padNum).setTitle("Paddle "+paddleNum);
			fitCanvases[canvasNum].getPad(padNum).setOptStat(0);
			drawPlots(sector, layer, paddleNum, fitCanvases[canvasNum]);

			padNum = padNum + 1;

			if ((paddleNum) % 24 == 0) {
				// new canvas
				canvasNum = canvasNum + 1;
				padNum = 0;

				fitCanvases[canvasNum] = new EmbeddedCanvas();
				fitCanvases[canvasNum].divide(6, 4);

			}

		}

		JFrame frame = new JFrame(stepName + " " + LAYER_NAME[layer - 1]
				+ " Sector " + sector);
		frame.setSize(1000, 800);

		JTabbedPane pane = new JTabbedPane();
		for (int i = 0; i <= canvasNum; i++) {
			pane.add(
					"Paddles " + ((i * 24) + 1) + " to "
							+ Math.min(((i + 1) * 24), NUM_PADDLES[layer - 1]),
					fitCanvases[i]);
		}

		frame.add(pane);
		// frame.pack();
		frame.setVisible(true);
		frame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);

	}

}
