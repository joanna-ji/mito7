package de.tum.bgu.msm.modules.modeChoice;

import de.tum.bgu.msm.data.DataSet;
import de.tum.bgu.msm.data.Purpose;
import de.tum.bgu.msm.io.input.readers.CoefficientReader;
import de.tum.bgu.msm.modules.Module;
import de.tum.bgu.msm.resources.Resources;
import de.tum.bgu.msm.util.MitoUtil;
import de.tum.bgu.msm.util.concurrent.ConcurrentExecutor;
import de.tum.bgu.msm.util.concurrent.RandomizableConcurrentFunction;
import org.apache.log4j.Logger;

import java.nio.file.Path;
import java.util.Map;

import static de.tum.bgu.msm.data.ModeRestriction.*;


public class ModeRestrictionChoice extends Module {

    private final static Logger logger = Logger.getLogger(ModeChoice.class);

    public ModeRestrictionChoice(DataSet dataSet) {
        super(dataSet);
    }

    @Override
    public void run() {
        logger.info(" Calculating mode restriction.");
        modeRestriction();
    }

    private void modeRestriction() {
        ConcurrentExecutor<Void> executor = ConcurrentExecutor.fixedPoolService(Purpose.values().length);
        executor.addTaskToQueue(new modeRestrictionChoice(dataSet));
        executor.execute();
    }

    static class modeRestrictionChoice extends RandomizableConcurrentFunction<Void> {

        private final DataSet dataSet;
        private int nonMobilePersons;

        private final static Path modeRestrictionCoefPath = Resources.instance.getModeRestrictionCoefficients();

        private final Map<String, Double> autoCoef;
        private final Map<String, Double> autoPtWalkCoef;
        private final Map<String, Double> autoPtWalkCycleCoef;
        private final Map<String, Double> ptWalkCoef;
        private final Map<String, Double> ptWalkCycleCoef;

        modeRestrictionChoice(DataSet dataSet) {
            super(MitoUtil.getRandomObject().nextLong());
            this.dataSet = dataSet;
            this.autoCoef = new CoefficientReader(dataSet, auto, modeRestrictionCoefPath).readCoefficients();
            this.autoPtWalkCoef = new CoefficientReader(dataSet, autoPtWalk, modeRestrictionCoefPath).readCoefficients();
            this.autoPtWalkCycleCoef = new CoefficientReader(dataSet, autoPtWalkCycle, modeRestrictionCoefPath).readCoefficients();
            this.ptWalkCoef = new CoefficientReader(dataSet, ptWalk, modeRestrictionCoefPath).readCoefficients();
            this.ptWalkCycleCoef = new CoefficientReader(dataSet, ptWalkCycle, modeRestrictionCoefPath).readCoefficients();
        }

        @Override
        public Void call() {
            nonMobilePersons = 0;
//            try {
//                for (MitoPerson person : dataSet.getPersons().values()) {
//                    if(person.getTrips().size() == 0) {
//                        nonMobilePersons++;
//                    } else {
//                        chooseModeRestriction(person, calculateProbabilities(calculateUtilities(person)));
//                    }
//                }
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
            logger.info(nonMobilePersons + " non-mobile persons skipped");
            return null;
        }

