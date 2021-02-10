package de.tum.bgu.msm.modules.modeChoice;

import de.tum.bgu.msm.data.*;
import de.tum.bgu.msm.io.input.readers.CoefficientReader;
import de.tum.bgu.msm.modules.Module;
import de.tum.bgu.msm.resources.Resources;
import de.tum.bgu.msm.util.LogitTools;
import de.tum.bgu.msm.util.MitoUtil;
import org.apache.log4j.Logger;

import java.nio.file.Path;
import java.util.*;

import static de.tum.bgu.msm.data.ModeRestriction.*;


public class ModeRestrictionChoice extends Module {

    private final static Logger logger = Logger.getLogger(ModeChoice.class);
    private final static Path modeRestrictionCoefPath = Resources.instance.getModeRestrictionCoefficients();

    private final EnumMap<ModeRestriction, Map<String, Double>> coefficients = new EnumMap<>(ModeRestriction.class);
    private final LogitTools logitTools = new LogitTools(ModeRestriction.class);
    private int nonMobilePersons;


    public ModeRestrictionChoice(DataSet dataSet) {
        super(dataSet);
        for(ModeRestriction modeRestriction : ModeRestriction.values()) {
            coefficients.put(modeRestriction, new CoefficientReader(dataSet, modeRestriction, modeRestrictionCoefPath).readCoefficients());
        }
    }

    @Override
    public void run() {
        logger.info(" Calculating mode restriction.");
        modeRestriction();
    }

    private void modeRestriction() {

        for (MitoPerson person : dataSet.getModelledPersons().values()) {
            if(person.getTrips().size() == 0) {
                nonMobilePersons++;
            } else {
                chooseModeRestriction(person, logitTools.getProbabilitiesMNL(getUtilities(person)));
            }
        }
        logger.info(nonMobilePersons + " non-mobile persons skipped");
    }

    private EnumMap<ModeRestriction, Double> getUtilities(MitoPerson person) {

        EnumMap<ModeRestriction, Double> utilities = new EnumMap(ModeRestriction.class);

        EnumSet<ModeRestriction> availableChoices = determineAvailability(person);
        for(ModeRestriction modeRestriction : availableChoices) {
            utilities.put(modeRestriction, getPredictor(person, coefficients.get(modeRestriction)));
        }

        return (utilities);
    }

