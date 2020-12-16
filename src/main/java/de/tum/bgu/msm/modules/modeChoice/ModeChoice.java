package de.tum.bgu.msm.modules.modeChoice;

import de.tum.bgu.msm.data.*;
import de.tum.bgu.msm.io.input.readers.CoefficientReader;
import de.tum.bgu.msm.modules.Module;
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

    private final static Logger logger = Logger.getLogger(ModeChoice.class);
    private final static EnumSet<Purpose> PURPOSES = EnumSet.of(HBW,HBE,HBS,HBR,HBO,RRT,NHBW,NHBO);
    private final static EnumSet<Mode> MODES = EnumSet.of(autoDriver,autoPassenger,publicTransport,bicycle,walk);
    private final static Path modeChoiceCoefFolder = Resources.instance.getModeChoiceCoefficients();

    private final EnumMap<Purpose, EnumMap<Mode, Map<String, Double>>> allCoefficients = new EnumMap<>(Purpose.class);

    public ModeChoice(DataSet dataSet) {
        super(dataSet);
        for (Purpose purpose : PURPOSES) {
            EnumMap<Mode, Map<String, Double>> coefficientsByMode = new EnumMap<>(Mode.class);
            for (Mode mode : MODES) {
                if(!(purpose.equals(RRT) & EnumSet.of(autoDriver,autoPassenger,publicTransport).contains(mode))) {
                    coefficientsByMode.put(mode, new CoefficientReader(dataSet, mode,
                            modeChoiceCoefFolder.resolve(purpose.toString() + ".csv")).readCoefficients());
                }
            }
            allCoefficients.put(purpose, coefficientsByMode);
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
            executor.addTaskToQueue(new ModeChoiceByPurpose(purpose, dataSet, allCoefficients.get(purpose)));
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
        private final LogitCalculator logitCalculator;
        private int countTripsSkipped;

        ModeChoiceByPurpose(Purpose purpose, DataSet dataSet, EnumMap<Mode, Map<String, Double>> coefficients) {
            super(MitoUtil.getRandomObject().nextLong());
            this.purpose = purpose;
            this.dataSet = dataSet;
            this.logitCalculator = new LogitCalculator(dataSet, coefficients);
        }

        @Override
        public Void call() {
            logger.info("Mode choice for purpose " + purpose);
            countTripsSkipped = 0;
            try {
                for (MitoPerson person : dataSet.getModelledPersons().values()) {
                    for (MitoTrip trip : person.getTripsForPurpose(purpose)) {
                        chooseMode(trip, logitCalculator.getProbabilities(person, trip, determineAvailability(person)));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            logger.info(countTripsSkipped + " trips skipped for " + purpose);
            return null;
        }

        private EnumSet<Mode> determineAvailability(MitoPerson person) {
            EnumSet<Mode> availability = person.getModeRestriction().getRestrictedModeSet();

            if(purpose.equals(RRT)) {
                availability.removeAll(EnumSet.of(autoDriver, autoPassenger, publicTransport));
            }

            return availability;
        }

        private void chooseMode(MitoTrip trip, EnumMap<Mode, Double> probabilities) {
            if (probabilities == null) {
                countTripsSkipped++;
                return;
            }

            double sum = MitoUtil.getSum(probabilities.values());
            if (Math.abs(sum - 1) > 0.01) {
                logger.warn("Mode probabilities don't add to 1 for trip " + trip.getId());
            }
            if (sum > 0) {
                final Mode select = MitoUtil.select(probabilities, random);
                trip.setTripMode(select);
            } else {
                logger.error("Zero/negative probabilities for trip " + trip.getId());
                trip.setTripMode(null);
            }
        }
    }
}
