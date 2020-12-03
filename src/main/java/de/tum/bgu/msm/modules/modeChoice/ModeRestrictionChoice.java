package de.tum.bgu.msm.modules.modeChoice;

import de.tum.bgu.msm.data.*;
import de.tum.bgu.msm.io.input.readers.CoefficientReader;
import de.tum.bgu.msm.modules.Module;
import de.tum.bgu.msm.resources.Resources;
import de.tum.bgu.msm.util.MitoUtil;
import de.tum.bgu.msm.util.concurrent.ConcurrentExecutor;
import de.tum.bgu.msm.util.concurrent.RandomizableConcurrentFunction;
import org.apache.commons.math3.analysis.function.Logit;
import org.apache.log4j.Logger;

import java.nio.file.Path;
import java.util.*;

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

        private final Map<ModeRestriction, Map<String, Double>> coefficients = new HashMap<>();

        modeRestrictionChoice(DataSet dataSet) {
            super(MitoUtil.getRandomObject().nextLong());
            this.dataSet = dataSet;
            for(ModeRestriction modeRestriction : ModeRestriction.values()) {
                coefficients.put(modeRestriction,
                        new CoefficientReader(dataSet, modeRestriction, modeRestrictionCoefPath).readCoefficients());
            }
        }

        @Override
        public Void call() {
            nonMobilePersons = 0;
            try {
                for (MitoPerson person : dataSet.getPersons().values()) {
                    if(person.getTrips().size() == 0) {
                        nonMobilePersons++;
                    } else {
                        chooseModeRestriction(person,
                                LogitProbabilitiesCalculator.multinomialLogit(
                                calculateUtilities(person, determineAvailability(person))));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            logger.info(nonMobilePersons + " non-mobile persons skipped");
            return null;
        }

        private EnumSet<ModeRestriction> determineAvailability(MitoPerson person) {

            EnumSet<ModeRestriction> availableChoices = EnumSet.noneOf(ModeRestriction.class);
            Mode dominantCommuteMode = person.getDominantCommuteMode();

            boolean commuter = dominantCommuteMode != null;
            boolean hasRrtTrips = person.getTripsForPurpose(Purpose.RRT).size() > 0;

            EnumSet<ModeRestriction> possibleOptions;
            if(hasRrtTrips) {
                possibleOptions = EnumSet.of(autoPtWalk, autoPtWalkCycle, ptWalk, ptWalkCycle);
            } else {
                possibleOptions = EnumSet.allOf(ModeRestriction.class);
            }

            if(commuter) {
                for (ModeRestriction modeRestriction : possibleOptions) {
                    if (modeRestriction.getRestrictedModeSet().contains(dominantCommuteMode)) {
                        availableChoices.add(modeRestriction);
                    }
                }
            } else {
                availableChoices.addAll(possibleOptions);
            }
            return availableChoices;
        }

        private EnumMap<ModeRestriction, Double> calculateUtilities(MitoPerson person, EnumSet<ModeRestriction> availableOptions) {

            EnumMap<ModeRestriction, Double> utilities = new EnumMap(ModeRestriction.class);

            for(ModeRestriction modeRestriction : availableOptions) {
                utilities.put(modeRestriction, getResponse(person, coefficients.get(modeRestriction)));
            }

            return (utilities);
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

        private void chooseModeRestriction(MitoPerson person, EnumMap<ModeRestriction, Double> probabilities) {
            double sum = MitoUtil.getSum(probabilities.values());
            if (Math.abs(sum - 1) > 0.1) {
                logger.warn("Mode probabilities don't add to 1 for person " + person.getId());
            }
            if (sum > 0) {
                final ModeRestriction select = MitoUtil.select(probabilities, random);
                person.setModeRestriction(select);
            } else {
                logger.error("Mode Restriction: zero/negative probabilities for person " + person.getId());
                person.setModeRestriction(null);
            }
        }
    }
}

