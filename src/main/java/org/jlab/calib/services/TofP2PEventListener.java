package org.jlab.calib.services;


import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JOptionPane;

import org.jlab.detector.calib.utils.CalibrationConstants;
import org.jlab.groot.data.GraphErrors;
import org.jlab.groot.data.H1F;
import org.jlab.groot.data.H2F;
import org.jlab.groot.fitter.DataFitter;
import org.jlab.groot.graphics.EmbeddedCanvas;
import org.jlab.groot.group.DataGroup;
//import org.jlab.calib.temp.DataGroup;
import org.jlab.groot.math.F1D;
import org.jlab.groot.ui.TCanvas;
import org.jlab.io.base.DataEvent;
import org.jlab.utils.groups.IndexedList;

public class TofP2PEventListener extends TOFCalibrationEngine {


    // Test hists from Raffaella''s macro
    // PID
    public static H1F hi_vertex_dt = new H1F("hi_vertex_dt", "hi_vertex_dt", 200, -3.0, 3.0); 
    public static H1F hi_elec_t0 = new H1F("hi_elec_t0", "hi_elec_t0", 200, -3.0, 3.0); 
    public static H1F hi_pion_t0 = new H1F("hi_pion_t0", "hi_pion_t0", 200, -3.0, 3.0); 

    public static H2F hPaddles = new H2F("hPaddles", "hPaddles", 600, 0.0, 600.0, 600, 0.0, 600.0);
    public static H1F eSect1 = new H1F("eSect1","eSect1",6,0.5,6.5);
    public static H1F eSect = new H1F("eSect","eSect",6, 0.5,6.5);
    public static H1F pSect = new H1F("pSect","pSect",6, 0.5,6.5);


    private static final int ELECTRON = 11;
    private static final int PION = 211;

    // indices for constants
    public final int OFFSET_OVERRIDE = 0;

    // Reference paddle
    public final int REF_SECTOR = 1;
    public final int REF_LAYER = 1;
    public final int REF_PADDLE = 11; // should be 10 but low stats in paddle 10 in the calib challenge data

    final double MAX_OFFSET = 0.1;

    public TofP2PEventListener() {

        stepName = "P2P";
        fileNamePrefix = "FTOF_CALIB_P2P_";
        // get file name here so that each timer update overwrites it
        filename = nextFileName();

        calib = 
                new CalibrationConstants(3,
                        "paddle2paddle/F");

        calib.setName("/calibration/ftof/timing_offset/P2P");
        calib.setPrecision(3);

        // assign constraints
        calib.addConstraint(3, -MAX_OFFSET, MAX_OFFSET);

        // read in the time walk values from the text file
        String inputFile = "/home/louise/workspace/clascalib-services/ftof.timing_offset.smeared.txt";

        String line = null;
        try { 

            // Open the file
            FileReader fileReader = 
                    new FileReader(inputFile);

            // Always wrap FileReader in BufferedReader
            BufferedReader bufferedReader = 
                    new BufferedReader(fileReader);            

            line = bufferedReader.readLine();

            while (line != null) {

                String[] lineValues;
                lineValues = line.split(" ");

                int sector = Integer.parseInt(lineValues[0]);
                int layer = Integer.parseInt(lineValues[1]);
                int paddle = Integer.parseInt(lineValues[2]);
                double p2p = Double.parseDouble(lineValues[4]);

                p2pValues.add(p2p, sector, layer, paddle);

                line = bufferedReader.readLine();
            }    

            bufferedReader.close();            
        }
        catch(FileNotFoundException ex) {
            ex.printStackTrace();
            System.out.println(
                    "Unable to open file '" + 
                            inputFile + "'");                
        }
        catch(IOException ex) {
            System.out.println(
                    "Error reading file '" 
                            + inputFile + "'");                   
            // Or we could just do this: 
            // ex.printStackTrace();
        }

    }

    @Override
    public void resetEventListener() {

        // create the histograms for the first iteration
        createHists();
    }

