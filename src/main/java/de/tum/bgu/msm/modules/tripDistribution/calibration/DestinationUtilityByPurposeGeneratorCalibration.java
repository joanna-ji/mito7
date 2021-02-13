package de.tum.bgu.msm.modules.tripDistribution.calibration;

import com.google.common.math.LongMath;
import de.tum.bgu.msm.data.DataSet;
import de.tum.bgu.msm.data.MitoZone;
import de.tum.bgu.msm.data.Purpose;
import de.tum.bgu.msm.data.travelDistances.TravelDistances;
import de.tum.bgu.msm.modules.tripDistribution.DestinationUtilityCalculator;
import de.tum.bgu.msm.modules.tripDistribution.DestinationUtilityCalculatorImpl;
import de.tum.bgu.msm.util.matrices.IndexedDoubleMatrix2D;
import org.apache.log4j.Logger;
import org.matsim.core.utils.collections.Tuple;

import java.util.Map;
import java.util.concurrent.Callable;

public class DestinationUtilityByPurposeGeneratorCalibration implements Callable<Tuple<Integer, IndexedDoubleMatrix2D>> {

    private final static Logger logger = Logger.getLogger(DestinationUtilityByPurposeGeneratorCalibration.class);

    private final Integer i;
    private final Purpose purpose;
    private final Map<Integer, MitoZone> zones;
    private final TravelDistances travelDistances;
    private final double impedanceParam;
    private final double distanceParam;

    DestinationUtilityByPurposeGeneratorCalibration(Purpose purpose, int i, DataSet dataSet, double impedanceParam, double distanceParam) {
        this.purpose = purpose;
        this.i = i;
        this.zones = dataSet.getZones();
        this.travelDistances = dataSet.getTravelDistancesNMT();
        this.impedanceParam = impedanceParam;
        this.distanceParam = distanceParam;
    }

    @Override
    public Tuple<Integer, IndexedDoubleMatrix2D> call() {
        final IndexedDoubleMatrix2D utilityMatrix = new IndexedDoubleMatrix2D(zones.values(), zones.values());
        long counter = 0;
        for (MitoZone origin : zones.values()) {
            for (MitoZone destination : zones.values()) {
                final double utility =  calculateUtility(destination.getTripAttraction(purpose),
                        travelDistances.getTravelDistance(origin.getId(), destination.getId()));
                if (Double.isInfinite(utility) || Double.isNaN(utility)) {
                    throw new RuntimeException(utility + " utility calculated! Please check calculation!" +
                            " Origin: " + origin + " | Destination: " + destination + " | Distance: "
                            + travelDistances.getTravelDistance(origin.getId(), destination.getId()) +
                            " | Purpose: " + purpose + " | attraction rate: " + destination.getTripAttraction(purpose));
                }
                utilityMatrix.setIndexed(origin.getId(), destination.getId(), utility);
                counter++;
            }
        }
//        logger.info("Utility matrix for purpose " + purpose + " done.");
        return new Tuple<>(i, utilityMatrix);
    }

    public double calculateUtility(double attraction, double travelDistance) {
        if(attraction == 0) {
            return 0.;
        }
        double impedance = impedanceParam * Math.exp(distanceParam * travelDistance);
        return Math.exp(impedance) * attraction;
    }
}
