package de.tum.bgu.msm.modules.tripDistribution.calibration;

import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.AtomicDouble;
import de.tum.bgu.msm.data.*;
import de.tum.bgu.msm.data.travelDistances.TravelDistances;
import de.tum.bgu.msm.modules.Module;
import de.tum.bgu.msm.modules.tripDistribution.destinationChooser.HbsHbrHboDistribution;
import de.tum.bgu.msm.util.concurrent.ConcurrentExecutor;
import de.tum.bgu.msm.util.matrices.IndexedDoubleMatrix2D;
import org.apache.log4j.Logger;
import org.matsim.core.utils.collections.Tuple;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import static de.tum.bgu.msm.data.Purpose.*;

/**
 * @author Nico
 */
public final class DiscretionaryTripDistributionCalibration extends Module {

    public final static AtomicInteger distributedTripsCounter = new AtomicInteger(0);
    public final static AtomicDouble distributedDistanceSum = new AtomicDouble(0.);
    public final static AtomicDouble idealBudgetSum = new AtomicDouble(0.);
    public final static AtomicDouble actualBudgetSum = new AtomicDouble(0.);
    public final static AtomicInteger failedTripsCounter = new AtomicInteger(0);

    private final static Purpose PURPOSE = HBS;
    private final static EnumSet<Purpose> PURPOSES = EnumSet.of(PURPOSE);
    private final double referenceMean = 4.855919;

    private final double impedanceParam = 14.5;
    private double distanceParam = -0.0355;

    private EnumMap<Purpose, IndexedDoubleMatrix2D> utilityMatrices = new EnumMap<>(Purpose.class);

    private final static Logger logger = Logger.getLogger(DiscretionaryTripDistributionCalibration.class);

    public DiscretionaryTripDistributionCalibration(DataSet dataSet) {
        super(dataSet);
    }

    @Override
    public void run() {

        double adjustment;
        do {
            logger.info("Destination choice utility matrices...");
            buildMatrices();

            logger.info("Distributing trips for households...");
            distributeTrips();

            logger.info("Updating distanceParam...");
            adjustment = calculateAdjustment();
            distanceParam *= adjustment;

            logger.info("New distance param = " + distanceParam);
            distributedTripsCounter.set(0);
            distributedDistanceSum.set(0.0);
            failedTripsCounter.set(0);
            idealBudgetSum.set(0.);
            actualBudgetSum.set(0.);
        } while (Math.abs(adjustment - 1) > 0.01);
    }

    private void buildMatrices() {
        List<Callable<Tuple<Purpose,IndexedDoubleMatrix2D>>> utilityCalcTasks = new ArrayList<>();
        for (Purpose purpose : PURPOSES) {
            utilityCalcTasks.add(new DestinationUtilityByPurposeGeneratorCalibration(purpose, dataSet, impedanceParam, distanceParam));
        }
        ConcurrentExecutor<Tuple<Purpose, IndexedDoubleMatrix2D>> executor = ConcurrentExecutor.fixedPoolService(PURPOSES.size());
        List<Tuple<Purpose,IndexedDoubleMatrix2D>> results = executor.submitTasksAndWaitForCompletion(utilityCalcTasks);
        for(Tuple<Purpose, IndexedDoubleMatrix2D> result: results) {
            utilityMatrices.put(result.getFirst(), result.getSecond());
        }
        utilityMatrices.putAll(dataSet.getMandatoryUtilityMatrices());
    }

    private void distributeTrips() {
        final int numberOfThreads = Runtime.getRuntime().availableProcessors();
        final Collection<MitoHousehold> households = dataSet.getModelledHouseholds().values();

        final int partitionSize = (int) ((double) households.size() / (numberOfThreads)) + 1;
        Iterable<List<MitoHousehold>> partitions = Iterables.partition(households, partitionSize);

        logger.info("Using " + numberOfThreads + " thread(s)" +
                " with partitions of size " + partitionSize);

        ConcurrentExecutor<Void> executor = ConcurrentExecutor.fixedPoolService(numberOfThreads);
        List<Callable<Void>> homeBasedTasks = new ArrayList<>();
        for (final List<MitoHousehold> partition : partitions) {
            homeBasedTasks.add(HbsHbrHboDistributionCalibration.hbs(utilityMatrices.get(HBS), partition, dataSet.getZones(),
                    dataSet.getTravelDistancesAuto(), dataSet.getPeakHour()));
        }
        executor.submitTasksAndWaitForCompletion(homeBasedTasks);
        logger.info("Distributed: " + distributedTripsCounter + ", failed: " + failedTripsCounter);
        logger.info("Distance sum: " + distributedDistanceSum);
        logger.info("Ideal budget sum: " + idealBudgetSum);
        logger.info("Actual budget sum: " + actualBudgetSum);
    }

    private double calculateAdjustment() {
        double mean = distributedDistanceSum.doubleValue() / distributedTripsCounter.doubleValue();
        double adjustment = mean / referenceMean;
        if(adjustment < 0.5) {
            adjustment = 0.5;
        } else if (adjustment > 2) {
            adjustment = 2;
        }
        logger.info("Mean: " + mean + " Ref: " + referenceMean + " Adjustment: " + adjustment);
        return adjustment;
    }


}
