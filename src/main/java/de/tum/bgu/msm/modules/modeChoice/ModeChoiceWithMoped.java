package de.tum.bgu.msm.modules.modeChoice;

import de.tum.bgu.msm.data.*;
import de.tum.bgu.msm.data.travelTimes.TravelTimes;
import de.tum.bgu.msm.io.input.readers.CoefficientReader;
import de.tum.bgu.msm.modules.Module;
import de.tum.bgu.msm.resources.Resources;
import de.tum.bgu.msm.util.LogitTools;
import de.tum.bgu.msm.util.MitoUtil;
import de.tum.bgu.msm.util.concurrent.ConcurrentExecutor;
import de.tum.bgu.msm.util.concurrent.RandomizableConcurrentFunction;
import org.apache.log4j.Logger;
import org.matsim.core.utils.collections.Tuple;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import static de.tum.bgu.msm.data.Mode.*;
import static de.tum.bgu.msm.data.Purpose.*;

public class ModeChoiceWithMoped extends Module {

    private static final Logger logger = Logger.getLogger(ModeChoiceWithMoped.class);
    private static final EnumSet<Purpose> PURPOSES = EnumSet.of(HBW,HBE,HBS,HBR,HBO,RRT,NHBW,NHBO);
    private static final EnumSet<Mode> MODES = EnumSet.of(autoDriver,autoPassenger,train,tramOrMetro,bus,taxi,bicycle,walk);
    private static final Path modeChoiceCoefFolder = Resources.instance.getModeChoiceCoefficients();
    private static final LogitTools<Mode> logitTools = new LogitTools<>(Mode.class);

    private static final double SPEED_WALK_KMH = 4;
    private static final double SPEED_BICYCLE_KMH = 10;

