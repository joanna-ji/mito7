package de.tum.bgu.msm.modules.tripGeneration;

import de.tum.bgu.msm.data.*;
import de.tum.bgu.msm.io.input.readers.CoefficientReader;
import de.tum.bgu.msm.resources.Resources;
import de.tum.bgu.msm.util.MitoUtil;
import de.tum.bgu.msm.util.concurrent.RandomizableConcurrentFunction;
import org.apache.log4j.Logger;
import org.matsim.core.utils.collections.Tuple;
import umontreal.ssj.probdist.NegativeBinomialDist;

import java.util.*;

import static de.tum.bgu.msm.modules.tripGeneration.RawTripGenerator.TRIP_ID_COUNTER;

public class TripsByPurposeGeneratorHurdleModel extends RandomizableConcurrentFunction<Tuple<Purpose, Map<MitoPerson, List<MitoTrip>>>> implements TripsByPurposeGenerator {

    private static final Logger logger = Logger.getLogger(TripsByPurposeGeneratorHurdleModel.class);
    private Map<MitoPerson, List<MitoTrip>> tripsByPP = new HashMap<>();

    private final DataSet dataSet;
    private final Purpose purpose;

    private final Map<String, Double> zeroCoef;
    private final Map<String, Double> countCoef;

    protected TripsByPurposeGeneratorHurdleModel(DataSet dataSet, Purpose purpose) {
        super(MitoUtil.getRandomObject().nextLong());
        this.dataSet = dataSet;
        this.purpose = purpose;
        this.zeroCoef =
                new CoefficientReader(dataSet, purpose,
                        Resources.instance.getTripGenerationCoefficientsHurdleBinaryLogit()).readCoefficients();
        if(purpose.equals(Purpose.HBW) || purpose.equals(Purpose.HBE)) {
            this.countCoef =
                    new CoefficientReader(dataSet, purpose,
                            Resources.instance.getTripGenerationCoefficientsHurdleOrderedLogit()).readCoefficients();
        } else {
            this.countCoef =
                    new CoefficientReader(dataSet, purpose,
                            Resources.instance.getTripGenerationCoefficientsHurdleNegativeBinomial()).readCoefficients();
        }
    }

    @Override
    public Tuple<Purpose, Map<MitoPerson, List<MitoTrip>>> call() throws Exception {
        logger.info("  Generating trips for purpose " + purpose + " (multi-threaded)");

        for (MitoPerson person : dataSet.getModelledPersons().values()) {
            if(purpose.equals(Purpose.HBW) || purpose.equals(Purpose.HBE)) {
                generateTripsForPerson(person, polrEstimateTrips(person));
            } else {
                generateTripsForPerson(person, hurdleEstimateTrips(person));
            }
        }
        return new Tuple<>(purpose, tripsByPP);
    }

    private int polrEstimateTrips (MitoPerson pp) {
        double randomNumber = random.nextDouble();
        double binaryUtility = getPredictor(pp, zeroCoef);
        double phi = Math.exp(binaryUtility) / (1 + Math.exp(binaryUtility));
         double mu = getPredictor(pp, countCoef);

        double[] intercepts = new double[6];
        intercepts[0] = countCoef.get("1|2");
        intercepts[1] = countCoef.get("2|3");
        intercepts[2] = countCoef.get("3|4");
        intercepts[3] = countCoef.get("4|5");
        intercepts[4] = countCoef.get("5|6");
        intercepts[5] = countCoef.get("6|7");

        int i = 0;
        double cumProb = 0;
        double prob = 1 - phi;
        cumProb += prob;

        while(randomNumber > cumProb) {
            i++;
            if(i < 7) {
                prob = Math.exp(intercepts[i-1] - mu) / (1 + Math.exp(intercepts[i-1] - mu));
            } else {
                prob = 1;
            }
            if(i > 1) {
                prob -= Math.exp(intercepts[i-2] - mu) / (1 + Math.exp(intercepts[i-2] - mu));
            }
            cumProb += phi * prob;
        }
        return i;
    }

    private int hurdleEstimateTrips(MitoPerson pp) {
        double randomNumber = random.nextDouble();
        double binaryUtility = getPredictor(pp, zeroCoef);
        double phi = Math.exp(binaryUtility) / (1 + Math.exp(binaryUtility));
        double mu = Math.exp(getPredictor(pp, countCoef));
        double theta = countCoef.get("theta");

        NegativeBinomialDist nb = new NegativeBinomialDist(theta, theta / (theta + mu));

        double p0_zero = Math.log(phi);
        double p0_count = Math.log(1 - nb.cdf(0));
        double logphi = p0_zero - p0_count;

        int i = 0;
        double cumProb = 0;
        double prob = 1 - Math.exp(p0_zero);
        cumProb += prob;

        while(randomNumber > cumProb) {
            i++;
            prob = Math.exp(logphi + Math.log(nb.prob(i)));
            cumProb += prob;
        }
        return(i);
    }

