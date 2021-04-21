package de.tum.bgu.msm.modules.modeChoice;

import de.tum.bgu.msm.data.*;
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
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static de.tum.bgu.msm.data.Mode.*;
import static de.tum.bgu.msm.data.Purpose.*;

public class ModeChoiceWithMoped extends Module {

    private final static Logger logger = Logger.getLogger(ModeChoiceWithMoped.class);
    private final static EnumSet<Purpose> PURPOSES = EnumSet.of(HBW,HBE,HBS,HBR,HBO,RRT,NHBW,NHBO);
    private final static EnumSet<Mode> MODES = EnumSet.of(autoDriver,autoPassenger,publicTransport,bicycle,walk);
    private final static Path modeChoiceCoefFolder = Resources.instance.getModeChoiceCoefficients();

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
        private final EnumMap<Mode, Map<String, Double>> coefficients = new EnumMap<>(Mode.class);
        private int countTripsSkipped;
        private int countMopedWalkTripsSkipped;
        private int countConflictTrips;
        private final static LogitTools<Mode> logitTools = new LogitTools<>(Mode.class);


        ModeChoiceByPurpose(Purpose purpose, DataSet dataSet) {
            super(MitoUtil.getRandomObject().nextLong());
            this.purpose = purpose;
            this.dataSet = dataSet;
            for(Mode mode : MODES) {
                if(!(purpose.equals(RRT) & EnumSet.of(autoDriver,autoPassenger,publicTransport).contains(mode))) {
                    coefficients.put(mode, new CoefficientReader(dataSet, mode,
                            modeChoiceCoefFolder.resolve(purpose.toString() + ".csv")).readCoefficients());
                }
            }
        }