    public void createHists() {

        // Raffaellas test hists
        hi_vertex_dt.setOptStat(1111);       
        hi_vertex_dt.setTitleX("#DeltaT (ns)"); 
        hi_vertex_dt.setTitleY("Counts");
        hi_elec_t0.setOptStat(1111);       hi_elec_t0.setTitleX("#DeltaT (ns)"); hi_elec_t0.setTitleY("Counts");
        hi_pion_t0.setOptStat(1111);       hi_pion_t0.setTitleX("#DeltaT (ns)"); hi_pion_t0.setTitleY("Counts");

        // LC perform init processing
        for (int sector = 1; sector <= 6; sector++) {
            for (int layer = 1; layer <= 3; layer++) {
                int layer_index = layer - 1;
                for (int paddle = 1; paddle <= NUM_PADDLES[layer_index]; paddle++) {
                  
                    DataGroup dg = new DataGroup(1,1);

                    // create all the histograms
                    H1F refHist = 
                            new H1F("refHist","Offset to reference paddle Sector "+sector+" Paddle "+paddle, 
                                    99,-49.5*BEAM_BUCKET,49.5*BEAM_BUCKET);
                    refHist.setTitleX("Offset (ns)");
                    dg.addDataSet(refHist, 0);

                    dataGroups.add(dg,sector,layer,paddle);    

                    // initialize the constants array
                    Double[] consts = {0.0, UNDEFINED_OVERRIDE};
                    // override values
                    constants.add(consts, sector, layer, paddle);
                }
            }
        }
    }

    @Override
    public void processEvent(DataEvent event) {

        List<TOFPaddle> paddleList = DataProvider.getPaddleList(event);
        processPaddleList(paddleList);

    }

    @Override
    public void processPaddleList(List<TOFPaddle> paddleList) {

        TOFPaddle eRefPaddle = new TOFPaddle(0,0,0);
        boolean electronFound = false;
        
        // Look for electron in reference paddle
        for (TOFPaddle pad : paddleList) {
//            System.out.println("SLC "+pad.getDescriptor().getSector()+pad.getDescriptor().getLayer()+pad.getDescriptor().getComponent()+
//                                        " particle_id "+pad.PARTICLE_ID);
            //if (pad.getDescriptor().getSector()==REF_SECTOR &&
        	//    pad.getDescriptor().getLayer()==REF_LAYER && 
        	//    pad.getDescriptor().getComponent()==REF_PADDLE && 
        	//    pad.PARTICLE_ID == 11) {
        	if (pad.getDescriptor().getSector()==REF_SECTOR && 
        		pad.getDescriptor().getLayer()==REF_LAYER && 
        		pad.getDescriptor().getComponent()==REF_PADDLE) {
        		
                eRefPaddle = pad;
                electronFound = true;
                //break;
                for (TOFPaddle jpad : paddleList) {
                	
                	dataGroups.getItem(jpad.getDescriptor().getSector(),
         				   jpad.getDescriptor().getLayer(),
         				   jpad.getDescriptor().getComponent()).getH1F("refHist").fill(pad.startTime() - jpad.startTime());
                }
            }
        }
        
        // fill the histograms for the pions if an electron in the reference paddle is contained in the event
//        if (electronFound) {
//            for (TOFPaddle pad : paddleList) {
//
//                if (pad.PARTICLE_ID == 211) {
//                    dataGroups.getItem(pad.getDescriptor().getSector(),
//                    				   pad.getDescriptor().getLayer(),
//                    				   pad.getDescriptor().getComponent()).getH1F("refHist").fill(eRefPaddle.startTime() - pad.startTime());                                    
//                }
//            }
//        }
    }    

