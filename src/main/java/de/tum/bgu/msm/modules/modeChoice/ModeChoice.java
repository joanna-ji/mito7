package de.tum.bgu.msm.modules.modeChoice;

import de.tum.bgu.msm.data.*;
import de.tum.bgu.msm.data.travelTimes.TravelTimes;
import de.tum.bgu.msm.modules.Module;
import de.tum.bgu.msm.modules.modeChoice.calculators.CalibratingModeChoiceCalculatorImpl;
import de.tum.bgu.msm.modules.modeChoice.calculators.ModeChoiceCalculator2017NewImpl;
import de.tum.bgu.msm.modules.modeChoice.calculators.ModeChoiceCalculatorRrtImpl;
import de.tum.bgu.msm.resources.Properties;
import de.tum.bgu.msm.resources.Resources;
import de.tum.bgu.msm.util.MitoUtil;
import de.tum.bgu.msm.util.concurrent.ConcurrentExecutor;
import de.tum.bgu.msm.util.concurrent.RandomizableConcurrentFunction;
import org.apache.log4j.Logger;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static de.tum.bgu.msm.data.Purpose.*;

public class ModeChoice extends Module {

    private final static Logger logger = Logger.getLogger(ModeChoice.class);
    private final static EnumSet<Purpose> PURPOSES = EnumSet.of(HBW,HBE,HBS,HBR,HBO,RRT,NHBW,NHBO);
    private final static boolean RUN_MOPED = Resources.instance.getBoolean(Properties.RUN_MOPED, false);

    private final Map<Purpose, ModeChoiceCalculator> modeChoiceCalculatorByPurpose = new EnumMap<>(Purpose.class);

    public ModeChoice(DataSet dataSet) {
        super(dataSet);

        modeChoiceCalculatorByPurpose.put(Purpose.HBW, new CalibratingModeChoiceCalculatorImpl(new ModeChoiceCalculator2017NewImpl(Purpose.HBW, dataSet),dataSet.getModeChoiceCalibrationData()));
        modeChoiceCalculatorByPurpose.put(Purpose.HBE, new CalibratingModeChoiceCalculatorImpl(new ModeChoiceCalculator2017NewImpl(Purpose.HBE, dataSet),dataSet.getModeChoiceCalibrationData()));
        modeChoiceCalculatorByPurpose.put(Purpose.HBS, new CalibratingModeChoiceCalculatorImpl(new ModeChoiceCalculator2017NewImpl(Purpose.HBS, dataSet),dataSet.getModeChoiceCalibrationData()));
        modeChoiceCalculatorByPurpose.put(Purpose.HBR, new CalibratingModeChoiceCalculatorImpl(new ModeChoiceCalculator2017NewImpl(Purpose.HBR, dataSet),dataSet.getModeChoiceCalibrationData()));
        modeChoiceCalculatorByPurpose.put(Purpose.HBO, new CalibratingModeChoiceCalculatorImpl(new ModeChoiceCalculator2017NewImpl(Purpose.HBO, dataSet),dataSet.getModeChoiceCalibrationData()));
        modeChoiceCalculatorByPurpose.put(Purpose.RRT, new ModeChoiceCalculatorRrtImpl(dataSet));
        modeChoiceCalculatorByPurpose.put(Purpose.NHBW, new CalibratingModeChoiceCalculatorImpl(new ModeChoiceCalculator2017NewImpl(Purpose.NHBW, dataSet),dataSet.getModeChoiceCalibrationData()));
        modeChoiceCalculatorByPurpose.put(Purpose.NHBO, new CalibratingModeChoiceCalculatorImpl(new ModeChoiceCalculator2017NewImpl(Purpose.NHBO, dataSet),dataSet.getModeChoiceCalibrationData()));
    }

    public void registerModeChoiceCalculator(Purpose purpose, ModeChoiceCalculator modeChoiceCalculator) {
        final ModeChoiceCalculator prev = modeChoiceCalculatorByPurpose.put(purpose, modeChoiceCalculator);
        if (prev != null) {
            logger.info("Overwrote mode choice calculator for purpose " + purpose + " with " + modeChoiceCalculator.getClass());
        }
    }

    @Override
    public void run() {
        if (modeChoiceCalculatorByPurpose.isEmpty()){
            throw new RuntimeException("It is mandatory to define mode choice calculators. Look at TravelDemandGeneratorXXX.java");
        }
        logger.info(" Calculating mode choice probabilities for each trip");
        modeChoiceByPurpose();
        printModeShares();
    }

    private void modeChoiceByPurpose() {
        ConcurrentExecutor<Void> executor = ConcurrentExecutor.fixedPoolService(Purpose.values().length);
        for (Purpose purpose : PURPOSES) {
            executor.addTaskToQueue(new ModeChoiceByPurpose(purpose, dataSet, modeChoiceCalculatorByPurpose.get(purpose)));
        }
        executor.execute();
    }

