package de.tum.bgu.msm.modules.modeChoice;

import de.tum.bgu.msm.data.*;
import de.tum.bgu.msm.data.travelTimes.TravelTimes;
import de.tum.bgu.msm.io.input.readers.CoefficientReader;
import de.tum.bgu.msm.modules.Module;
import de.tum.bgu.msm.modules.modeChoice.calculators.AirportModeChoiceCalculator;
import de.tum.bgu.msm.modules.modeChoice.calculators.CalibratingModeChoiceCalculatorImpl;
import de.tum.bgu.msm.modules.modeChoice.calculators.ModeChoiceCalculatorImpl;
import de.tum.bgu.msm.modules.modeChoice.calculators.av.AVModeChoiceCalculatorImpl;
import de.tum.bgu.msm.resources.Resources;
import de.tum.bgu.msm.util.MitoUtil;
import de.tum.bgu.msm.util.concurrent.ConcurrentExecutor;
import de.tum.bgu.msm.util.concurrent.RandomizableConcurrentFunction;
import org.apache.log4j.Logger;

import java.nio.file.Path;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static de.tum.bgu.msm.data.Mode.*;
import static de.tum.bgu.msm.data.Purpose.*;

public class ModeChoice extends Module {

    private final static EnumSet<Purpose> PURPOSES = EnumSet.of(HBW,HBE,HBS,HBR,HBO,RRT,NHBW,NHBO);
    private final static EnumSet<Mode> MODES = EnumSet.of(autoDriver,autoPassenger,publicTransport,bicycle,walk);

    private final static Logger logger = Logger.getLogger(ModeChoice.class);
    private final static Path modeChoiceCoefFolder = Resources.instance.getModeChoiceCoefficients();
    private final Map<Purpose, Map<Mode, Map<String, Double>>> coefficients = new EnumMap<>(Purpose.class);

    public ModeChoice(DataSet dataSet) {
        super(dataSet);
        for (Purpose purpose : PURPOSES) {
            Map<Mode, Map<String, Double>> coefficientsByMode = new EnumMap<>(Mode.class);
            for (Mode mode : MODES) {
                coefficientsByMode.put(mode, new CoefficientReader(dataSet, mode,
                        modeChoiceCoefFolder.resolve(purpose.toString() + ".csv")).readCoefficients());
            }
            coefficients.put(purpose, coefficientsByMode);
        }
    }

    @Override
    public void run() {
        logger.info(" Calculating mode choice probabilities for each trip. Modes considered - 1. Auto driver, 2. Auto passenger, 3. Bicycle, 4. Bus, 5. Train, 6. Tram or Metro, 7. Walk ");
        modeChoiceByPurpose();
        printModeShares();
    }

    private void modeChoiceByPurpose() {
        ConcurrentExecutor<Void> executor = ConcurrentExecutor.fixedPoolService(Purpose.values().length);
        for (Purpose purpose : PURPOSES) {
            executor.addTaskToQueue(new ModeChoiceByPurpose(purpose, dataSet, coefficients.get(purpose)));
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
        private final Map<Mode, Map<String, Double>> coefficients;
        private final TravelTimes travelTimes;
        private int countTripsSkipped;

        ModeChoiceByPurpose(Purpose purpose, DataSet dataSet, Map<Mode, Map<String, Double>> coefficients) {
            super(MitoUtil.getRandomObject().nextLong());
            this.purpose = purpose;
            this.dataSet = dataSet;
            this.coefficients = coefficients;
            this.travelTimes = dataSet.getTravelTimes();
        }

        @Override
        public Void call() {
            countTripsSkipped = 0;
            try {
                for (MitoPerson person : dataSet.getPersons().values()) {
                    for (MitoTrip trip : person.getTripsForPurpose(purpose)) {
                        chooseMode(trip, calculateProbabilities(calculateUtilities(person, trip)));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            logger.info(countTripsSkipped + " trips skipped for " + purpose);
            return null;
        }

        private EnumMap<Mode, Double> calculateUtilities(MitoPerson person, MitoTrip trip) {
            EnumMap<Mode, Double> utilities = new EnumMap(Mode.class);

            for(Mode mode : MODES) {
                utilities.put(mode, getResponse(person, trip, coefficients.get(mode)));
            }

            return (utilities);
        }

        private double getResponse(MitoPerson person, MitoTrip trip, Map<String, Double> coefficients) {
            return -1.;
        }

        private EnumMap<Mode, Double> calculateProbabilities(EnumMap<Mode, Double> utilities) {
            EnumMap<Mode, Double> probabilities = new EnumMap(Mode.class);

            return probabilities;
        }

        private void chooseMode(MitoTrip trip, EnumMap<Mode, Double> probabilities) {
            if (probabilities == null) {
                countTripsSkipped++;
                return;
            }

            //found Nan when there is no transit!!
            probabilities.replaceAll((mode, probability) ->
                    probability.isNaN() ? 0: probability);

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
