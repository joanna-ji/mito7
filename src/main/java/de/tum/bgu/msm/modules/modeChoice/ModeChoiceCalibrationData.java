package de.tum.bgu.msm.modules.modeChoice;

import de.tum.bgu.msm.data.*;
import de.tum.bgu.msm.resources.Properties;
import de.tum.bgu.msm.resources.Resources;
import org.apache.log4j.Logger;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import static de.tum.bgu.msm.data.Purpose.*;

public class ModeChoiceCalibrationData {

    private Map<String, Map<Purpose, Map<Mode, Double>>> observedModalShare;
    private Map<String, Map<Purpose, Map<Mode, Integer>>> simulatedTripsByRegionPurposeAndMode;
    private Map<String, Map<Purpose, Map<Mode, Double>>> calibrationFactors;
    private Map<Integer, String> zoneToRegionMap;

    private final static EnumSet<Purpose> PURPOSES = EnumSet.of(HBW,HBE,HBS,HBR,HBO,NHBW,NHBO);
    private final static boolean RUN_MOPED = Resources.instance.getBoolean(Properties.RUN_MOPED, false);

    private PrintWriter pw = null;

    private static Logger logger = Logger.getLogger(ModeChoiceCalibrationData.class);

    public ModeChoiceCalibrationData() {
        this.observedModalShare = new HashMap<>();
        this.calibrationFactors = new HashMap<>();
        this.simulatedTripsByRegionPurposeAndMode = new HashMap<>();
        zoneToRegionMap = new HashMap<>();
    }


    public double[] getCalibrationFactorsAsArray(Purpose tripPurpose, Location tripOrigin) {

        double[] factors = new double[Mode.values().length];
        for (Mode mode : Mode.values()) {
            factors[mode.getId()] = calibrationFactors.get(zoneToRegionMap.get(tripOrigin.getZoneId())).get(tripPurpose).getOrDefault(mode, 0.);
        }
        return factors;
    }

    public Map<String, Map<Purpose, Map<Mode, Double>>> getObservedModalShare() {
        return observedModalShare;
    }

    public Map<String, Map<Purpose, Map<Mode, Double>>> getCalibrationFactors() {
        return calibrationFactors;
    }

    public Map<Integer, String> getZoneToRegionMap() {
        return zoneToRegionMap;
    }

    public void updateCalibrationCoefficients(DataSet dataSet, int iteration) {

        if (pw == null){
            try {
                pw = new PrintWriter("mode_choice_calibration.csv");
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            pw.println("iteration,region,purpose,mode,observed_share,sim_share,k,trips");
        }

        simulatedTripsByRegionPurposeAndMode.clear();

        for (MitoTrip trip : dataSet.getTrips().values()) {
            if (trip.getTripMode() != null) {
                String region = zoneToRegionMap.get(trip.getTripOrigin().getZoneId());
                Purpose purpose = trip.getTripPurpose();
                Mode mode = trip.getTripMode();

                simulatedTripsByRegionPurposeAndMode.putIfAbsent(region, new HashMap<>());
                simulatedTripsByRegionPurposeAndMode.get(region).putIfAbsent(purpose, new HashMap<>());
                simulatedTripsByRegionPurposeAndMode.get(region).get(purpose).putIfAbsent(mode, 0);
                int newValue = simulatedTripsByRegionPurposeAndMode.get(region).get(purpose).get(mode) + 1;
                simulatedTripsByRegionPurposeAndMode.get(region).get(purpose).put(mode, newValue);
            }
        }


        for (String region : observedModalShare.keySet()) {
            for (Purpose purpose : PURPOSES) {

                // Adjustments for MoPeD (since walk is not part of the mode share)
                double observedShareAdjustment = 1.;
                double simulatedTripCountAdjustment = 0.;
                if(RUN_MOPED && !Purpose.getMandatoryPurposes().contains(purpose)) {
                    observedShareAdjustment = 1. / (1. - observedModalShare.get(region).get(purpose).get(Mode.walk));
                    simulatedTripCountAdjustment = simulatedTripsByRegionPurposeAndMode.get(region).get(purpose).get(Mode.walk);
                }

                for (Mode mode : Mode.values()) {
                    double observedShare = observedModalShare.get(region).get(purpose).getOrDefault(mode, 0.) * observedShareAdjustment;
                    double tripAtRegionAndPurpose = simulatedTripsByRegionPurposeAndMode.get(region).get(purpose).values().stream().mapToInt(Integer::intValue).sum() - simulatedTripCountAdjustment;
                    double simulatedShare;
                    if (tripAtRegionAndPurpose != 0) {
                        simulatedShare = (simulatedTripsByRegionPurposeAndMode.get(region).get(purpose).getOrDefault(mode, 0) / tripAtRegionAndPurpose);
                    } else {
                        simulatedShare = 0.;
                    }

                    double newFactor = 0.;
                    if(observedShare > 0. && simulatedShare > 0.) {
                        double existingFactor = calibrationFactors.get(region).get(purpose).getOrDefault(mode, 0.);
                        newFactor = existingFactor + Math.log(Math.log(simulatedShare) / Math.log(observedShare));
                        calibrationFactors.get(region).get(purpose).put(mode, newFactor);
                    }

                    double tripCount = tripAtRegionAndPurpose * simulatedShare;
                    pw.println(iteration + "," + region + "," + purpose + "," + mode + "," +
                            observedShare + "," + simulatedShare + "," + newFactor + "," + tripCount);
                }
            }
        }


    }

    public void close() {
        pw.close();
        logger.info("Finished mode choice calibration");
    }


}