        @Override
        public Void call() {
            logger.info("Mode choice for purpose " + purpose);
            countTripsSkipped = 0;
            try {
                List<Tuple<EnumSet<Mode>, Double>> nests = logitTools.identifyNests(coefficients);
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

        private EnumSet<Mode> determineAvailability(MitoPerson person) {
            EnumSet<Mode> availability = person.getModeRestriction().getRestrictedModeSet();
            assert availability != null;
            if(purpose.equals(RRT)) {
                availability.removeAll(EnumSet.of(autoDriver, autoPassenger, publicTransport));
            }
            return availability;
        }

        private EnumMap<Mode, Double> getPersonUtilities(MitoPerson person) {

            EnumMap<Mode, Double> utilities = new EnumMap<>(Mode.class);

            EnumSet<Mode> availableChoices = determineAvailability(person);
            for(Mode mode : availableChoices) {
                utilities.put(mode, getPersonPredictor(person, coefficients.get(mode)));
            }

            return (utilities);
        }

        private EnumMap<Mode, Double> getTripUtilities(EnumMap<Mode, Double> personUtilities, MitoTrip trip) {

            EnumMap<Mode, Double> utilities = new EnumMap<>(Mode.class);

            if(Purpose.getMandatoryPurposes().contains(trip.getTripPurpose())||trip.getTripPurpose().equals(RRT)) {
                for (Mode mode : personUtilities.keySet()) {
                    utilities.put(mode, personUtilities.get(mode) + getTripPredictor(trip, coefficients.get(mode)));
                }
            }else{
                for (Mode mode : personUtilities.keySet()) {
                    if(mode.equals(walk)){
                        if(personUtilities.keySet().size()==1) {
                            countConflictTrips++;
                            utilities.put(mode, personUtilities.get(mode) + getTripPredictor(trip, coefficients.get(mode)));
                        }else{
                            utilities.put(mode, personUtilities.get(mode) + getTripPredictor(trip, coefficients.get(mode)) + Double.NEGATIVE_INFINITY);
                        }
                    }else{
                        utilities.put(mode, personUtilities.get(mode) + getTripPredictor(trip, coefficients.get(mode)));
                    }
                }
            }

            return utilities;
        }

        private double getTripPredictor(MitoTrip t, Map<String, Double> coefficients) {
            int originId = t.getTripOrigin().getZoneId();
            int destinationId = t.getTripDestination().getZoneId();
            double tripDistance = dataSet.getTravelDistancesNMT().getTravelDistance(originId,destinationId);
            if(tripDistance == 0) {
                logger.info("0 trip distance for trip " + t.getId());
            }

            return Math.log(tripDistance) * coefficients.get("t.distance_T");
        }

        private double getPersonPredictor(MitoPerson pp, Map<String, Double> coefficients) {
            double predictor = 0.;
            MitoHousehold hh = pp.getHousehold();

            // Intercept
            predictor += coefficients.get("INTERCEPT");

            // Household size
            int householdSize = hh.getHhSize();
            if (householdSize == 1) {
                predictor += coefficients.getOrDefault("hh.size_1",0.);
            } else if (householdSize == 2) {
                predictor += coefficients.getOrDefault("hh.size_2",0.);
            } else if (householdSize == 3) {
                predictor += coefficients.getOrDefault("hh.size_3",0.);
            } else if (householdSize == 4) {
                predictor += coefficients.getOrDefault("hh.size_4",0.);
            } else if (householdSize >= 5) {;
                predictor += coefficients.getOrDefault("hh.size_5",0.);
            }

            // Number of children in household
            int householdChildren = DataSet.getChildrenForHousehold(hh);
            if(householdChildren == 1) {
                predictor += coefficients.getOrDefault("hh.children_1",0.);
            } else if (householdChildren == 2) {
                predictor += coefficients.getOrDefault("hh.children_2",0.);
            } else if (householdChildren >= 3) {
                predictor += coefficients.getOrDefault("hh.children_3",0.);
            }

            // Economic status
            int householdEconomicStatus = hh.getEconomicStatus();
            if (householdEconomicStatus == 2) {
                predictor += coefficients.getOrDefault("hh.econStatus_2",0.);
            } else if (householdEconomicStatus == 3) {
                predictor += coefficients.getOrDefault("hh.econStatus_3",0.);
            } else if (householdEconomicStatus == 4) {
                predictor += coefficients.getOrDefault("hh.econStatus_4",0.);
            }

            // Household in urban region
            if(!(hh.getHomeZone().getAreaTypeR().equals(AreaTypes.RType.RURAL))) {
                predictor += coefficients.getOrDefault("hh.urban",0.);
            }

            // Autos
            int householdAutos = hh.getAutos();
            if (householdAutos == 1) {
                predictor += coefficients.getOrDefault("hh.cars_1",0.);
            } else if (householdAutos == 2) {
                predictor += coefficients.getOrDefault("hh.cars_2",0.);
            } else if (householdAutos >= 3) {
                predictor += coefficients.getOrDefault("hh.cars_3",0.);
            }

            // Autos per adult
            int householdAdults = hh.getHhSize() - householdChildren;
            double autosPerAdult = Math.min((double) householdAutos / householdAdults , 1.);
            predictor += autosPerAdult * coefficients.getOrDefault("hh.autosPerAdult",0.);

            // Is home close to PT?
            if(coefficients.containsKey("hh.homePT")) {
                int homeZoneId = hh.getHomeZone().getId();
                double homeDistanceToPT = dataSet.getZones().get(homeZoneId).getDistanceToNearestRailStop();
                double homeWalkToPT = homeDistanceToPT * (60 / 4.8);
                if(homeWalkToPT <= 20) {
                    predictor += coefficients.get("hh.homePT");
                }
            }

            // Is the place of work or study close to PT?
            if(coefficients.containsKey("p.workPT_12")) {
                if(pp.getOccupation() != null) {
                    int occupationZoneId = pp.getOccupation().getZoneId();
                    double occupationDistanceToPT = dataSet.getZones().get(occupationZoneId).getDistanceToNearestRailStop();
                    double occupationWalkToPT = occupationDistanceToPT * (60 / 4.8);
                    if(occupationWalkToPT <= 20) {
                        predictor += coefficients.get("p.workPT_12");
                    }
                }
            }

            // Age
            int age = pp.getAge();
            if (age <= 18) {
                predictor += coefficients.getOrDefault("p.age_gr_1",0.);
            }
            else if (age <= 29) {
                predictor += coefficients.getOrDefault("p.age_gr_2",0.);
            }
            else if (age <= 49) {
                predictor += 0.;
            }
            else if (age <= 59) {
                predictor += coefficients.getOrDefault("p.age_gr_4",0.);
            }
            else if (age <= 69) {
                predictor += coefficients.getOrDefault("p.age_gr_5",0.);
            }
            else {
                predictor += coefficients.getOrDefault("p.age_gr_6",0.);
            }

            // Female
            if(pp.getMitoGender().equals(MitoGender.FEMALE)) {
                predictor += coefficients.getOrDefault("p.female",0.);
            }

            // Has drivers Licence
            if(pp.hasDriversLicense()) {
                predictor += coefficients.getOrDefault("p.driversLicense",0.);
            }

            // Has bicycle
            if(pp.hasBicycle()) {
                predictor += coefficients.getOrDefault("p.ownBicycle",0.);
            }

            // Number of mandatory trips
            int workTrips = pp.getTripsForPurpose(Purpose.HBW).size();

            if (workTrips == 0) {
                predictor += coefficients.getOrDefault("p.workTrips_0",0.);
            } else if (workTrips < 5) {
                predictor += coefficients.getOrDefault("p.workTrips_1234",0.);
            } else {
                predictor += coefficients.getOrDefault("p.workTrips_5",0.);
            }

            int eduTrips = pp.getTripsForPurpose(Purpose.HBE).size();
            if (eduTrips >= 5) {
                predictor += coefficients.getOrDefault("p.eduTrips_5",0.);
            } else if (eduTrips > 0) {
                predictor += coefficients.getOrDefault("p.eduTrips_1234",0.);
            }

            // Usual commute mode
            Mode dominantCommuteMode = pp.getDominantCommuteMode();
            if(dominantCommuteMode != null) {
                if (dominantCommuteMode.equals(Mode.autoDriver)) {
                    predictor += coefficients.getOrDefault("p.usualCommuteMode_carD",0.);
                } else if (dominantCommuteMode.equals(Mode.autoPassenger)) {
                    predictor += coefficients.getOrDefault("p.usualCommuteMode_carP",0.);
                } else if (dominantCommuteMode.equals(Mode.publicTransport)) {
                    predictor += coefficients.getOrDefault("p.usualCommuteMode_PT",0.);
                } else if (dominantCommuteMode.equals(Mode.bicycle)) {
                    predictor += coefficients.getOrDefault("p.usualCommuteMode_cycle",0.);
                } else if (dominantCommuteMode.equals(Mode.walk)) {
                    predictor += coefficients.getOrDefault("p.usualCommuteMode_walk",0.);
                }
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
