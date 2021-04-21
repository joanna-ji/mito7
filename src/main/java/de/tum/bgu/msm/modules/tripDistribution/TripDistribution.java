package de.tum.bgu.msm.modules.tripDistribution;

import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.AtomicDoubleArray;
import de.tum.bgu.msm.data.DataSet;
import de.tum.bgu.msm.data.MitoHousehold;
import de.tum.bgu.msm.data.Purpose;
import de.tum.bgu.msm.modules.Module;
import de.tum.bgu.msm.modules.tripDistribution.destinationChooser.*;
import de.tum.bgu.msm.util.concurrent.ConcurrentExecutor;
import de.tum.bgu.msm.util.matrices.IndexedDoubleMatrix2D;
import org.apache.commons.lang3.tuple.Triple;
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
public final class TripDistribution extends Module {

    private final static Logger logger = Logger.getLogger(TripDistribution.class);
    private final static EnumSet<Purpose> PURPOSES = EnumSet.of(HBW,HBE,HBS,HBR,HBO,RRT,NHBW,NHBO);
    private final static EnumSet<Purpose> MANDATORY_PURPOSES = EnumSet.of(HBW,HBE);
    private final static EnumSet<Purpose> DISCRETIONARY_PURPOSES = EnumSet.of(HBS,HBR,HBO,RRT,NHBW,NHBO);
    private final static EnumSet<Purpose> HOMEBASEDDISCRETIONARY_PURPOSES = EnumSet.of(HBS,HBR,HBO,RRT);
    private final static EnumSet<Purpose> NONHOMEBASED_PURPOSES = EnumSet.of(NHBW,NHBO);

    public final static EnumMap<Purpose, AtomicIntegerArray> distributedTrips = new EnumMap<>(Purpose.class);
    public final static EnumMap<Purpose, AtomicDoubleArray> distributedDistances = new EnumMap<>(Purpose.class);
    public final static EnumMap<Purpose, AtomicInteger> failedTrips = new EnumMap<>(Purpose.class);

    public final static EnumMap<Purpose, AtomicIntegerArray> randomOccupationTrips = new EnumMap<>(Purpose.class);
    public final static EnumMap<Purpose, AtomicDoubleArray> randomOccupationDistances = new EnumMap<>(Purpose.class);

    public final static EnumMap<Purpose, AtomicInteger> completelyRandomNhbTrips = new EnumMap<>(Purpose.class);
    public final static EnumMap<Purpose, Map<Integer, IndexedDoubleMatrix2D>> utilityMatrices = new EnumMap<>(Purpose.class);

    // Calibration parameters (set all to null unless calibrating)
    final static Purpose calibrationPurpose = null;
    final static Double calibrationImpedanceParam = null;
    final static double[] calibrationDistanceParams = null;
    private final static double[] calibrationReferenceMeans = null;

    private final int numberOfThreads = Runtime.getRuntime().availableProcessors();
    private final List<Tuple<Integer, List<MitoHousehold>>> partitions = new ArrayList<>();

    private boolean completedMandatory = false;
    private boolean completedHomeBasedDiscretionary = false;

    public final static AtomicInteger countNoDestinationErrorTrips = new AtomicInteger(0);
    public final static AtomicInteger countOriginPriorTrips = new AtomicInteger(0);
    public final static AtomicInteger countOriginOccupation = new AtomicInteger(0);
    public final static AtomicInteger countOriginRandom = new AtomicInteger(0);

    public TripDistribution(DataSet dataSet) {
        super(dataSet);
    }

