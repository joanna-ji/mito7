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
import static de.tum.bgu.msm.data.Purpose.HBE;
import static de.tum.bgu.msm.data.Purpose.HBW;


public class ModeRestrictionChoice extends Module {

    private final static Logger logger = Logger.getLogger(ModeRestrictionChoice.class);
    private final static Path modeRestrictionCoefficientsPath = Resources.instance.getModeRestrictionCoefficients();
    private final static Path modeRestrictionConstantsPath = Resources.instance.getModeRestrictionConstants();
    private final static String[] MODES = {"auto", "publicTransport", "walk", "bicycle"};

    private final Map<String, Map<String, Double>> coefficients = new HashMap<>();
    private final EnumMap<ModeRestriction, Double> constants = new EnumMap<>(ModeRestriction.class);
    private final LogitTools<ModeRestriction> logitTools = new LogitTools<>(ModeRestriction.class);

    private int nonMobilePersons;


    public ModeRestrictionChoice(DataSet dataSet) {
        super(dataSet);
        Map<String, Double> constantsAsStrings = new CoefficientReader(dataSet, "constant", modeRestrictionConstantsPath).readCoefficients();
        for (ModeRestriction modeRestriction : ModeRestriction.values()) {
            constants.put(modeRestriction, constantsAsStrings.get(modeRestriction.toString()));
        }
        for(String mode : MODES) {
            coefficients.put(mode, new CoefficientReader(dataSet, mode, modeRestrictionCoefficientsPath).readCoefficients());
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

        EnumMap<ModeRestriction, Double> utilities = new EnumMap<>(ModeRestriction.class);
        EnumSet<ModeRestriction> availableChoices = EnumSet.allOf(ModeRestriction.class);
        if(person.hasTripsForPurpose(Purpose.RRT)) {
            availableChoices.removeAll(EnumSet.of(Auto, AutoPt, Pt));
        }

        for(ModeRestriction modeRestriction : availableChoices) {
            double utility = constants.get(modeRestriction);
            String modeRestrictionString = modeRestriction.toString();
            if(modeRestrictionString.contains("Auto"))  utility += getPredictor(person, coefficients.get("auto"));
            if(modeRestrictionString.contains("Pt"))    utility += getPredictor(person, coefficients.get("publicTransport"));
            if(modeRestrictionString.contains("Cycle")) utility += getPredictor(person, coefficients.get("bicycle"));
            if(modeRestrictionString.contains("Walk"))  utility += getPredictor(person, coefficients.get("walk"));
            utilities.put(modeRestriction, utility);
        }
        return (utilities);
    }

    private double getPredictor(MitoPerson pp, Map<String, Double> coefficients) {
        double predictor = 0.;
        MitoHousehold hh = pp.getHousehold();

        // Number of adults and children
        int householdChildren = DataSet.getChildrenForHousehold(hh);
        int householdAdults = hh.getHhSize() - householdChildren;
        double householdSizeAdj = householdAdults + 0.5*householdChildren;

        // Intercept
        predictor += coefficients.get("INTERCEPT");

        // Economic status (MOP version)
        double econStatusMop = hh.getMonthlyIncome_EUR() / householdSizeAdj;
        if (econStatusMop > 800) {
            predictor += coefficients.get("hh.econStatus_2");
        } else if (econStatusMop > 1600) {
            predictor += coefficients.get("hh.econStatus_3");
        } else if (econStatusMop > 2400) {
            predictor += coefficients.get("hh.econStatus_4");
        }

        // Household in urban region
        if(!(hh.getHomeZone().getAreaTypeR().equals(AreaTypes.RType.RURAL))) {
            predictor += coefficients.get("hh.urban");
        }

        // Is home close to PT?
        int homeZoneId = hh.getHomeZone().getId();
        double homeDistanceToPT = dataSet.getZones().get(homeZoneId).getDistanceToNearestRailStop();
        double homeWalkToPT = homeDistanceToPT * (60 / 4.8);
        if(homeWalkToPT <= 20) {
            predictor += coefficients.get("hh.homePT");
        }

        // Number of children in household
        if(householdChildren == 1) {
            predictor += coefficients.get("hh.children_1");
        } else if (householdChildren == 2) {
            predictor += coefficients.get("hh.children_2");
        } else if (householdChildren >= 3) {
            predictor += coefficients.get("hh.children_3");
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
        double autosPerAdult = Math.min((double) householdAutos / householdAdults , 1.);
        predictor += autosPerAdult * coefficients.get("hh.autosPerAdult");

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
            predictor += coefficients.get("p.driversLicense");
        }

        // Has bicycle
        if(pp.hasBicycle()) {
            predictor += coefficients.get("p.ownBicycle");
        }

        // Mito occupation Status
        MitoOccupationStatus occupationStatus = pp.getMitoOccupationStatus();
        if (occupationStatus.equals(MitoOccupationStatus.STUDENT)) {
            predictor += coefficients.get("p.occupationStatus_Student");
        } else if (occupationStatus.equals(MitoOccupationStatus.UNEMPLOYED)) {
            predictor += coefficients.get("p.occupationStatus_Unemployed");
        }

        // Is the place of work or study close to PT?
        if(pp.getOccupation() != null) {
            int occupationZoneId = pp.getOccupation().getZoneId();
            double occupationDistanceToPT = dataSet.getZones().get(occupationZoneId).getDistanceToNearestRailStop();
            double occupationWalkToPT = occupationDistanceToPT * (60 / 4.8);
            if(occupationWalkToPT <= 20) {
                predictor += coefficients.get("p.workPT_12");
            }
        }

        // RRT trips
        if(pp.hasTripsForPurpose(Purpose.RRT)) {
            predictor += coefficients.get("p.isMobile_RRT");
        }

        // Work trips
        if(pp.hasTripsForPurpose(HBW)) {
            double meanWorkKm = pp.getTripsForPurpose(HBW).stream().
                    mapToDouble(t -> dataSet.getTravelDistancesNMT().
                            getTravelDistance(homeZoneId, t.getTripDestination().getZoneId())).average().getAsDouble();
            predictor += coefficients.get("p.isMobile_HBW") + Math.log(meanWorkKm) * coefficients.get("p.log_km_mean_HBW");
        }

        // Education trips
        if(pp.hasTripsForPurpose(HBE)) {
            double meanEduKm = pp.getTripsForPurpose(HBE).stream().
                    mapToDouble(t -> dataSet.getTravelDistancesNMT().
                            getTravelDistance(homeZoneId, t.getTripDestination().getZoneId())).average().getAsDouble();
            predictor += coefficients.get("p.isMobile_HBE") + Math.log(meanEduKm) * coefficients.get("p.log_km_mean_HBE");
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