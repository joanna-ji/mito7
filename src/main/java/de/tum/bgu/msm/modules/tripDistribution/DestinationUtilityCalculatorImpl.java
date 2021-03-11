package de.tum.bgu.msm.modules.tripDistribution;

import de.tum.bgu.msm.data.Purpose;

public class DestinationUtilityCalculatorImpl implements DestinationUtilityCalculator {

    private final static double[] TRAVEL_DISTANCE_PARAM_HBW = {-0.03404, -0.02821, -0.01712};
    private final static double IMPEDANCE_PARAM_HBW = 9;

    private final static double TRAVEL_DISTANCE_PARAM_HBE = -0.006599;
    private final static double IMPEDANCE_PARAM_HBE = 28.3;

    private final static double[] TRAVEL_DISTANCE_PARAM_HBS = {-0.08805, -0.03948, -0.03362};
    private final static double IMPEDANCE_PARAM_HBS = 14.5;

    private final static double[] TRAVEL_DISTANCE_PARAM_HBR = {-0.011585, -0.010204, -0.0096598};
    private final static double IMPEDANCE_PARAM_HBR = 20;

    private final static double[] TRAVEL_DISTANCE_PARAM_HBO = {-0.006823, -0.004451, -0.004141};
    private final static double IMPEDANCE_PARAM_HBO = 53;

    private final static double TRAVEL_DISTANCE_PARAM_RRT = -0.05204;
    private final static double IMPEDANCE_PARAM_RRT = 12;

    private final static double[] TRAVEL_DISTANCE_PARAM_NHBW = {-0.027959,  -0.015731, -0.010706}; // -0.012747;
    private final static double IMPEDANCE_PARAM_NHBW = 15.1;

    private final static double[] TRAVEL_DISTANCE_PARAM_NHBO = {-0.022910, -0.013904, -0.011814}; // -0.0130997;
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
                distanceParam = TRAVEL_DISTANCE_PARAM_RRT;
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
