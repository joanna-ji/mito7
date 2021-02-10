package de.tum.bgu.msm.modules.modeChoice;

import de.tum.bgu.msm.data.*;
import de.tum.bgu.msm.io.input.readers.CoefficientReader;
import de.tum.bgu.msm.modules.Module;
import de.tum.bgu.msm.resources.Resources;
import de.tum.bgu.msm.util.LogitTools;
import de.tum.bgu.msm.util.MitoUtil;
import org.apache.log4j.Logger;
import org.matsim.core.utils.collections.Tuple;

import java.nio.file.Path;
import java.util.*;

import static de.tum.bgu.msm.data.Mode.*;
import static de.tum.bgu.msm.data.Purpose.HBW;
import static de.tum.bgu.msm.data.Purpose.HBE;

public class DominantCommuteMode extends Module {

    private final static Logger logger = Logger.getLogger(ModeChoice.class);
    private final static EnumSet<Mode> MODES = EnumSet.of(autoDriver,autoPassenger,publicTransport,bicycle,walk);
    private final static Path dominantModeCoefPath = Resources.instance.getDominantCommuteModeCoefficients();

    private final EnumMap<Mode, Map<String, Double>> coefficients = new EnumMap<>(Mode.class);
    private final LogitTools logitTools = new LogitTools(Mode.class);
    private final List<Tuple<EnumSet<Mode>, Double>> nests;

    private int nonCommuters = 0;

    public DominantCommuteMode(DataSet dataSet) {
        super(dataSet);
        for(Mode mode : MODES) {
            coefficients.put(mode, new CoefficientReader(dataSet, mode, dominantModeCoefPath).readCoefficients());
        }
        nests = logitTools.identifyNests(coefficients);
    }

    @Override
    public void run() {
        logger.info(" Calculating dominant commute mode probabilities. Modes considered - 1. Auto driver, 2. Auto passenger, 3. Public Transport, 4. Bicycle, 5. Walk");
        dominantCommuteMode();
    }

    private void dominantCommuteMode() {

        for (MitoPerson person : dataSet.getModelledPersons().values()) {
            if (person.hasTripsForPurpose(HBW) || person.hasTripsForPurpose(HBE)) {
                chooseDominantCommuteMode(person, logitTools.getProbabilitiesNL(getUtilities(person), nests));
            } else {
                nonCommuters++;
            }
        }
        logger.info(nonCommuters + " non-commuters skipped");
    }

    private EnumMap<Mode, Double> getUtilities(MitoPerson person) {
        EnumMap<Mode, Double> utilities = new EnumMap(Mode.class);
        for(Mode mode : MODES) {
            utilities.put(mode, getPredictor(person, coefficients.get(mode)));
        }
        return utilities;
    }

    private double getPredictor(MitoPerson pp, Map<String, Double> coefficients) {
        double predictor = 0.;
        MitoHousehold hh = pp.getHousehold();

        // Intercept
        predictor += coefficients.get("INTERCEPT");

        // Household size
        int householdSize = hh.getHhSize();
        if (householdSize == 1) {
            predictor += coefficients.get("hh.size_1");
        } else if (householdSize == 2) {
            predictor += coefficients.get("hh.size_2");
        } else if (householdSize == 3) {
            predictor += coefficients.get("hh.size_3");
        } else if (householdSize == 4) {
            predictor += coefficients.get("hh.size_4");
        } else if (householdSize >= 5) {;
            predictor += coefficients.get("hh.size_5");
        }

        // Number of children in household
        int householdChildren = DataSet.getChildrenForHousehold(hh);
        if(householdChildren == 1) {
            predictor += coefficients.get("hh.children_1");
        } else if (householdChildren == 2) {
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

        // Autos per adult
        int householdAdults = hh.getHhSize() - householdChildren;
        double autosPerAdult = Math.min((double) hh.getAutos() / householdAdults , 1.);
        predictor += autosPerAdult * coefficients.get("hh.autosPerAdult");

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
            predictor += 0.;
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

        // Number of education trips
        int trips_HBE = pp.getTripsForPurpose(HBE).size();
        if (trips_HBE == 0) {
            predictor += 0;
        } else if (trips_HBE < 5) {
            predictor += coefficients.get("p.trips_HBE_1234");
        } else {
            predictor += coefficients.get("p.trips_HBE_5");
        }

        // Mean & coefficient of variation of mandatory trips
        List<MitoTrip> workTrips = pp.getTripsForPurpose(HBW);
        List<MitoTrip> educationTrips = pp.getTripsForPurpose(HBE);

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

            predictor += sqrtMean * coefficients.get("p.m_mode_km_T");
            predictor += coefficientOfVariation * coefficients.get("p.m_cv_km");
        }

        return predictor;
    }

    private void chooseDominantCommuteMode(MitoPerson person, EnumMap<Mode, Double> probabilities) {
        if (probabilities == null) {
            nonCommuters++;
            return;
        }

        double sum = MitoUtil.getSum(probabilities.values());
        if (Math.abs(sum - 1) > 0.01) {
            logger.warn("Mode probabilities don't add to 1 for person " + person.getId());
        }
        if (sum > 0) {
            final Mode select = MitoUtil.select(probabilities, MitoUtil.getRandomObject());
            person.setDominantCommuteMode(select);
        } else {
            logger.error("Zero/negative probabilities for person " + person.getId());
            person.setDominantCommuteMode(null);
        }
    }
}