    private void printModeShares() {

        //filter valid trips by purpose
        Map<Purpose, List<MitoTrip>> tripsByPurpose = dataSet.getTrips().values().stream()
                .filter(trip -> trip.getTripMode() != null)
                .collect(Collectors.groupingBy(MitoTrip::getTripPurpose));


        tripsByPurpose.forEach((purpose, trips) -> {
            final long totalTrips = trips.size();
            trips.parallelStream()
                    //group number of trips by mode
                    .collect(Collectors.groupingBy(MitoTrip::getTripMode, Collectors.counting()))
                    //calculate and add share to data set table
                    .forEach((mode, count) ->
                            dataSet.addModeShareForPurpose(purpose, mode, (double) count / totalTrips));
        });

        for (Purpose purpose : Purpose.values()) {
            logger.info("#################################################");
            logger.info("Mode shares for purpose " + purpose + ":");
            for (Mode mode : Mode.values()) {
                Double share = dataSet.getModeShareForPurpose(purpose, mode);
                if (share != null) {
                    logger.info(mode + " = " + share * 100 + "%");
                }
            }
        }
    }

    static class ModeChoiceByPurpose extends RandomizableConcurrentFunction<Void> {

        private final Purpose purpose;
        private final DataSet dataSet;
        private final TravelTimes travelTimes;
        private final ModeChoiceCalculator modeChoiceCalculator;
        private int countTripsSkipped;
        private int countMopedWalkTripsSkipped = 0;
        private int countConflictTrips = 0;

        ModeChoiceByPurpose(Purpose purpose, DataSet dataSet, ModeChoiceCalculator modeChoiceCalculator) {
            super(MitoUtil.getRandomObject().nextLong());
            this.purpose = purpose;
            this.dataSet = dataSet;
            this.travelTimes = dataSet.getTravelTimes();
            this.modeChoiceCalculator = modeChoiceCalculator;
        }

        @Override
        public Void call() {
            countTripsSkipped = 0;
            try {
                for (MitoHousehold household : dataSet.getModelledHouseholds().values()) {
                    for (MitoTrip trip : household.getTripsForPurpose(purpose)) {
                        if(RUN_MOPED && Purpose.getMopedPurposes().contains(purpose) && Mode.walk.equals(trip.getTripMode())) {
                            countMopedWalkTripsSkipped++;
                        } else {
                            chooseMode(trip, calculateTripProbabilities(household, trip));
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            logger.info(countTripsSkipped + " trips skipped for " + purpose + " because of null origin or destination");
            if(RUN_MOPED) {
                logger.info(countMopedWalkTripsSkipped + " moped walk trips skipped for " + purpose);
                logger.info(countConflictTrips + " conflict trips for " + purpose + ", mode restricted to walk but moped didn't choose walk as mode!");
            }

            return null;
        }

        private EnumMap<Mode, Double> calculateTripProbabilities(MitoHousehold household, MitoTrip trip) {
            if (trip.getTripOrigin() == null || trip.getTripDestination() == null) {
                countTripsSkipped++;
                return null;
            }
            if(RUN_MOPED && trip.getPerson().getModeRestriction().equals(ModeRestriction.Walk)) {
                countConflictTrips++;
                return null;
            }

            final int originId = trip.getTripOrigin().getZoneId();

            final int destinationId = trip.getTripDestination().getZoneId();
            final MitoZone origin = dataSet.getZones().get(originId);
            final MitoZone destination = dataSet.getZones().get(destinationId);
            final double travelDistanceAuto = dataSet.getTravelDistancesAuto().getTravelDistance(originId,
                    destinationId);
            final double travelDistanceNMT = dataSet.getTravelDistancesNMT().getTravelDistance(originId,
                    destinationId);
            return modeChoiceCalculator.calculateProbabilities(purpose, household, trip.getPerson(), origin, destination, travelTimes, travelDistanceAuto,
                    travelDistanceNMT, dataSet.getPeakHour());
        }

        private void chooseMode(MitoTrip trip, EnumMap<Mode, Double> probabilities) {
            if (probabilities == null) {
                return;
            }

            if(RUN_MOPED && Purpose.getMopedPurposes().contains(purpose)) {
                probabilities.remove(Mode.walk);
            }

            //found Nan when there is no transit!!
            probabilities.replaceAll((mode, probability) ->
                    probability.isNaN() ? 0 : probability);

            double sum = MitoUtil.getSum(probabilities.values());
            if (sum > 0) {
                final Mode select = MitoUtil.select(probabilities, random);
                trip.setTripMode(select);
            } else {
                logger.error("Negative probabilities for trip " + trip.getId());
                trip.setTripMode(null);
            }
        }
    }
}
