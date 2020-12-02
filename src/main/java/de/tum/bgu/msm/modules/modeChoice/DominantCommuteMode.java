package de.tum.bgu.msm.modules.modeChoice;

import de.tum.bgu.msm.data.*;
import de.tum.bgu.msm.io.input.readers.CoefficientReader;
import de.tum.bgu.msm.modules.Module;
import de.tum.bgu.msm.resources.Resources;
import de.tum.bgu.msm.util.MitoUtil;
import de.tum.bgu.msm.util.concurrent.ConcurrentExecutor;
import de.tum.bgu.msm.util.concurrent.RandomizableConcurrentFunction;
import org.apache.log4j.Logger;
import org.matsim.core.utils.collections.Tuple;

import java.nio.file.Path;
import java.util.*;

import static de.tum.bgu.msm.data.Mode.*;

public class DominantCommuteMode extends Module {

    private final static Logger logger = Logger.getLogger(ModeChoice.class);

    public DominantCommuteMode(DataSet dataSet) {
        super(dataSet);
    }

    @Override
    public void run() {
        logger.info(" Calculating dominant commute mode probabilities. Modes considered - 1. Auto driver, 2. Auto passenger, 3. Public Transport, 4. Bicycle, 5. Walk");
        dominantCommuteMode();
    }

    private void dominantCommuteMode() {
        ConcurrentExecutor<Void> executor = ConcurrentExecutor.fixedPoolService(Purpose.values().length);
        executor.addTaskToQueue(new dominantCommuteModeChoice(dataSet));
        executor.execute();
    }

    static class dominantCommuteModeChoice extends RandomizableConcurrentFunction<Void> {

        private final DataSet dataSet;
        private int nonCommuters;

        private final static Path dominantModeCoefPath = Resources.instance.getDominantCommuteModeCoefficients();

        private final Map<String, Double> autoDriverCoef;
        private final Map<String, Double> autoPassengerCoef;
        private final Map<String, Double> publicTransportCoef;
        private final Map<String, Double> cycleCoef;
        private final Map<String, Double> walkCoef;

        dominantCommuteModeChoice(DataSet dataSet) {
            super(MitoUtil.getRandomObject().nextLong());
            this.dataSet = dataSet;
            this.autoDriverCoef = new CoefficientReader(dataSet, autoDriver, dominantModeCoefPath).readCoefficients();
            this.autoPassengerCoef = new CoefficientReader(dataSet, autoPassenger, dominantModeCoefPath).readCoefficients();
            this.publicTransportCoef = new CoefficientReader(dataSet, publicTransport, dominantModeCoefPath).readCoefficients();
            this.cycleCoef = new CoefficientReader(dataSet, bicycle, dominantModeCoefPath).readCoefficients();
            this.walkCoef = new CoefficientReader(dataSet, walk, dominantModeCoefPath).readCoefficients();
        }

        @Override
        public Void call() {
            nonCommuters = 0;
            try {
                for (MitoPerson person : dataSet.getPersons().values()) {
                    if (person.getTripsForPurpose(Purpose.HBW).size() + person.getTripsForPurpose(Purpose.HBE).size() == 0) {
                        nonCommuters++;
                    } else {
                        chooseDominantCommuteMode(person, calculateProbabilities(calculateUtilities(person)));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            logger.info(nonCommuters + " non-commuters skipped");
            return null;
        }

        private EnumMap<Mode, Double> calculateUtilities(MitoPerson person) {
            EnumMap<Mode, Double> utilities = new EnumMap(Mode.class);

            utilities.put(autoDriver , getResponse(person, autoDriverCoef));
            utilities.put(autoPassenger , getResponse(person, autoPassengerCoef));
            utilities.put(publicTransport , getResponse(person, publicTransportCoef));
            utilities.put(bicycle , getResponse(person, cycleCoef));
            utilities.put(walk , getResponse(person, walkCoef));

            return (utilities);
        }

        private EnumMap<Mode, Double> calculateProbabilities(EnumMap<Mode, Double> utilities) {

            List<Tuple<EnumSet<Mode>, Double>> nests = new ArrayList<>();
            nests.add(new Tuple<>(EnumSet.of(autoDriver, autoPassenger), 0.455136));
            nests.add(new Tuple<>(EnumSet.of(publicTransport), 1.));
            nests.add(new Tuple<>(EnumSet.of(bicycle, walk), 0.767650));

            return calculateNestedLogitProbabilities(utilities, nests);
        }

        private double getResponse(MitoPerson pp, Map<String, Double> coefficients) {
            double response = 0.;
            MitoHousehold hh = pp.getHousehold();

            // Household size
            int householdSize = hh.getHhSize();
            response += Math.min(householdSize,5) * coefficients.get("hh.size");

            // Household children
            int householdChildren = DataSet.getChildrenForHousehold(hh);
            response += Math.min(householdChildren,3) * coefficients.get("hh.children");

            // Autos per adult
            int householdAdults = householdSize - householdChildren;
            double autosPerAdult = Math.min((double) hh.getAutos() / (double) householdAdults , 1.0);
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

            // Is the place of work or study close to PT?
            if(pp.getOccupation() != null) {
                int occupationZoneId = pp.getOccupation().getZoneId();
                double occupationDistanceToPT = dataSet.getZones().get(occupationZoneId).getDistanceToNearestRailStop();
                double occupationWalkToPT = occupationDistanceToPT * (60 / 4.8);
                if(occupationWalkToPT <= 20) {
                    response += coefficients.get("p.workPT_12");
                }
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

            // Mean commute trip length & coefficient of variation
            List<MitoTrip> workTrips = pp.getTripsForPurpose(Purpose.HBW);
            List<MitoTrip> educationTrips = pp.getTripsForPurpose(Purpose.HBE);

            List<MitoTrip> commuteTrips = new ArrayList(workTrips);
            commuteTrips.addAll(educationTrips);

            int educationTripCount = educationTrips.size();
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

                response += sqrtMean * coefficients.get("p.m_mode_km_T");
                response += coefficientOfVariation * coefficients.get("p.m_cv_km");

                if(educationTripCount == 1) {
                    response += coefficients.get("p.eduTrips_1");
                } else if (educationTripCount == 2) {
                    response += coefficients.get("p.eduTrips_2");
                } else if (educationTripCount == 3) {
                    response += coefficients.get("p.eduTrips_3");
                } else if (educationTripCount == 4) {
                    response += coefficients.get("p.eduTrips_4");
                } else if (educationTripCount >= 5) {
                    response += coefficients.get("p.eduTrips_5");
                }
            }
            return response;
        }

        private EnumMap<Mode, Double> calculateNestedLogitProbabilities(EnumMap<Mode, Double> utilities,
                                                                        List<Tuple<EnumSet<Mode>,Double>> nests) {

            EnumMap<Mode, Double> expModeUtils = new EnumMap(Mode.class);
            EnumMap<Mode, Double> expNestSums = new EnumMap(Mode.class);
            EnumMap<Mode, Double> expNestUtils = new EnumMap(Mode.class);
            EnumMap<Mode, Double> probabilities = new EnumMap(Mode.class);
            double expSumRoot = 0;

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

        private void chooseDominantCommuteMode(MitoPerson person, EnumMap<Mode, Double> probabilities) {
            if (probabilities == null) {
                nonCommuters++;
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
    }
}