 /*       private ArrayList<ModeRestriction> calculateAvailability(MitoPerson person) {

            ArrayList<ModeRestriction> availableChoices = new ArrayList<>();
            Mode dominantCommuteMode = person.getDominantCommuteMode();

            boolean commuter = dominantCommuteMode != null;

            if(!(dominantCommuteMode == null)) {
                for(ModeRestriction modeRestriction : ModeRestriction.values()) {
                    if(commuter && modeRestriction.getRestrictedModeSet().contains(dominantCommuteMode)) {
                        availableChoices.add(modeRestriction);
                    } else if (!commuter) {
                        availableChoices.add(modeRestriction);
                    }
                }
            }

            if()

            if(dominantCommuteMode == null & person.getTripsForPurpose(Purpose.RRT).size() == 0) {

            }

            if (person.getTripsForPurpose(Purpose.RRT).size() == 0) {
                if (dominantCommuteMode == null) {
                    availableChoices.add(auto);
                }
                else if (dominantCommuteMode.equals(Mode.autoDriver) || dominantCommuteMode.equals(Mode.autoPassenger)) {
                    availableChoices.add(auto);
                }
            }

            if



            Mode dominantCommuteMode = person.getDominantCommuteMode();


        }

        private EnumMap<ModeRestriction, Double> calculateUtilities(MitoPerson person) {

            EnumMap<ModeRestriction, Double> utilities = new EnumMap(ModeRestriction.class);

            utilities.put(auto , getResponse(person, autoCoef));
            utilities.put(autoPtWalk , getResponse(person, autoPtWalkCoef));
            utilities.put(autoPtWalkCycle , getResponse(person, autoPtWalkCycleCoef));
            utilities.put(ptWalk , getResponse(person, ptWalkCoef));
            utilities.put(ptWalkCycle , getResponse(person, ptWalkCycleCoef));

            return (utilities);
        }

        private EnumMap<ModeRestriction, Double> calculateProbabilities(EnumMap<ModeRestriction, Double> utilities) {

            return calculateMultinomialLogitProbabilities(utilities);
        }

        private double getResponse(MitoPerson pp, Map<String, Double> coefficients) {
            double response = 0.;
            MitoHousehold hh = pp.getHousehold();

            // Household children
            int householdChildren = DataSet.getChildrenForHousehold(hh);
            if(householdChildren >= 2) {
                response += coefficients.get("hh.children_23");
            }

            // Autos
            int householdAutos = hh.getAutos();
            if (householdAutos == 1) {
                response += coefficients.get("hh.cars_1");
            } else if (householdAutos == 2) {
                response += coefficients.get("hh.cars_2");
            } else if (householdAutos >= 3) {
                response += coefficients.get("hh.cars_3");
            }

            // Autos per adult
            int householdAdults = hh.getHhSize() - householdChildren;
            double autosPerAdult = Math.min((double) householdAutos / householdAdults , 1.);
            response += autosPerAdult * coefficients.get("hh.autosPerAdult");

            // Economic status
            int householdEconomicStatus = hh.getEconomicStatus();
            if (householdEconomicStatus == 2) {
                response += coefficients.get("hh.econStatus_2");
            } else if (householdEconomicStatus == 3) {
                response += coefficients.get("hh.econStatus_3");
            } else if (householdEconomicStatus == 4) {
                response += coefficients.get("hh.econStatus_4");
            }

            // Urban
            if(!(hh.getHomeZone().getAreaTypeR().equals(AreaTypes.RType.RURAL))) {
                response += coefficients.get("hh.urban");
            }

            // Is the home close to PT?
            int homeZoneId = hh.getHomeZone().getId();
            double homeDistanceToPT = dataSet.getZones().get(homeZoneId).getDistanceToNearestRailStop();
            double homeWalkToPT = homeDistanceToPT * (60 / 4.8);
            if(homeWalkToPT <= 20) {
                response += coefficients.get("hh.homePT");
            }

            // Age
            int age = pp.getAge();
            if (age <= 18) {
                response += coefficients.get("p.age_gr_1");
            }
            else if (age <= 29) {
                response += coefficients.get("p.age_gr_2");
            }
            else if (age <= 49) {
                response += 0.;
            }
            else if (age <= 59) {
                response += coefficients.get("p.age_gr_4");
            }
            else if (age <= 69) {
                response += 0.;
            }
            else {
                response += coefficients.get("p.age_gr_6");
            }

            // Female
            if(pp.getMitoGender().equals(MitoGender.FEMALE)) {
                response += coefficients.get("p.female");
            }

            // Has drivers Licence
            if(pp.hasDriversLicense()) {
                response += coefficients.get("p.driversLicence");
            }

            // Has bicycle
            if(pp.hasBicycle()) {
                response += coefficients.get("p.ownBicycle");
            }

            // Number of trips for each purpose
            response += Math.sqrt(pp.getTripsForPurpose(Purpose.HBW).size()) * coefficients.get("p.trips_HBW_T");
            response += Math.sqrt(pp.getTripsForPurpose(Purpose.HBE).size()) * coefficients.get("p.trips_HBE_T");
            response += Math.sqrt(pp.getTripsForPurpose(Purpose.HBS).size()) * coefficients.get("p.trips_HBS_T");
            response += Math.sqrt(pp.getTripsForPurpose(Purpose.HBR).size()) * coefficients.get("p.trips_HBR_T");
            response += Math.sqrt(pp.getTripsForPurpose(Purpose.HBO).size()) * coefficients.get("p.trips_HBO_T");
            response += Math.sqrt(pp.getTripsForPurpose(Purpose.NHBW).size()) * coefficients.get("p.trips_NHBW_T");
            response += Math.sqrt(pp.getTripsForPurpose(Purpose.NHBO).size()) * coefficients.get("p.trips_NHBO_T");
            if(pp.getTripsForPurpose(Purpose.RRT).size() >= 1) {
                response += coefficients.get("p.isMobile_RRT");
            }

            // Mean commute trip length & coefficient of variation
            List<MitoTrip> trips = pp.getTrips();
            ArrayList<Double> tripDistances = new ArrayList<>();
            int tripCount = trips.size();
            double sum = 0;
            double sumSquaredDiff = 0;
            double maxTripDistance = 0.;
            double minTripDistance = 99999.;

            for (MitoTrip trip : trips) {
                int originId = trip.getTripOrigin().getZoneId();
                int destinationId = trip.getTripDestination().getZoneId();
                double tripDistance = dataSet.getTravelDistancesNMT().getTravelDistance(originId, destinationId);
                if (tripDistance > maxTripDistance) {
                    maxTripDistance = tripDistance;
                }
                if (tripDistance < minTripDistance) {
                    minTripDistance = tripDistance;
                }
                tripDistances.add(tripDistance);
                sum += tripDistance;
            }
            double mean = sum / tripCount;
            for (Double s : tripDistances) {
                sumSquaredDiff += Math.pow(s - mean , 2);
            }

            double coefficientOfVariation = Math.sqrt(sumSquaredDiff / tripCount) / mean;

            if(maxTripDistance == 0. | minTripDistance == 99999.) {
                logger.info("bad distance info for person " + pp.getId());
            }

            response += Math.log(minTripDistance) * coefficients.get("p.min_km_T");
            response += Math.log(maxTripDistance) * coefficients.get("p.max_km_T");
            response += coefficientOfVariation * coefficients.get("p.cv_km");

            // Usual commute mode
            Mode dominantCommuteMode = pp.getDominantCommuteMode();
            if (dominantCommuteMode.equals(Mode.publicTransport)) {
                response += coefficients.get("p.usualCommuteMode_PT");
            } else if (dominantCommuteMode.equals(Mode.bicycle)) {
                response += coefficients.get("p.usualCommuteMode_cycle");
            } else if (dominantCommuteMode.equals(Mode.walk)) {
                response += coefficients.get("p.usualCommuteMode_walk");
            }

            return response;
        }

        private EnumMap<ModeRestriction, Double> calculateMultinomialLogitProbabilities(EnumMap<ModeRestriction, Double> utilities) {
            EnumMap<Mode, Double> expModeUtils = new EnumMap(Mode.class);
            EnumMap<Mode, Double> expNestSums = new EnumMap(Mode.class);
            EnumMap<Mode, Double> expNestUtils = new EnumMap(Mode.class);
            EnumMap<Mode, Double> probabilities = new EnumMap(Mode.class);
            double expSumRoot = 0;



            for(ModeRestriction option : ModeRestriction.values()) {
                expSumRoot += Math.exp(utilities.get(option));
            }

            for(Tuple<EnumSet<Mode>,Double> nest : nests) {
                double expNestSum = 0;
                EnumSet<Mode> modes = nest.getFirst();
                double nestingCoefficient = nest.getSecond();
                for(Mode mode : modes) {
                    double expModeUtil = Math.exp(utilities.get(mode) / nestingCoefficient);
                    expModeUtils.put(mode, expModeUtil);
                    expNestSum += expModeUtil;
                }
                double expNestUtil = Math.exp(nestingCoefficient * Math.log(expNestSum));
                for(Mode mode : modes) {
                    expNestSums.put(mode, expNestSum);
                    expNestUtils.put(mode, expNestUtil);
                }
                expSumRoot += expNestUtil;
            }

            Set<Mode> choiceSet = utilities.keySet();
            for (Mode mode : choiceSet) {
                double expSumNest = expNestSums.get(mode);
                if(expSumNest == 0) {
                    probabilities.put(mode, 0.);
                } else {
                    probabilities.put(mode, (expModeUtils.get(mode) * expNestUtils.get(mode)) / (expSumNest * expSumRoot));
                }
            }
            return probabilities;
        }

        private void chooseModeRestriction(MitoPerson person, EnumMap<Mode, Double> probabilities) {
            if (probabilities == null) {
                nonMobilePersons++;
                return;
            }

            //found Nan when there is no transit!!
            probabilities.replaceAll((mode, probability) -> probability.isNaN() ? 0: probability);

            double sum = MitoUtil.getSum(probabilities.values());
            if (sum > 0) {
                final Mode select = MitoUtil.select(probabilities, random);
                person.setDominantCommuteMode(select);
            } else {
                logger.error("Dominant Commute Mode: Negative probabilities for person " + person.getId());
                person.setDominantCommuteMode(null);
            }
        }
        */
    }
}