    @Override
    public void run() {
        if(!completedMandatory) {

            logger.info("Creating partitions...");
            createPartitions();

            logger.info("Setting counters and matrices to zero...");
            zeroCountersAndMatrices(PURPOSES);

            logger.info("Building destination choice utility matrices...");
            buildMatrices(PURPOSES);

            if(MANDATORY_PURPOSES.contains(calibrationPurpose)) runCalibration(calibrationPurpose);
            else {
                logger.info("Distributing HBW and HBE trips...");
                distributeMandatoryTrips(MANDATORY_PURPOSES);
            }

            logger.info("Mandatory distribution statistics...");
            distributionStatistics(MANDATORY_PURPOSES);

            completedMandatory = true;
        } else {
            if(EnumSet.of(HBS,HBR,HBO).contains(calibrationPurpose)) runCalibration(calibrationPurpose);
            else {
                logger.info("Distributing HBS, HBR, and HBO trips...");
                distributeHbsHbrHboTrips(EnumSet.of(HBS,HBR,HBO));

                if(RRT.equals(calibrationPurpose)) runCalibration(calibrationPurpose);
                else {
                    logger.info("Distributing RRT trips...");
                    distributeRrtTrips();

                    if(EnumSet.of(NHBW,NHBO).contains(calibrationPurpose)) runCalibration(calibrationPurpose);
                    else {
                        logger.info("Distributing NHBW and NHBO trips...");
                        distributeNhbwNhboTrips(EnumSet.of(NHBW,NHBO));
                    }
                }
            }

            logger.info("Discretionary distribution statistics...");
            distributionStatistics(DISCRETIONARY_PURPOSES);
        }
    }

    public void runWithMoped() {
        if(!completedMandatory) {

            logger.info("Creating partitions...");
            createPartitions();

            logger.info("Setting counters and matrices to zero...");
            zeroCountersAndMatrices(PURPOSES);

            logger.info("Building destination choice utility matrices...");
            buildMatrices(PURPOSES);

            if(MANDATORY_PURPOSES.contains(calibrationPurpose)) runCalibration(calibrationPurpose);
            else {
                logger.info("Distributing HBW and HBE trips...");
                distributeMandatoryTrips(MANDATORY_PURPOSES);
            }

            logger.info("Mandatory distribution statistics...");
            distributionStatistics(MANDATORY_PURPOSES);

            completedMandatory = true;
        } else if(!completedHomeBasedDiscretionary) {
            if(EnumSet.of(HBS,HBR,HBO).contains(calibrationPurpose)) runCalibration(calibrationPurpose);
            else {
                logger.info("Distributing HBS, HBR, and HBO trips...");
                distributeHbsHbrHboTrips(EnumSet.of(HBS,HBR,HBO));

                if(RRT.equals(calibrationPurpose)) runCalibration(calibrationPurpose);
                else {
                    logger.info("Distributing RRT trips...");
                    distributeRrtTrips();
                }
            }

            logger.info("Home Based Discretionary distribution statistics...");
            distributionStatistics(HOMEBASEDDISCRETIONARY_PURPOSES);

            completedHomeBasedDiscretionary = true;
        }else{
            if(EnumSet.of(NHBW,NHBO).contains(calibrationPurpose)) runCalibration(calibrationPurpose);
            else {
                logger.info("Distributing NHBW and NHBO trips...");
                distributeNhbwNhboTrips(EnumSet.of(NHBW,NHBO));
            }
            logger.info("Non Home Based Discretionary distribution statistics...");
            distributionStatistics(NONHOMEBASED_PURPOSES);
        }
    }

    //for Moped
    public void setUp() {
        logger.info("Building initial destination choice utility matrices...");
        buildMatrices(NONHOMEBASED_PURPOSES);

        logger.info("finding origins for non home based trips...");
        final int numberOfThreads = Runtime.getRuntime().availableProcessors();

        ConcurrentExecutor<Void> executor = ConcurrentExecutor.fixedPoolService(numberOfThreads);
        List<Callable<Void>> nonHomeBasedTasks = new ArrayList<>();

        for (Purpose purpose : NONHOMEBASED_PURPOSES){
            for (final Tuple<Integer, List<MitoHousehold>> partition : partitions) {
                if (purpose.equals(NHBW)){
                    nonHomeBasedTasks.add(new NhbwNhboOrigin(NHBW, dataSet, partition.getFirst(), partition.getSecond()));
                } else if (purpose.equals(NHBO)){
                    nonHomeBasedTasks.add(new NhbwNhboOrigin(NHBO, dataSet, partition.getFirst(), partition.getSecond()));
                }
            }
        }
        executor.submitTasksAndWaitForCompletion(nonHomeBasedTasks);
        logger.error(countNoDestinationErrorTrips + " trips have no mito destination zones. " +
                "Maybe because Moped selected zones is not in the mito zone systems " +
                "(no population zones as HBR/HBO destination) ");
        logger.info(countOriginPriorTrips + " trips origin --> prior trips. ");
        logger.info(countOriginOccupation + " trips origin --> occupation zone. ");
        logger.info(countOriginRandom + " trips origin --> random select. ");

    }