    private double getPredictor(MitoPerson pp, Map<String, Double> coefficients) {
        MitoHousehold hh = pp.getHousehold();
        double predictor = 0.;

        // Intercept
        predictor += coefficients.get("(Intercept)");

        // Household size
        int householdSize = hh.getHhSize();
        if(householdSize == 1) {
            predictor += coefficients.get("hh.size_1");
        }
        else if(householdSize == 2) {
            predictor += coefficients.get("hh.size_2");
        }
        else if(householdSize == 3) {
            predictor += coefficients.get("hh.size_3");
        }
        else if(householdSize == 4) {
            predictor += coefficients.get("hh.size_4");
        }
        else  {
            assert(householdSize >= 5);
            predictor += coefficients.get("hh.size_5");
        }

        // Number of children in household
        int householdChildren = DataSet.getChildrenForHousehold(hh);
        if(householdChildren == 1) {
            predictor += coefficients.get("hh.children_1");
        }
        else if (householdChildren == 2) {
            predictor += coefficients.get("hh.children_2");
        }
        else if (householdChildren >= 3) {
            predictor += coefficients.get("hh.children_3");
        }

        // Household in urban region
        if(!(hh.getHomeZone().getAreaTypeR().equals(AreaTypes.RType.RURAL))) {
            predictor += coefficients.get("hh.urban");
        }

        // Household autos
        int householdAutos = hh.getAutos();
        if(householdAutos == 1) {
            predictor += coefficients.get("hh.cars_1");
        }
        else if(householdAutos == 2) {
            predictor += coefficients.get("hh.cars_2");
        }
        else if(householdAutos >= 3) {
            predictor += coefficients.get("hh.cars_3");
        }

        // Autos per adult
        int householdAdults = householdSize - householdChildren;
        double autosPerAdult = Math.min((double) hh.getAutos() / (double) householdAdults , 1.0);
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
            predictor += coefficients.get("p.age_gr_3");
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
        if (pp.getMitoGender().equals(MitoGender.FEMALE)) {
            predictor += coefficients.get("p.female");
        }

        // Has drivers Licence
        if (pp.hasDriversLicense()) {
            predictor += coefficients.get("p.driversLicense");
        }

        // Has bicycle
        if (pp.hasBicycle()) {
            predictor += coefficients.get("p.ownBicycle");
        }

        // Mito occupation Status
        MitoOccupationStatus occupationStatus = pp.getMitoOccupationStatus();
        if (occupationStatus.equals(MitoOccupationStatus.STUDENT)) {
            predictor += coefficients.get("p.occupationStatus_Student");
        } else if (occupationStatus.equals(MitoOccupationStatus.UNEMPLOYED)) {
            predictor += coefficients.get("p.occupationStatus_Unemployed");
        }

        // Number of work trips
        int workTrips = pp.getTripsForPurpose(Purpose.HBW).size();
        if (workTrips == 1) {
            predictor += coefficients.get("p.workTrips_1");
        } else if (workTrips == 2) {
            predictor += coefficients.get("p.workTrips_2");
        } else if (workTrips == 3) {
            predictor += coefficients.get("p.workTrips_3");
        } else if (workTrips == 4) {
            predictor += coefficients.get("p.workTrips_4");
        } else if (workTrips >= 5) {
            predictor += coefficients.get("p.workTrips_5");
        }

        // Number of education trips
        int eduTrips = pp.getTripsForPurpose(Purpose.HBE).size();
        if (eduTrips == 1) {
            predictor += coefficients.get("p.eduTrips_1");
        } else if (eduTrips == 2) {
            predictor += coefficients.get("p.eduTrips_2");
        } else if (eduTrips == 3) {
            predictor += coefficients.get("p.eduTrips_3");
        } else if (eduTrips == 4) {
            predictor += coefficients.get("p.eduTrips_4");
        } else if (eduTrips >= 5) {
            predictor += coefficients.get("p.eduTrips_5");
        }

        // Usual commute mode
        Mode dominantCommuteMode = pp.getDominantCommuteMode();
        if(dominantCommuteMode != null) {
            if (dominantCommuteMode.equals(Mode.autoDriver)) {
                predictor += coefficients.get("p.usualCommuteMode_carD");
            } else if (dominantCommuteMode.equals(Mode.autoPassenger)) {
                predictor += coefficients.get("p.usualCommuteMode_carP");
            } else if (dominantCommuteMode.equals(Mode.publicTransport)) {
                predictor += coefficients.get("p.usualCommuteMode_PT");
            } else if (dominantCommuteMode.equals(Mode.bicycle)) {
                predictor += coefficients.get("p.usualCommuteMode_cycle");
            } else if (dominantCommuteMode.equals(Mode.walk)) {
                predictor += coefficients.get("p.usualCommuteMode_walk");
            }
        }

        // Commute distance
        if(pp.getOccupation() != null) {
            int homeZoneId = pp.getHousehold().getZoneId();
            int occupationZoneId = pp.getOccupation().getZoneId();
            double commuteDistance = dataSet.getTravelDistancesNMT().getTravelDistance(homeZoneId,occupationZoneId);
            if(commuteDistance == 0) {
                logger.info("Commute distance from zone " + homeZoneId + " to zone " + occupationZoneId + "is Zero");
                commuteDistance = 0.25;
            }
            predictor += Math.log(commuteDistance) * coefficients.get("p.m_mode_km_T");
        }


        return predictor;
    }

    private void generateTripsForPerson(MitoPerson pp, int numberOfTrips) {

        List<MitoTrip> trips = new ArrayList<>();
        for (int i = 0; i < numberOfTrips; i++) {
            MitoTrip trip = new MitoTrip(TRIP_ID_COUNTER.incrementAndGet(), purpose);
            trip.setPerson(pp);
            if (trip != null) {
                trips.add(trip);
            }
        }
        tripsByPP.put(pp, trips);
    }
}
