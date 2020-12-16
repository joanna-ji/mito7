package de.tum.bgu.msm.modules.modeChoice;

import de.tum.bgu.msm.data.*;
import org.apache.log4j.Logger;
import org.matsim.core.utils.collections.Tuple;

import java.util.*;
import java.util.stream.Collectors;


public class LogitCalculator <E extends Enum<E>> {

    private static final Logger logger = Logger.getLogger(LogitCalculator.class);

    private final DataSet dataSet;
    private final EnumMap<E, Map<String, Double>> coefficientsByOption;
    private final List<Tuple<EnumSet<E>, Double>> nests;
    private final Class<E> enumClass;

    public LogitCalculator(DataSet dataSet, EnumMap<E, Map<String, Double>> coefficientsByOption) {
        this.dataSet = dataSet;
        this.coefficientsByOption = coefficientsByOption;
        this.nests = getNests();
        this.enumClass = coefficientsByOption.keySet().iterator().next().getDeclaringClass();
    }

    public EnumMap<E, Double> getProbabilities(MitoPerson person, Set<E> availableOptions) {

        EnumMap<E, Double> utilities = getUtilities(person, availableOptions);

        if(nests == null) {
            return multinomialLogit(utilities);
        } else {
            return nestedLogit(utilities);
        }
    }

    public EnumMap<E, Double> getProbabilities(MitoPerson person, MitoTrip trip, Set<E> availableOptions) {
        EnumMap<E, Double> utilities = getUtilities(person, trip, availableOptions);

        if(nests == null) {
            return multinomialLogit(utilities);
        } else {
            return nestedLogit(utilities);
        }
    }

    public EnumMap<E, Double> getProbabilities(MitoPerson person) {
        return getProbabilities(person, coefficientsByOption.keySet());
    }

    private List<Tuple<EnumSet<E>, Double>> getNests() {

        Map<Integer,Tuple<EnumSet<E>, Double>> nests = new HashMap<>();

        if(!coefficientsByOption.entrySet().iterator().next().getValue().containsKey("nest_number") &&
                !coefficientsByOption.entrySet().iterator().next().getValue().containsKey("nest_coefficient")) {
            logger.info("Using Multinomial Logit.");
            return null;
        } else {
            for(E option : coefficientsByOption.keySet()) {
                int nestCode = coefficientsByOption.get(option).get("nest_number").intValue();
                if(nests.keySet().contains(nestCode)) {
                    nests.get(nestCode).getFirst().add(option);
                } else {
                    nests.put(nestCode, new Tuple<>(EnumSet.of(option),coefficientsByOption.get(option).get("nest_coefficient")));
                }
            }

            logger.info("Using Nested Logit. Nests:");
            List<Tuple<EnumSet<E>, Double>> testNests =  nests.values().stream().collect(Collectors.toList());
            for(int code : nests.keySet()) {
                logger.info("Nest " + code + ": " + nests.get(code).getFirst().stream().map(Enum::toString).collect(Collectors.joining(",")) +
                        " | Coefficient: " + nests.get(code).getSecond());
            }

            return nests.values().stream().collect(Collectors.toList());
        }
    }

    private EnumMap<E, Double> multinomialLogit(EnumMap<E, Double> utilities) {

        EnumMap<E, Double> probabilities = new EnumMap<>(enumClass);
        EnumMap<E, Double> expUtils = new EnumMap<>(enumClass);
        double expUtilsSum = 0.;

        for(E option : utilities.keySet()) {
            double expUtil = Math.exp(utilities.get(option));
            expUtils.put(option, expUtil);
            expUtilsSum += expUtil;
        }

        for(E option : utilities.keySet()) {
            probabilities.put(option, expUtils.get(option) / expUtilsSum);
        }

        return probabilities;
    }

    private EnumMap<E, Double> nestedLogit(EnumMap<E, Double> utilities) {

        EnumMap<E, Double> expOptionUtils = new EnumMap(enumClass);
        EnumMap<E, Double> expNestSums = new EnumMap(enumClass);
        EnumMap<E, Double> expNestUtils = new EnumMap(enumClass);
        EnumMap<E, Double> probabilities = new EnumMap(enumClass);
        double expSumRoot = 0;

        for(Tuple<EnumSet<E>,Double> nest : nests) {
            double expNestSum = 0;
            EnumSet<E> nestOptions = EnumSet.copyOf(nest.getFirst());
            nestOptions.retainAll(utilities.keySet());
            double nestingCoefficient = nest.getSecond();
            for(E option : nestOptions) {
                double expOptionUtil = Math.exp(utilities.get(option) / nestingCoefficient);
                expOptionUtils.put(option, expOptionUtil);
                expNestSum += expOptionUtil;
            }
            double expNestUtil = Math.exp(nestingCoefficient * Math.log(expNestSum));
            for(E option : nestOptions) {
                expNestSums.put(option, expNestSum);
                expNestUtils.put(option, expNestUtil);
            }
            expSumRoot += expNestUtil;
        }

        for (E option : utilities.keySet()) {
            if(expNestSums.get(option) == 0) {
                probabilities.put(option, 0.);
            } else {
                probabilities.put(option, (expOptionUtils.get(option) * expNestUtils.get(option)) / (expNestSums.get(option) * expSumRoot));
            }
        }
        return probabilities;
    }

