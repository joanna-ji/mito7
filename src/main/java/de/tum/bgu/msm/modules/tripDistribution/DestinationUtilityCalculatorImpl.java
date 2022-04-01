package de.tum.bgu.msm.modules.tripDistribution;

import de.tum.bgu.msm.data.Purpose;

public class DestinationUtilityCalculatorImpl implements DestinationUtilityCalculator {

    private final static double[] TRAVEL_DISTANCE_PARAM_HBW = {-0.02752, -0.02602, -0.01710};
    private final static double IMPEDANCE_PARAM_HBW = 9;

    private final static double TRAVEL_DISTANCE_PARAM_HBE = -0.005791;
    private final static double IMPEDANCE_PARAM_HBE = 28.3;

    private final static double[] TRAVEL_DISTANCE_PARAM_HBS = {-0.04593, -0.03493, -0.03075};
    private final static double IMPEDANCE_PARAM_HBS = 14.5;

    private final static double[] TRAVEL_DISTANCE_PARAM_HBR = {-0.008269, -0.008626, -0.008143};
    private final static double IMPEDANCE_PARAM_HBR = 20;

    private final static double[] TRAVEL_DISTANCE_PARAM_HBO = {-0.004626, -0.003937, -0.003739};
    private final static double IMPEDANCE_PARAM_HBO = 53;

    private final static double[] TRAVEL_DISTANCE_PARAM_RRT = {-0.058139, -0.025304, -0.010405};
    private final static double IMPEDANCE_PARAM_RRT = 14;

    private final static double[] TRAVEL_DISTANCE_PARAM_NHBW = {-0.016240, -0.012459, -0.009441}; // -0.012747;
    private final static double IMPEDANCE_PARAM_NHBW = 15.1;

    private final static double[] TRAVEL_DISTANCE_PARAM_NHBO = {-0.012754, -0.011711, -0.010579}; // -0.0130997;
    private final static double IMPEDANCE_PARAM_NHBO = 20;

    private final double distanceParam;
    private final double impedanceParam;

    DestinationUtilityCalculatorImpl(Purpose purpose, Integer index) {

        if (purpose.equals(TripDistribution.calibrationPurpose)) {
            distanceParam = TripDistribution.calibrationDistanceParams[(index == null) ? 0 : index];
            impedanceParam = TripDistribution.calibrationImpedanceParam;
        }
        else switch (purpose) {
            case HBW:
                distanceParam = TRAVEL_DISTANCE_PARAM_HBW[index];
                impedanceParam = IMPEDANCE_PARAM_HBW;
                break;
            case HBE:
                distanceParam = TRAVEL_DISTANCE_PARAM_HBE;
                impedanceParam = IMPEDANCE_PARAM_HBE;
                break;
            case HBS:
                distanceParam = TRAVEL_DISTANCE_PARAM_HBS[index];
                impedanceParam = IMPEDANCE_PARAM_HBS;
                break;
            case HBR:
                distanceParam = TRAVEL_DISTANCE_PARAM_HBR[index];
                impedanceParam = IMPEDANCE_PARAM_HBR;
                break;
            case HBO:
                distanceParam = TRAVEL_DISTANCE_PARAM_HBO[index];
                impedanceParam = IMPEDANCE_PARAM_HBO;
                break;
            case RRT:
                distanceParam = TRAVEL_DISTANCE_PARAM_RRT[index];
                impedanceParam = IMPEDANCE_PARAM_RRT;
                break;
            case NHBW:
                distanceParam = TRAVEL_DISTANCE_PARAM_NHBW[index];
                impedanceParam = IMPEDANCE_PARAM_NHBW;
                break;
            case NHBO:
                distanceParam = TRAVEL_DISTANCE_PARAM_NHBO[index];
                impedanceParam = IMPEDANCE_PARAM_NHBO;
                break;
            case AIRPORT:
            default:
                throw new RuntimeException("not implemented!");
        }
    }

    @Override
    public double calculateUtility(double attraction, double travelDistance) {
        if(attraction == 0) {
            return 0.;
        }
        double impedance = impedanceParam * Math.exp(distanceParam * travelDistance);
        return Math.exp(impedance) * attraction;
    }
}