    // non-standard analyze step for P2P
    // require to iterate through all events several times
    @Override
    public void analyze() {

        //processTextFile();
        //fitAll();

        // Raffaella''s test hists
//        TCanvas vt = new TCanvas("vt", 700, 1000);
//        vt.divide(1,3);
//        vt.getCanvas().setGridX(false); vt.getCanvas().setGridY(false);
//        vt.getCanvas().setAxisFontSize(18);
//        vt.getCanvas().setAxisTitleSize(24);
//        vt.cd(0);
//        F1D f1 = new F1D("f1","[amp]*gaus(x,[mean],[sigma])", -1.0, 1.0);
//        f1.setParameter(0, 100.0);
//        f1.setParameter(1, 0.0);
//        f1.setParameter(2, 0.3);
//        f1.setLineWidth(2);
//        f1.setLineColor(2);
//        f1.setOptStat("1111");
//        DataFitter.fit(f1, hi_vertex_dt, "Q"); //No options uses error for sigma
//        vt.draw(hi_vertex_dt);
//        vt.cd(1);
//        vt.draw(hi_elec_t0);
//        vt.cd(2);
//        vt.draw(hi_pion_t0);
//
//        TCanvas paddles = new TCanvas("paddles", 800, 800);
//        paddles.cd(0);
//        paddles.draw(hPaddles);
//
//        TCanvas sects = new TCanvas("sects", 800, 800);
//        sects.divide(1, 3);
//        sects.cd(0);
//        sects.draw(eSect);
//        sects.cd(1);
//        sects.draw(pSect);
//        sects.cd(2);
//        sects.draw(eSect1);


        save();
        calib.fireTableDataChanged();
    }

    private Double formatDouble(double val) {
        return Double.parseDouble(new DecimalFormat("0.000").format(val));
    }

    @Override
    public void customFit(int sector, int layer, int paddle){

        String[] fields = { "Override offset:"};
        TOFCustomFitPanel panel = new TOFCustomFitPanel(fields);

        int result = JOptionPane.showConfirmDialog(null, panel, 
                "Adjust Fit / Override for paddle "+paddle, JOptionPane.OK_CANCEL_OPTION);
        if (result == JOptionPane.OK_OPTION) {

            double override = toDouble(panel.textFields[0].getText());

            // save the override values
            Double[] consts = constants.getItem(sector, layer, paddle);
            consts[OFFSET_OVERRIDE] = override;

            // update the table
            saveRow(sector,layer,paddle);
            calib.fireTableDataChanged();

        }     
    }

    public Double getOffset(int sector, int layer, int paddle) {

        double offset = 0.0;
        double overrideVal = constants.getItem(sector, layer, paddle)[OFFSET_OVERRIDE];

        if (overrideVal != UNDEFINED_OVERRIDE) {
            offset = overrideVal;
        }
        else {
            //offset = constants.getItem(sector, layer, paddle)[REF_OFFSET];
        	H1F refHist = dataGroups.getItem(sector,layer,paddle).getH1F("refHist");
        	int maxBin = refHist.getMaximumBin();
        	offset = refHist.getXaxis().getBinCenter(maxBin);
        	
        }
        return offset;
    }    

    @Override
    public void saveRow(int sector, int layer, int paddle) {

        calib.setDoubleValue(getOffset(sector,layer,paddle),
                "paddle2paddle", sector, layer, paddle);

    }

    @Override
    public void drawPlots(int sector, int layer, int paddle, EmbeddedCanvas canvas) {

        // TO DO

    }

    @Override
    public boolean isGoodPaddle(int sector, int layer, int paddle) {

        return (getOffset(sector,layer,paddle) >= -MAX_OFFSET
                &&
                getOffset(sector,layer,paddle) <= MAX_OFFSET);

    }

    @Override
    public DataGroup getSummary(int sector, int layer) {

        int layer_index = layer-1;
        double[] paddleNumbers = new double[NUM_PADDLES[layer_index]];
        double[] paddleUncs = new double[NUM_PADDLES[layer_index]];
        double[] values = new double[NUM_PADDLES[layer_index]];
        double[] valueUncs = new double[NUM_PADDLES[layer_index]];

        for (int p = 1; p <= NUM_PADDLES[layer_index]; p++) {

            paddleNumbers[p - 1] = (double) p;
            paddleUncs[p - 1] = 0.0;
            values[p - 1] = getOffset(sector, layer, p);
            valueUncs[p - 1] = 0.0;
        }

        GraphErrors summ = new GraphErrors("summ", paddleNumbers,
                values, paddleUncs, valueUncs);

        //        summary.setTitle("Left Right centroids: "
        //                + LAYER_NAME[layer - 1] + " Sector "
        //                + sector);
        //        summary.setTitleX("Paddle Number");
        //        summary.setYTitle("Centroid (cm)");
        //        summary.setMarkerSize(5);
        //        summary.setMarkerStyle(2);

        DataGroup dg = new DataGroup(1,1);
        dg.addDataSet(summ, 0);
        return dg;

    }


}