    public ModeChoiceWithMoped(DataSet dataSet) {
        super(dataSet);
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
            executor.addTaskToQueue(new ModeChoiceByPurpose(purpose, dataSet));
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
        private final double peakHour_s;
        private final EnumMap<Mode, Map<String, Double>> coefficients = new EnumMap<>(Mode.class);
        private final Map<Mode, Double> calibrationConstants;
        private int countTripsSkipped;
        private int countMopedWalkTripsSkipped;
        private int countConflictTrips;

        ModeChoiceByPurpose(Purpose purpose, DataSet dataSet) {
            super(MitoUtil.getRandomObject().nextLong());
            this.purpose = purpose;
            this.dataSet = dataSet;
            this.travelTimes = dataSet.getTravelTimes();
            this.peakHour_s = dataSet.getPeakHour();
            for(Mode mode : MODES) {
                if(!(purpose.equals(RRT) & EnumSet.of(autoDriver,autoPassenger,train,tramOrMetro,bus,taxi).contains(mode))) {
                    coefficients.put(mode, new CoefficientReader(dataSet, mode,
                            modeChoiceCoefFolder.resolve("mc_coefficients_" + purpose.toString().toLowerCase() + ".csv")).readCoefficients());
                }
            }
            this.calibrationConstants = dataSet.getModeChoiceCalibrationData().getCalibrationFactors().get("mma").get(purpose);
        }

        @Override
        public Void call() {
            logger.info("Mode choice for purpose " + purpose);
            countTripsSkipped = 0;
            try {
                List<Tuple<EnumSet<Mode>, Double>> nests = purpose.equals(RRT) ? null : getNests();
                for (MitoPerson person : dataSet.getModelledPersons().values()) {
                    List<MitoTrip> trips = person.getTripsForPurpose(purpose);
                    if(trips.size() > 0) {
                        EnumMap<Mode, Double> personUtilities = getPersonUtilities(person);
                        for (MitoTrip trip : trips) {
                            if (!Mode.walk.equals(trip.getTripMode())) {
                                if(trip.getTripOrigin()==null||trip.getTripDestination()==null){
                                    //logger.warn(trip.getId() + "|" + trip.getTripPurpose() + ", no origin or destination");
                                    continue;
                                }
                                chooseMode(trip, logitTools.getProbabilities(getTripUtilities(personUtilities, trip), nests));
                            }else{
                                countMopedWalkTripsSkipped++;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            logger.info(countTripsSkipped + " trips skipped for " + purpose);
            logger.info(countMopedWalkTripsSkipped + " moped walk trips skipped for " + purpose);
            logger.info(countConflictTrips + " conflict trips for " + purpose + ", mode restricted to walk but moped didn't choose walk as mode!");
            return null;
        }

        private List<Tuple<EnumSet<Mode>, Double>> getNests() {
            List<Tuple<EnumSet<Mode>, Double>> nests = new ArrayList<>();
            nests.add(new Tuple<>(EnumSet.of(autoDriver,autoPassenger),coefficients.get(autoDriver).get("nestingCoefficient")));
            nests.add(new Tuple<>(EnumSet.of(train, tramOrMetro, bus, taxi), coefficients.get(train).get("nestingCoefficient")));
            nests.add(new Tuple<>(EnumSet.of(walk,bicycle), coefficients.get(walk).get("nestingCoefficient")));
            return nests;
        }

        private EnumSet<Mode> determineAvailability(MitoPerson person) {
            EnumSet<Mode> availability = person.getModeRestriction().getRestrictedModeSet();
            assert availability != null;
            if(purpose.equals(RRT)) {
                availability.removeAll(EnumSet.of(autoDriver, autoPassenger, train, tramOrMetro, bus, taxi));
            }
            return availability;
        }

        private EnumMap<Mode, Double> getPersonUtilities(MitoPerson person) {

            EnumMap<Mode, Double> utilities = new EnumMap<>(Mode.class);

            EnumSet<Mode> availableChoices = determineAvailability(person);
            for(Mode mode : availableChoices) {
                if(purpose.equals(RRT)) {
                    utilities.put(mode, getPersonPredictorRRT(person, coefficients.get(mode)));
                } else {
                    utilities.put(mode, getPersonPredictor(person, coefficients.get(mode)));
                }
            }

            return (utilities);
        }

        private EnumMap<Mode, Double> getTripUtilities(EnumMap<Mode, Double> personUtilities, MitoTrip trip) {

            EnumMap<Mode, Double> utilities = new EnumMap<>(Mode.class);

            if(purpose.equals(RRT)) {
                for (Mode mode : personUtilities.keySet()) {
                    utilities.put(mode, personUtilities.get(mode) + getTripPredictorRRT(trip, coefficients.get(mode)));
                }
            } else if (Purpose.getMandatoryPurposes().contains(purpose)) {
                for (Mode mode : personUtilities.keySet()) {
                    utilities.put(mode, calibrationConstants.get(mode) + personUtilities.get(mode) + getTripPredictor(mode, trip, coefficients.get(mode)));
                }
            } else {
                for (Mode mode : personUtilities.keySet()) {
                    if(mode.equals(walk)){
                        if(personUtilities.keySet().size()==1) {
                            countConflictTrips++;
                            utilities.put(mode, calibrationConstants.get(mode) + personUtilities.get(mode) + getTripPredictor(mode, trip, coefficients.get(mode)));
                        }else{
                            utilities.put(mode, calibrationConstants.get(mode) + personUtilities.get(mode) + getTripPredictor(mode, trip, coefficients.get(mode)) + Double.NEGATIVE_INFINITY);
                        }
                    }else{
                        utilities.put(mode, calibrationConstants.get(mode) + personUtilities.get(mode) + getTripPredictor(mode, trip, coefficients.get(mode)));
                    }
                }
            }

            return utilities;
        }

        private double getPersonPredictorRRT(MitoPerson pp, Map<String, Double> coefficients) {
            double predictor = 0.;
            MitoHousehold hh = pp.getHousehold();

            // Intercept
            predictor += coefficients.get("INTERCEPT");

            // Household in urban region
            if(!(hh.getHomeZone().getAreaTypeR().equals(AreaTypes.RType.RURAL))) {
                predictor += coefficients.get("hh.urban");
            }

            // Household size
            int householdSize = hh.getHhSize();
            if (householdSize == 2) {
                predictor += coefficients.get("hh.size_2");
            } else if (householdSize == 3) {
                predictor += coefficients.get("hh.size_3");
            } else if (householdSize == 4) {
                predictor += coefficients.get("hh.size_4");
            } else if (householdSize >= 5) {;
                predictor += coefficients.get("hh.size_5");
            }

            // Age
            int age = pp.getAge();
            if (age <= 18) {
                predictor += coefficients.get("p.age_gr_1");
            } else if (age <= 59) {
                predictor += 0.;
            } else if (age <= 69) {
                predictor += coefficients.get("p.age_gr_5");
            } else {
                predictor += coefficients.get("p.age_gr_6");
            }

            // Female
            if(pp.getMitoGender().equals(MitoGender.FEMALE)) {
                predictor += coefficients.get("p.female");
            }

            return predictor;
        }

        private double getTripPredictorRRT(MitoTrip t, Map<String, Double> coefficients) {
            int originId = t.getTripOrigin().getZoneId();
            int destinationId = t.getTripDestination().getZoneId();
            double tripDistance = dataSet.getTravelDistancesNMT().getTravelDistance(originId,destinationId);
            if(tripDistance == 0) {
                logger.info("0 trip distance for trip " + t.getId());
            }
            return Math.log(tripDistance) * coefficients.get("t.distance_T");
        }

        private double getTripPredictor(Mode mode, MitoTrip t, Map<String, Double> coefficients) {
            int originId = t.getTripOrigin().getZoneId();
            int destinationId = t.getTripDestination().getZoneId();

            MitoZone originZone = dataSet.getZones().get(originId);
            MitoZone destinationZone = dataSet.getZones().get(destinationId);

            int monthlyIncome_EUR = t.getPerson().getHousehold().getMonthlyIncome_EUR();

            // Travel time
            double time;
            switch(mode) {
                case autoDriver:
                case autoPassenger:
                case taxi:
                    time = travelTimes.getTravelTime(originZone, destinationZone, peakHour_s, "car");
                    break;
                case train:
                    time = travelTimes.getTravelTime(originZone, destinationZone, peakHour_s, "train");
                    break;
                case bus:
                    time = travelTimes.getTravelTime(originZone, destinationZone, peakHour_s, "bus");
                    break;
                case tramOrMetro:
                    time = travelTimes.getTravelTime(originZone, destinationZone, peakHour_s, "tramMetro");
                    break;
                case bicycle:
                    time = dataSet.getTravelDistancesNMT().getTravelDistance(originId,destinationId) / SPEED_BICYCLE_KMH * 60.;
                    break;
                case walk:
                    time = dataSet.getTravelDistancesNMT().getTravelDistance(originId,destinationId) / SPEED_WALK_KMH * 60.;
                    break;
                default:
                    throw new RuntimeException("Unknown mode: " + mode);
            }

            // Generalised cost
            double gc;
            if(mode.equals(bicycle) || mode.equals(walk)) {
                gc = time;
            } else {
                double distance = dataSet.getTravelDistancesAuto().getTravelDistance(originId,destinationId);
                double costPerKm = coefficients.get("costPerKm");
                double vot;
                if(monthlyIncome_EUR <= 1500) {
                    vot = coefficients.get("vot_under_1500_eur_min");
                } else if(monthlyIncome_EUR <= 5600) {
                    vot = coefficients.get("vot_1500_to_5600_eur_min");
                } else {
                    vot = coefficients.get("vot_above_5600_eur_min");
                }
                gc = time + distance * costPerKm / vot;
            }

            // Return trip predictor
            return coefficients.get("exp_generalized_time_min") * Math.exp(gc * coefficients.get("alpha"));
        }

        private double getPersonPredictor(MitoPerson pp, Map<String, Double> coefficients) {
            double predictor = 0.;
            MitoHousehold hh = pp.getHousehold();

            // Intercept
            predictor += coefficients.get("intercept");

            // Gender
            if(pp.getMitoGender().equals(MitoGender.MALE)) {
                predictor += coefficients.get("gender_male");
            } else {
                predictor += coefficients.get("gender_female");
            }

            // Occupation
            MitoOccupationStatus occupationStatus = pp.getMitoOccupationStatus();
            if(occupationStatus.equals(MitoOccupationStatus.WORKER)) {
                predictor += coefficients.get("is_employed");
            } else if(occupationStatus.equals(MitoOccupationStatus.STUDENT)) {
                predictor += coefficients.get("is_student");
            } else if(occupationStatus.equals(MitoOccupationStatus.UNEMPLOYED)) {
                predictor += coefficients.get("is_homemaker_or_other");
            } else if(occupationStatus.equals(MitoOccupationStatus.RETIRED)){
                predictor += coefficients.get("is_retired_or_pensioner");
            } else {
                throw new RuntimeException("No occupation for person " + pp.getId());
            }

            // Age
            int age = pp.getAge();
            if (age <= 17) {
                predictor += coefficients.get("age_0_to_17");
            }
            else if (age <= 29) {
                predictor += coefficients.get("age_18_to_29");
            }
            else if (age <= 39) {
                predictor += coefficients.get("age_30_to_39");;
            }
            else if (age <= 49) {
                predictor += coefficients.get("age_40_to_49");
            }
            else if (age <= 59) {
                predictor += coefficients.get("age_50_to_59");
            }
            else {
                predictor += coefficients.get("age_above_60");
            }

            // Economic status
            int householdEconomicStatus = hh.getEconomicStatus();
            if (householdEconomicStatus == 1) {
                predictor += coefficients.get("is_economic_status_very_low");
            } else if (householdEconomicStatus == 2) {
                predictor += coefficients.get("is_economic_status_low");
            } else if (householdEconomicStatus == 3) {
                predictor += coefficients.get("is_economic_status_medium");
            } else if (householdEconomicStatus == 4) {
                predictor += coefficients.get("is_economic_status_high");
            } else if (householdEconomicStatus == 5) {
                predictor += coefficients.get("is_economic_status_very_high");
            } else {
                throw new RuntimeException("Unknown economic status: " + householdEconomicStatus);
            }

            // Household size
            int householdSize = hh.getHhSize();
            if (householdSize == 1) {
                predictor += coefficients.get("is_hh_one_person");
            } else if (householdSize == 2) {
                predictor += coefficients.get("is_hh_two_persons");
            } else if (householdSize == 3) {
                predictor += coefficients.get("is_hh_three_persons");
            } else {
                predictor += coefficients.get("is_hh_four_or_more_persons");
            }

            // Autos
            int householdAutos = hh.getAutos();
            if (householdAutos == 0) {
                predictor += coefficients.get("hh_no_car");
            } else if (householdAutos == 1) {
                predictor += coefficients.get("hh_one_car");
            } else {
                predictor += coefficients.get("hh_two_or_more_cars");
            }

            // Bikes
            int householdBikes = (int) hh.getPersons().values().stream().filter(p -> p.hasBicycle()).count();
            if (householdBikes > 0) {
                predictor += coefficients.get("hh_has_bike");
            } else {
                predictor += coefficients.get("hh_no_bike");
            }

            return predictor;
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
