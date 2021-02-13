package de.tum.bgu.msm.modules.tripDistribution.calibration;

import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.AtomicDouble;
import com.google.common.util.concurrent.AtomicDoubleArray;
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
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.stream.Collectors;

import static de.tum.bgu.msm.data.Purpose.*;

/**
 * @author Nico
 */
public final class DiscretionaryTripDistributionCalibration extends Module {

    public final static AtomicDouble idealBudgetSum = new AtomicDouble(0.);
    public final static AtomicDouble actualBudgetSum = new AtomicDouble(0.);
    public final static AtomicInteger failedTripsCounter = new AtomicInteger(0);

    public final static AtomicIntegerArray distributedTripsCounters = new AtomicIntegerArray(new int[] {0,0,0});
    public final static AtomicDoubleArray distributedDistanceSums = new AtomicDoubleArray(new double[] {0.,0.,0.});

    private final static Purpose PURPOSE = HBO;

    private final double impedanceParam = 53;

    private double[] referenceMeans = {5.411735, 8.806595, 9.801090};
    private double[] distanceParams = {-0.062, -0.062, -0.062};

    private Map<Integer, IndexedDoubleMatrix2D> utilityMatrices = new HashMap<>();

    private final static Logger logger = Logger.getLogger(DiscretionaryTripDistributionCalibration.class);

    public DiscretionaryTripDistributionCalibration(DataSet dataSet) {
        super(dataSet);
    }

    @Override
    public void run() {

        double[] adjustments;
        do {
//            logger.info("Destination choice utility matrices...");
            buildMatrices();

//            logger.info("Distributing trips for households...");
            distributeTrips();

//            logger.info("Updating distanceParam...");
            adjustments = calculateAdjustments();
            for (int i = 0 ; i < 3 ; i++) {
                distanceParams[i] *= adjustments[i];
                distributedTripsCounters.set(i, 0);
                distributedDistanceSums.set(i, 0.);
            }

            logger.info("New distance params = " + distanceParams[0] + " || " + distanceParams[1] + " || " + distanceParams[2]);

            failedTripsCounter.set(0);
            idealBudgetSum.set(0.);
            actualBudgetSum.set(0.);
        } while (Arrays.stream(adjustments).map(b -> Math.abs(b-1.)).max().getAsDouble() > 0.01);
    }

    private void buildMatrices() {
        List<Callable<Tuple<Integer,IndexedDoubleMatrix2D>>> utilityCalcTasks = new ArrayList<>();

        for (int i = 0 ; i < 3 ; i++) {
            utilityCalcTasks.add(new DestinationUtilityByPurposeGeneratorCalibration(PURPOSE, i, dataSet, impedanceParam, distanceParams[i]));
        }
        ConcurrentExecutor<Tuple<Integer, IndexedDoubleMatrix2D>> executor = ConcurrentExecutor.fixedPoolService(3);
        List<Tuple<Integer,IndexedDoubleMatrix2D>> results = executor.submitTasksAndWaitForCompletion(utilityCalcTasks);
        for(Tuple<Integer, IndexedDoubleMatrix2D> result: results) {
            utilityMatrices.put(result.getFirst(), result.getSecond());
        }
    }

    private void distributeTrips() {
        final int numberOfThreads = Runtime.getRuntime().availableProcessors();
        final Collection<MitoHousehold> households = dataSet.getModelledHouseholds().values();

        final int partitionSize = (int) ((double) households.size() / (numberOfThreads)) + 1;
        Iterable<List<MitoHousehold>> partitions = Iterables.partition(households, partitionSize);

//        logger.info("Using " + numberOfThreads + " thread(s)" +
//                " with partitions of size " + partitionSize);

        ConcurrentExecutor<Void> executor = ConcurrentExecutor.fixedPoolService(numberOfThreads);
        List<Callable<Void>> homeBasedTasks = new ArrayList<>();
        for (final List<MitoHousehold> partition : partitions) {
            List<MitoHousehold> noCarHouseholds = partition.stream().filter(hh -> hh.getAutos() == 0).collect(Collectors.toUnmodifiableList());
            List<MitoHousehold> partialCarHouseholds = partition.stream().filter(hh -> hh.getAutosPerAdult() > 0 && hh.getAutosPerAdult() < 1).collect(Collectors.toUnmodifiableList());
            List<MitoHousehold> allCarHouseholds = partition.stream().filter(hh -> hh.getAutosPerAdult() >= 1).collect(Collectors.toUnmodifiableList());

            homeBasedTasks.add(HbsHbrHboDistributionCalibration.hbo(utilityMatrices.get(0), 0, noCarHouseholds, dataSet.getZones(), dataSet.getTravelDistancesAuto()));
            homeBasedTasks.add(HbsHbrHboDistributionCalibration.hbo(utilityMatrices.get(1), 1, partialCarHouseholds, dataSet.getZones(), dataSet.getTravelDistancesAuto()));
            homeBasedTasks.add(HbsHbrHboDistributionCalibration.hbo(utilityMatrices.get(2), 2, allCarHouseholds, dataSet.getZones(), dataSet.getTravelDistancesAuto()));
        }
        executor.submitTasksAndWaitForCompletion(homeBasedTasks);
//        logger.info("Distributed: " + distributedTripsCounter + ", failed: " + failedTripsCounter);
//        logger.info("Distance sum: " + distributedDistanceSum);
//        logger.info("Ideal budget sum: " + idealBudgetSum);
//        logger.info("Actual budget sum: " + actualBudgetSum);
    }

    private double[] calculateAdjustments() {

        double[] adjustments = new double[3];

        for (int i = 0 ; i < 3 ; i++) {
            double mean = distributedDistanceSums.get(i) / distributedTripsCounters.get(i);
            adjustments[i] = mean / referenceMeans[i];

            adjustments[i] = Math.max(adjustments[i], 0.5);
            adjustments[i] = Math.min(adjustments[i], 2);

            logger.info(i + " || Mean: " + mean + " Ref: " + referenceMeans[i] + " Adjustment: " + adjustments[i]);
        }
        return adjustments;
    }


}