    private void createPartitions() {
        final Collection<MitoHousehold> households = dataSet.getModelledHouseholds().values();
        final int partitionSize = (int) ((double) households.size() / (numberOfThreads)) + 1;
        for(List<MitoHousehold> partition : Iterables.partition(households, partitionSize)) {
            partitions.add(new Tuple(0, partition.stream().filter(hh -> hh.getAutos() == 0).collect(Collectors.toUnmodifiableList())));
            partitions.add(new Tuple(1, partition.stream().filter(hh -> hh.getAutosPerAdult() > 0 && hh.getAutosPerAdult() < 1).collect(Collectors.toUnmodifiableList())));
            partitions.add(new Tuple(2, partition.stream().filter(hh -> hh.getAutosPerAdult() >= 1).collect(Collectors.toUnmodifiableList())));
        }
        logger.info("Using " + numberOfThreads + " thread(s) with " + partitions.size() + " household partitions.");
    }

    private void zeroCountersAndMatrices(EnumSet<Purpose> purposes) {
        for (Purpose purpose : purposes) {
            if(EnumSet.of(HBW,HBE,HBS,HBR,RRT,HBO,NHBW,NHBO).contains(purpose)) {
                utilityMatrices.put(purpose, new HashMap<>());
                distributedTrips.put(purpose, new AtomicIntegerArray(new int[] {0,0,0}));
                distributedDistances.put(purpose, new AtomicDoubleArray(new double[] {0.,0.,0.}));
                failedTrips.put(purpose, new AtomicInteger(0));
            }
            if(EnumSet.of(HBW, HBE).contains(purpose)) {
                randomOccupationTrips.put(purpose, new AtomicIntegerArray(new int[] {0,0,0}));
                randomOccupationDistances.put(purpose, new AtomicDoubleArray(new double[] {0.,0.,0.}));
            }
            if(EnumSet.of(NHBW,NHBO).contains(purpose)) {
                completelyRandomNhbTrips.put(purpose, new AtomicInteger(0));
            }
        }
    }

    private void buildMatrices(EnumSet<Purpose> purposes) {
        ConcurrentExecutor<Triple<Purpose, Integer, IndexedDoubleMatrix2D>> executor = ConcurrentExecutor.fixedPoolService(numberOfThreads);
        List<Callable<Triple<Purpose, Integer, IndexedDoubleMatrix2D>>> utilityCalcTasks = new ArrayList<>();

        for (Purpose purpose : purposes) {
            if (EnumSet.of(HBE,RRT).contains(purpose)) {
                utilityCalcTasks.add(new DestinationUtilityByPurposeGenerator(purpose, null, dataSet));
            } else {
                utilityCalcTasks.add(new DestinationUtilityByPurposeGenerator(purpose, 0, dataSet));
                utilityCalcTasks.add(new DestinationUtilityByPurposeGenerator(purpose, 1, dataSet));
                utilityCalcTasks.add(new DestinationUtilityByPurposeGenerator(purpose, 2, dataSet));
            }
        }
        List<Triple<Purpose, Integer, IndexedDoubleMatrix2D>> results = executor.submitTasksAndWaitForCompletion(utilityCalcTasks);

        for(Triple<Purpose, Integer, IndexedDoubleMatrix2D> result: results) {
            Purpose purpose = result.getLeft();
            Integer index = result.getMiddle();
            IndexedDoubleMatrix2D utilityMatrix = result.getRight();
            if (index == null) {
                utilityMatrices.get(purpose).put(0, utilityMatrix);
                utilityMatrices.get(purpose).put(1, utilityMatrix);
                utilityMatrices.get(purpose).put(2, utilityMatrix);
            } else {
                utilityMatrices.get(purpose).put(index, utilityMatrix);
            }
        }
    }