    private EnumSet<ModeRestriction> determineAvailability(MitoPerson person) {

        EnumSet<ModeRestriction> availableChoices = EnumSet.noneOf(ModeRestriction.class);
        Mode dominantCommuteMode = person.getDominantCommuteMode();

        boolean commuter = dominantCommuteMode != null;

        EnumSet<ModeRestriction> possibleOptions;
        if(person.hasTripsForPurpose(Purpose.RRT)) {
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

    private double getPredictor(MitoPerson pp, Map<String, Double> coefficients) {
        double predictor = 0.;
        MitoHousehold hh = pp.getHousehold();

        // Intercept
        predictor += coefficients.get("INTERCEPT");

        // Number of children in household
        int householdChildren = DataSet.getChildrenForHousehold(hh);
        if(householdChildren == 2) {
            predictor += coefficients.get("hh.children_2");
        } else if (householdChildren >= 3) {
            predictor += coefficients.get("hh.children_3");
        }

        // Economic status
        int householdEconomicStatus = hh.getEconomicStatus();
        if (householdEconomicStatus == 2) {
            predictor += coefficients.get("hh.econStatus_2");
        } else if (householdEconomicStatus == 3) {
            predictor += coefficients.get("hh.econStatus_3");
        } else if (householdEconomicStatus == 4) {
            predictor += coefficients.get("hh.econStatus_4");
        }

        // Household in urban region
        if(!(hh.getHomeZone().getAreaTypeR().equals(AreaTypes.RType.RURAL))) {
            predictor += coefficients.get("hh.urban");
        }

        // Autos
        int householdAutos = hh.getAutos();
        if (householdAutos == 1) {
            predictor += coefficients.get("hh.cars_1");
        } else if (householdAutos == 2) {
            predictor += coefficients.get("hh.cars_2");
        } else if (householdAutos >= 3) {
            predictor += coefficients.get("hh.cars_3");
        }

        // Autos per adult
        int householdAdults = hh.getHhSize() - householdChildren;
        double autosPerAdult = Math.min((double) householdAutos / householdAdults , 1.);
        predictor += autosPerAdult * coefficients.get("hh.autosPerAdult");

        // Is home close to PT?
        int homeZoneId = hh.getHomeZone().getId();
        double homeDistanceToPT = dataSet.getZones().get(homeZoneId).getDistanceToNearestRailStop();
        double homeWalkToPT = homeDistanceToPT * (60 / 4.8);
        if(homeWalkToPT <= 20) {
            predictor += coefficients.get("hh.homePT");
        }

        // Age
        int age = pp.getAge();
        if (age <= 18) {
            predictor += coefficients.get("p.age_gr_1");
        }
        else if (age <= 29) {
            predictor += coefficients.get("p.age_gr_2");
        }
        else if (age <= 49) {
            predictor += 0.;
        }
        else if (age <= 59) {
            predictor += coefficients.get("p.age_gr_4");
        }
        else if (age <= 69) {
            predictor += coefficients.get("p.age_gr_5");
        }
        else {
            predictor += coefficients.get("p.age_gr_6");
        }

        // Female
        if(pp.getMitoGender().equals(MitoGender.FEMALE)) {
            predictor += coefficients.get("p.female");
        }

        // Has drivers Licence
        if(pp.hasDriversLicense()) {
            predictor += coefficients.get("p.driversLicence");
        }

        // Has bicycle
        if(pp.hasBicycle()) {
            predictor += coefficients.get("p.ownBicycle");
        }

        // Trip distance mean, max, cv
        List<MitoTrip> trips = pp.getTrips();
        int tripCount = trips.size();
        ArrayList<Double> tripDistances = new ArrayList<>();
        double sum = 0;
        double sumSquaredDiff = 0;
        double maxTripDistance = Double.MIN_VALUE;
        double minTripDistance = Double.MAX_VALUE;

        for (MitoTrip trip : trips) {

            Location origin = trip.getTripOrigin();
            Location destination = trip.getTripDestination();

            if(origin == null || destination == null) {
                logger.error("Null trip destination tripID: " + trip.getId() + " personID: " + pp.getId());
            }

            double tripDistance = dataSet.getTravelDistancesNMT().
                    getTravelDistance(origin.getZoneId(), destination.getZoneId());

            maxTripDistance = Math.max(maxTripDistance, tripDistance);
            minTripDistance = Math.min(minTripDistance, tripDistance);
            tripDistances.add(tripDistance);
            sum += tripDistance;
        }
        double mean = sum / tripCount;
        for (Double s : tripDistances) {
            sumSquaredDiff += Math.pow(s - mean , 2);
        }
        double coefficientOfVariation = Math.sqrt(sumSquaredDiff / tripCount) / mean;

        predictor += Math.log(minTripDistance) * coefficients.get("p.min_km_T");
        predictor += Math.log(maxTripDistance) * coefficients.get("p.max_km_T");
        predictor += coefficientOfVariation * coefficients.get("p.cv_km");

        // Number of trips by purpose
        predictor += Math.sqrt(pp.getTripsForPurpose(Purpose.HBW).size()) * coefficients.get("p.trips_HBW_T");
        predictor += Math.sqrt(pp.getTripsForPurpose(Purpose.HBE).size()) * coefficients.get("p.trips_HBE_T");
        predictor += Math.sqrt(pp.getTripsForPurpose(Purpose.HBS).size()) * coefficients.get("p.trips_HBS_T");
        predictor += Math.sqrt(pp.getTripsForPurpose(Purpose.HBR).size()) * coefficients.get("p.trips_HBR_T");
        predictor += Math.sqrt(pp.getTripsForPurpose(Purpose.HBO).size()) * coefficients.get("p.trips_HBO_T");
        predictor += Math.sqrt(pp.getTripsForPurpose(Purpose.NHBO).size()) * coefficients.get("p.trips_NHBO_T");
        if(pp.hasTripsForPurpose(Purpose.RRT)) predictor += coefficients.get("p.isMobile_RRT");



        // Usual commute mode
        Mode dominantCommuteMode = pp.getDominantCommuteMode();
        if(dominantCommuteMode != null) {
            if (dominantCommuteMode.equals(Mode.autoDriver)) {
                predictor += 0.;
            } else if (dominantCommuteMode.equals(Mode.autoPassenger)) {
                predictor += 0.;
            } else if (dominantCommuteMode.equals(Mode.publicTransport)) {
                predictor += coefficients.get("p.usualCommuteMode_PT");
            } else if (dominantCommuteMode.equals(Mode.bicycle)) {
                predictor += coefficients.get("p.usualCommuteMode_cycle");
            } else if (dominantCommuteMode.equals(Mode.walk)) {
                predictor += coefficients.get("p.usualCommuteMode_walk");
            }
        }

        return predictor;
    }

    private void chooseModeRestriction(MitoPerson person, EnumMap<ModeRestriction, Double> probabilities) {
        double sum = MitoUtil.getSum(probabilities.values());
        if (Math.abs(sum - 1) > 0.1) {
            logger.warn("Mode probabilities don't add to 1 for person " + person.getId());
        }
        if (sum > 0) {
            final ModeRestriction select = MitoUtil.select(probabilities, MitoUtil.getRandomObject());
            person.setModeRestriction(select);
        } else {
            logger.error("Zero/negative probabilities for person " + person.getId());
            person.setModeRestriction(null);
        }
    }
}