    private EnumMap<E, Double> getUtilities(MitoPerson person, Set<E> availableOptions) {

        EnumMap<E, Double> utilities = new EnumMap(enumClass);

        for(E option : availableOptions) {
            utilities.put(option, getPredictor(person, coefficientsByOption.get(option)));
        }

        return (utilities);
    }

    private EnumMap<E, Double> getUtilities(MitoPerson person, MitoTrip trip, Set<E> availableOptions) {

        EnumMap<E, Double> utilities = new EnumMap(enumClass);

        for(E option : availableOptions) {
            utilities.put(option, getPredictor(person, trip, coefficientsByOption.get(option)));
        }

        return (utilities);
    }

    private double getPredictor(MitoPerson pp, Map<String, Double> coefficients) {
        double response = 0.;
        MitoHousehold hh = pp.getHousehold();

        // Intercept
        response += coefficients.get("INTERCEPT");

        // Household size
        int householdSize = hh.getHhSize();
        if (householdSize == 1) {
            response += coefficients.getOrDefault("hh.size_1",0.);
        } else if (householdSize == 2) {
            response += coefficients.getOrDefault("hh.size_2",0.);
        } else if (householdSize == 3) {
            response += coefficients.getOrDefault("hh.size_3",0.);
        } else if (householdSize == 4) {
            response += coefficients.getOrDefault("hh.size_4",0.);
        } else if (householdSize >= 5) {;
            response += coefficients.getOrDefault("hh.size_5",0.);
        }

        // Number of children in household
        int householdChildren = DataSet.getChildrenForHousehold(hh);
        if(householdChildren == 1) {
            response += coefficients.getOrDefault("hh.children_1",0.);
        } else if (householdChildren == 2) {
            response += coefficients.getOrDefault("hh.children_2",0.);
        } else if (householdChildren >= 3) {
            response += coefficients.getOrDefault("hh.children_3",0.);
        }

        // Economic status
        int householdEconomicStatus = hh.getEconomicStatus();
        if (householdEconomicStatus == 2) {
            response += coefficients.getOrDefault("hh.econStatus_2",0.);
        } else if (householdEconomicStatus == 3) {
            response += coefficients.getOrDefault("hh.econStatus_3",0.);
        } else if (householdEconomicStatus == 4) {
            response += coefficients.getOrDefault("hh.econStatus_4",0.);
        }

        // Household in urban region
        if(!(hh.getHomeZone().getAreaTypeR().equals(AreaTypes.RType.RURAL))) {
            response += coefficients.getOrDefault("hh.urban",0.);
        }

        // Autos
        int householdAutos = hh.getAutos();
        if (householdAutos == 1) {
            response += coefficients.getOrDefault("hh.cars_1",0.);
        } else if (householdAutos == 2) {
            response += coefficients.getOrDefault("hh.cars_2",0.);
        } else if (householdAutos >= 3) {
            response += coefficients.getOrDefault("hh.cars_3",0.);
        }

        // Autos per adult
        int householdAdults = hh.getHhSize() - householdChildren;
        double autosPerAdult = Math.min((double) householdAutos / householdAdults , 1.);
        response += autosPerAdult * coefficients.getOrDefault("hh.autosPerAdult",0.);

        // Is home close to PT?
        if(coefficients.containsKey("hh.homePT")) {
            int homeZoneId = hh.getHomeZone().getId();
            double homeDistanceToPT = dataSet.getZones().get(homeZoneId).getDistanceToNearestRailStop();
            double homeWalkToPT = homeDistanceToPT * (60 / 4.8);
            if(homeWalkToPT <= 20) {
                response += coefficients.get("hh.homePT");
            }
        }

        // Is the place of work or study close to PT?
        if(coefficients.containsKey("p.workPT_12")) {
            if(pp.getOccupation() != null) {
                int occupationZoneId = pp.getOccupation().getZoneId();
                double occupationDistanceToPT = dataSet.getZones().get(occupationZoneId).getDistanceToNearestRailStop();
                double occupationWalkToPT = occupationDistanceToPT * (60 / 4.8);
                if(occupationWalkToPT <= 20) {
                    response += coefficients.get("p.workPT_12");
                }
            }
        }

        // Age
        int age = pp.getAge();
        if (age <= 18) {
            response += coefficients.getOrDefault("p.age_gr_1",0.);
        }
        else if (age <= 29) {
            response += coefficients.getOrDefault("p.age_gr_2",0.);
        }
        else if (age <= 49) {
            response += 0.;
        }
        else if (age <= 59) {
            response += coefficients.getOrDefault("p.age_gr_4",0.);
        }
        else if (age <= 69) {
            response += coefficients.getOrDefault("p.age_gr_5",0.);
        }
        else {
            response += coefficients.getOrDefault("p.age_gr_6",0.);
        }

        // Female
        if(pp.getMitoGender().equals(MitoGender.FEMALE)) {
            response += coefficients.getOrDefault("p.female",0.);
        }

        // Has drivers Licence
        if(pp.hasDriversLicense()) {
            response += coefficients.getOrDefault("p.driversLicence",0.);
        }

        // Has bicycle
        if(pp.hasBicycle()) {
            response += coefficients.getOrDefault("p.ownBicycle",0.);
        }

        // Number of mandatory trips
        int trips_HBW = pp.getTripsForPurpose(Purpose.HBW).size();

        if (trips_HBW == 0) {
            response += coefficients.getOrDefault("p.trips_HBW_0",0.);
        } else if (trips_HBW < 5) {
            response += coefficients.getOrDefault("p.trips_HBW_1234",0.);
            response += coefficients.getOrDefault("p.isMobile_HBW",0.);
        } else {
            response += coefficients.getOrDefault("p.trips_HBW_5",0.);
            response += coefficients.getOrDefault("p.isMobile_HBW",0.);
        }

        int trips_HBE = pp.getTripsForPurpose(Purpose.HBE).size();

        if (trips_HBE == 0) {
            response += 0;
        } else if (trips_HBW < 5) {
            response += coefficients.getOrDefault("p.trips_HBE_1324",0.);
            response += coefficients.getOrDefault("p.isMobile_HBE",0.);
        } else {
            response += coefficients.getOrDefault("p.trips_HBE_5",0.);
            response += coefficients.getOrDefault("p.isMobile_HBE",0.);
        }

        // Number of discretionary trips
        response += Math.sqrt(Math.min(trips_HBW,5)) * coefficients.getOrDefault("p.trips_HBW_T",0.);
        response += Math.sqrt(Math.min(trips_HBE,5)) * coefficients.getOrDefault("p.trips_HBE_T",0.);
        response += Math.sqrt(pp.getTripsForPurpose(Purpose.HBS).size()) * coefficients.getOrDefault("p.trips_HBS_T",0.);
        response += Math.sqrt(pp.getTripsForPurpose(Purpose.HBR).size()) * coefficients.getOrDefault("p.trips_HBR_T",0.);
        response += Math.sqrt(pp.getTripsForPurpose(Purpose.HBO).size()) * coefficients.getOrDefault("p.trips_HBO_T",0.);
        response += Math.sqrt(pp.getTripsForPurpose(Purpose.NHBW).size()) * coefficients.getOrDefault("p.trips_NHBW_T",0.);
        response += Math.sqrt(pp.getTripsForPurpose(Purpose.NHBO).size()) * coefficients.getOrDefault("p.trips_NHBO_T",0.);
        if(pp.getTripsForPurpose(Purpose.RRT).size() >= 1) {
            response += coefficients.getOrDefault("p.isMobile_RRT",0.);
        }

        // Mandatory trips: Mean & coefficient of variation
        if(coefficients.containsKey("p.m_mode_km_T") || coefficients.containsKey("p.m_cv_km")) {
            List<MitoTrip> workTrips = pp.getTripsForPurpose(Purpose.HBW);
            List<MitoTrip> educationTrips = pp.getTripsForPurpose(Purpose.HBE);

            List<MitoTrip> commuteTrips = new ArrayList(workTrips);
            commuteTrips.addAll(educationTrips);
            int commuteTripCount = commuteTrips.size();

            if (commuteTripCount > 0) {
                ArrayList<Double> commuteTripDistances = new ArrayList<>();
                double sum = 0;
                for (MitoTrip trip : commuteTrips) {
                    int originId = trip.getTripOrigin().getZoneId();
                    int destinationId = trip.getTripDestination().getZoneId();
                    double commuteDistance = dataSet.getTravelDistancesNMT().getTravelDistance(originId,destinationId);
                    commuteTripDistances.add(commuteDistance);
                    sum += commuteDistance;
                }
                double mean = sum / commuteTripCount;
                double sqrtMean = Math.sqrt(mean);
                double sumSquaredDiff = 0;
                for(Double s : commuteTripDistances) {
                    sumSquaredDiff += Math.pow(s - mean , 2);
                }
                double standardDeviation = Math.sqrt(sumSquaredDiff / commuteTripCount);
                double coefficientOfVariation = standardDeviation / mean;

                response += sqrtMean * coefficients.getOrDefault("p.m_mode_km_T",0.);
                response += coefficientOfVariation * coefficients.getOrDefault("p.m_cv_km",0.);
            }
        }

        // All trips: min, max, and coefficient of variation
        if(coefficients.containsKey("p.min_km_T") ||
                coefficients.containsKey("p.max_km_T") ||
                coefficients.containsKey("p.cv_km")) {

            List<MitoTrip> trips = pp.getTrips();
            ArrayList<Double> tripDistances = new ArrayList<>();
            int validTripCount = 0;
            double sum = 0;
            double sumSquaredDiff = 0;
            double maxTripDistance = 0.;
            double minTripDistance = 99999.;

            for (MitoTrip trip : trips) {
                Location origin = trip.getTripOrigin();
                Location destination = trip.getTripDestination();

                if(origin != null && destination != null) {
                    int originId = origin.getZoneId();
                    int destinationId = destination.getZoneId();
                    double tripDistance = dataSet.getTravelDistancesNMT().getTravelDistance(originId, destinationId);
                    if (tripDistance == 0.0) {
                        logger.warn("0 trip distance for person " + pp.getId());
                    }
                    if (tripDistance > maxTripDistance) {
                        maxTripDistance = tripDistance;
                    }
                    if (tripDistance < minTripDistance) {
                        minTripDistance = tripDistance;
                    }
                    tripDistances.add(tripDistance);
                    sum += tripDistance;
                    validTripCount++;
                }
            }
            double mean = sum / validTripCount;
            for (Double s : tripDistances) {
                sumSquaredDiff += Math.pow(s - mean , 2);
            }

            double coefficientOfVariation = Math.sqrt(sumSquaredDiff / validTripCount) / mean;

            if(maxTripDistance == 0. | minTripDistance == 99999.) {
                // todo: make new person property to record people with no distance info (too many for error msg.)
                logger.error("No distance info for person " + pp.getId());
                maxTripDistance = 1.;
                minTripDistance = 1.;
                coefficientOfVariation = 0.;
            }

            response += Math.log(minTripDistance) * coefficients.getOrDefault("p.min_km_T",0.);
            response += Math.log(maxTripDistance) * coefficients.getOrDefault("p.max_km_T",0.);
            response += coefficientOfVariation * coefficients.getOrDefault("p.cv_km",0.);
        }

        // Usual commute mode
        Mode dominantCommuteMode = pp.getDominantCommuteMode();
        if(dominantCommuteMode != null) {
            if (dominantCommuteMode.equals(Mode.autoDriver)) {
                response += coefficients.getOrDefault("p.usualCommuteMode_carD",0.);
            } else if (dominantCommuteMode.equals(Mode.autoPassenger)) {
                response += coefficients.getOrDefault("p.usualCommuteMode_carP",0.);
            } else if (dominantCommuteMode.equals(Mode.publicTransport)) {
                response += coefficients.getOrDefault("p.usualCommuteMode_PT",0.);
            } else if (dominantCommuteMode.equals(Mode.bicycle)) {
                response += coefficients.getOrDefault("p.usualCommuteMode_cycle",0.);
            } else if (dominantCommuteMode.equals(Mode.walk)) {
                response += coefficients.getOrDefault("p.usualCommuteMode_walk",0.);
            }
        }

        return response;
    }

    private double getPredictor(MitoPerson pp, MitoTrip t, Map<String, Double> coefficients) {

        // Household & person predictors
        double response = getPredictor(pp, coefficients);

        // Trip Predictors
        int originId = t.getTripOrigin().getZoneId();
        int destinationId = t.getTripDestination().getZoneId();
        double tripDistance = dataSet.getTravelDistancesNMT().getTravelDistance(originId,destinationId);

        if(tripDistance == 0) {
            logger.info("0 trip distance for trip " + t.getId());
        }

        response += Math.log(tripDistance) * coefficients.getOrDefault("t.distance_T", 0.);

        return response;
    }
}