    private void distributeMandatoryTrips(EnumSet<Purpose> purposes) {
        ConcurrentExecutor<Void> executor = ConcurrentExecutor.fixedPoolService(numberOfThreads);
        List<Callable<Void>> mandatoryTasks = new ArrayList<>();
        for(Purpose purpose : purposes) {
            for (final Tuple<Integer, List<MitoHousehold>> partition : partitions) {
                    mandatoryTasks.add(new HbeHbwDistribution(purpose, dataSet, partition.getFirst(), partition.getSecond()));
            }
        }
        executor.submitTasksAndWaitForCompletion(mandatoryTasks);
    }

    private void distributeHbsHbrHboTrips(EnumSet<Purpose> purposes) {
        ConcurrentExecutor<Void> executor = ConcurrentExecutor.fixedPoolService(numberOfThreads);
        List<Callable<Void>> homeBasedTasks = new ArrayList<>();
        for(Purpose purpose : purposes) {
            for (final Tuple<Integer, List<MitoHousehold>> partition : partitions) {
                homeBasedTasks.add(new HbsHbrHboDistribution(purpose, dataSet, partition.getFirst(), partition.getSecond()));
            }
        }
        executor.submitTasksAndWaitForCompletion(homeBasedTasks);
    }

    private void distributeRrtTrips() {
        ConcurrentExecutor<Void> executor = ConcurrentExecutor.fixedPoolService(numberOfThreads);
        List<Callable<Void>> rrtTasks = new ArrayList<>();
        for (final Tuple<Integer, List<MitoHousehold>> partition : partitions) {
            rrtTasks.add(new RrtDistribution(dataSet, partition.getFirst(), partition.getSecond()));
        }
        executor.submitTasksAndWaitForCompletion(rrtTasks);
    }

    private void distributeNhbwNhboTrips(EnumSet<Purpose> purposes) {
        ConcurrentExecutor<Void> executor = ConcurrentExecutor.fixedPoolService(numberOfThreads);
        List<Callable<Void>> nonHomeBasedTasks = new ArrayList<>();
        for(Purpose purpose : purposes) {
            for (final Tuple<Integer, List<MitoHousehold>> partition : partitions) {
                nonHomeBasedTasks.add(new NhbwNhboDistribution(purpose, dataSet, partition.getFirst(), partition.getSecond()));
            }
        }
        executor.submitTasksAndWaitForCompletion(nonHomeBasedTasks);
    }

    private void distributionStatistics(EnumSet<Purpose> purposes) {
        // Trip distances (differentiated by car ownership)
        logger.info("PURPOSE || FAILED ||   MEAN DISTANCE   || [ 0 CARS/ADULT, < 1 CARS/ADULT, >= 1 CARS/ADULT ]");
        for (Purpose purpose : purposes) {
            AtomicIntegerArray counts = distributedTrips.get(purpose);
            AtomicDoubleArray distances = distributedDistances.get(purpose);
            double mean = (distances.get(0) + distances.get(1) + distances.get(2)) / (counts.get(0) + counts.get(1) + counts.get(2));
            double[] meanDistances = new double[3];
            meanDistances[0] = distances.get(0) / counts.get(0);
            meanDistances[1] = distances.get(1) / counts.get(1);
            meanDistances[2] = distances.get(2) / counts.get(2);
            logger.info(purpose + "     || " + failedTrips.get(purpose) + "      || " + mean + " || " + Arrays.toString(meanDistances));
        }
        // Random occupation trips (differentiated by car ownership)
        if(MANDATORY_PURPOSES.containsAll(purposes)) {
            logger.info("Random occupation trips:");
            logger.info("PURPOSE || COUNT ||   MEAN DISTANCE   || [ 0 CARS/ADULT, < 1 CARS/ADULT, >= 1 CARS/ADULT ]");
            for (Purpose purpose : MANDATORY_PURPOSES) {
                AtomicIntegerArray counts = randomOccupationTrips.get(purpose);
                AtomicDoubleArray distances = randomOccupationDistances.get(purpose);
                int count = counts.get(0) + counts.get(1) + counts.get(2);
                double mean = (distances.get(0) + distances.get(1) + distances.get(2)) / (counts.get(0) + counts.get(1) + counts.get(2));
                double[] meanDistances = new double[3];
                meanDistances[0] = distances.get(0) / counts.get(0);
                meanDistances[1] = distances.get(1) / counts.get(1);
                meanDistances[2] = distances.get(2) / counts.get(2);
                logger.info(purpose + "     || " + count + " || " + mean + " || " + Arrays.toString(meanDistances));
            }
        }
        // Non-home-based trips without matching home-based trip
        if(purposes.contains(NHBW)) {
            if (completelyRandomNhbTrips.get(NHBW).get() > 0) {
                logger.info("There were " + completelyRandomNhbTrips.get(NHBW).get() + " NHBW trips made by persons without a HBW trip." +
                        "  Assumed a destination for a suitable HBW trip as either origin or destination for the non-home-based trip.");
            }
        }
        if(purposes.contains(NHBO)) {
            if(completelyRandomNhbTrips.get(NHBO).get() > 0) {
                logger.info("There were " + completelyRandomNhbTrips.get(NHBO).get() + " NHBO trips made by persons without a matching home based trip." +
                        "  Assumed a destination for a suitable home based trip as either origin or destination for the non-home-based trip.");
            }
        }
    }

    private void runCalibration(Purpose purpose) {
        logger.info("Starting calibration for purpose " + purpose + "...");
        double[] adjustments;
        int iteration = 0;

        do {
            iteration++;
            logger.info("STARTING ITERATION " + iteration);

            // Rest counters and matrix to zero for purpose being calibrated
            zeroCountersAndMatrices(EnumSet.of(purpose));

            // Build new matrices for given purpose
            buildMatrices(EnumSet.of(purpose));

            // Distribute trips
            if(MANDATORY_PURPOSES.contains(purpose)) {
                distributeMandatoryTrips(EnumSet.of(purpose));
            } else if (EnumSet.of(HBS,HBR,HBO).contains(purpose)) {
                distributeHbsHbrHboTrips(EnumSet.of(purpose));
            } else if (purpose.equals(RRT)) {
                distributeRrtTrips();
            } else {
                distributeNhbwNhboTrips(EnumSet.of(purpose));
            }

            // Calculate adjustments
            adjustments = calculateDistanceParameterAdjustments(purpose);

            // Adjust distance parameters
            for (int i = 0 ; i < adjustments.length ; i++) {
                calibrationDistanceParams[i] *= adjustments[i];
            }
            logger.info("New distance params = " + Arrays.toString(calibrationDistanceParams));
        } while (Arrays.stream(adjustments).map(b -> Math.abs(b-1.)).max().getAsDouble() > 0.01);
        logger.info("CALIBRATED PURPOSE " + purpose + " AFTER " + iteration + " ITERATIONS");
    }

    private double[] calculateDistanceParameterAdjustments(Purpose purpose) {
        double[] adjustments = new double[calibrationReferenceMeans.length];
        AtomicDoubleArray distances;
        AtomicIntegerArray counts;

        if(MANDATORY_PURPOSES.contains(purpose)) {
            distances = randomOccupationDistances.get(purpose);
            counts = randomOccupationTrips.get(purpose);
        } else {
            distances = distributedDistances.get(purpose);
            counts = distributedTrips.get(purpose);
        }

        if(adjustments.length == 1) {
            double mean = (distances.get(0) + distances.get(1) + distances.get(2)) /
                    (counts.get(0) + counts.get(1) + counts.get(2));
            adjustments[0] = mean / calibrationReferenceMeans[0];
            logger.info("Index: null  Mean: " + mean + " Ref: " + calibrationReferenceMeans[0] + " Adjustment: " + adjustments[0]);
        } else for (int i = 0 ; i < adjustments.length ; i++) {
            double mean = distances.get(i) / counts.get(i);
            adjustments[i] = mean / calibrationReferenceMeans[i];
            adjustments[i] = Math.max(adjustments[i], 0.5);
            adjustments[i] = Math.min(adjustments[i], 2);
            logger.info("Index: "+ i + " Mean: " + mean + " Ref: " + calibrationReferenceMeans[i] + " Adjustment: " + adjustments[i]);
        }
        return adjustments;
    }
}
