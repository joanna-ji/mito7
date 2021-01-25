package de.tum.bgu.msm.modules.tripDistribution.destinationChooser;

import com.google.common.collect.ImmutableList;
import com.google.common.math.LongMath;
import de.tum.bgu.msm.data.*;
import de.tum.bgu.msm.data.travelDistances.TravelDistances;
import de.tum.bgu.msm.data.travelTimes.TravelTimes;
import de.tum.bgu.msm.modules.tripDistribution.DiscretionaryTripDistribution;
import de.tum.bgu.msm.util.MitoUtil;
import de.tum.bgu.msm.util.concurrent.RandomizableConcurrentFunction;
import de.tum.bgu.msm.util.matrices.IndexedDoubleMatrix1D;
import de.tum.bgu.msm.util.matrices.IndexedDoubleMatrix2D;
import org.apache.commons.math3.util.FastMath;
import org.apache.log4j.Logger;

import java.util.*;
import java.util.stream.IntStream;

import static de.tum.bgu.msm.data.Purpose.*;

/**
 * @author Nico
 */
public final class RrtDistribution extends RandomizableConcurrentFunction<Void> {

    private final static double VARIANCE_DOUBLED = 5000 * 2;
    private final static double SQRT_INV = 1.0 / Math.sqrt(Math.PI * VARIANCE_DOUBLED);

    private final static Logger logger = Logger.getLogger(RrtDistribution.class);

    private final double speedInv;

    private final List<Purpose> priorPurposes;
    private final EnumMap<Purpose, IndexedDoubleMatrix2D> baseProbabilities;
    private final TravelDistances travelDistances;

    private double idealBudgetSum = 0;
    private double actualBudgetSum = 0;
    private double personBudgetPerTrip;
    private double mean;

    private final Collection<MitoHousehold> householdPartition;
    private final Map<Integer, MitoZone> zonesCopy;

    private RrtDistribution(List<Purpose> priorPurposes,
                            EnumMap<Purpose, IndexedDoubleMatrix2D> baseProbabilities,
                            Collection<MitoHousehold> householdPartition, Map<Integer, MitoZone> zones,
                            TravelDistances travelDistances, double speedInv) {
        super(MitoUtil.getRandomObject().nextLong());
        this.priorPurposes = priorPurposes;
        this.baseProbabilities = baseProbabilities;
        this.zonesCopy = new HashMap<>(zones);
        this.travelDistances = travelDistances;
        this.householdPartition = householdPartition;
        this.speedInv = speedInv;
    }

    public static RrtDistribution rrt(EnumMap<Purpose, IndexedDoubleMatrix2D> baseProbabilites,
                                      Collection<MitoHousehold> householdPartition, Map<Integer, MitoZone> zones,
                                      TravelDistances travelDistances) {
        return new RrtDistribution(ImmutableList.of(HBW, HBE, HBS, HBR, HBO),
                baseProbabilites, householdPartition, zones, travelDistances, 60 / 4.732784);
    }

    @Override
    public Void call() {
        long counter = 0;
        for (MitoHousehold household : householdPartition) {
            if (LongMath.isPowerOfTwo(counter)) {
                logger.info(counter + " households done for Purpose RRT"
                        + "\nIdeal budget sum: " + idealBudgetSum + " | actual budget sum: " + actualBudgetSum);
            }
            for(MitoPerson person : household.getPersons().values()) {
                if(person.getTripsForPurpose(RRT).size() > 0) {
                    if(person.getTravelTimeBudgetForPurpose(RRT) > 0) {
                        updateBudgets(person);
                        for (MitoTrip trip : person.getTripsForPurpose(RRT)) {
                            Location origin = findOrigin(person);
                            if (origin == null) {
                                logger.debug("No origin found for trip" + trip);
                                DiscretionaryTripDistribution.failedTripsCounter.incrementAndGet();
                                continue;
                            }
                            trip.setTripOrigin(origin);
                            MitoZone destination = findDestination(origin.getZoneId());
                            trip.setTripDestination(destination);
                            if (destination == null) {
                                logger.debug("No destination found for trip" + trip);
                                DiscretionaryTripDistribution.failedTripsCounter.incrementAndGet();
                                continue;
                            }
                            postProcessTrip(trip);
                            DiscretionaryTripDistribution.distributedTripsCounter.incrementAndGet();
                        }
                    } else {
                        logger.warn("Person " + person.getId() + " has RRT trips but no TTB!");
                        DiscretionaryTripDistribution.failedTripsCounter.incrementAndGet();
                    }
                }
            }
            counter++;
        }
        logger.info("Ideal budget sum: " + idealBudgetSum + " | actual budget sum: " + actualBudgetSum);
        return null;
    }

    private void updateBudgets(MitoPerson person) {
        double ratio;
        if (idealBudgetSum == actualBudgetSum) {
            ratio = 1;
        } else {
            ratio = idealBudgetSum / actualBudgetSum;
        }
        personBudgetPerTrip = person.getTravelTimeBudgetForPurpose(RRT) / person.getTripsForPurpose(RRT).size();
        mean = personBudgetPerTrip * ratio;
    }

    private Location findOrigin(MitoPerson person) {
        if (MitoUtil.getRandomObject().nextDouble() < 0.85) {
            return person.getHousehold().getHomeZone();
        } else {
            final List<Location> possibleBaseZones = new ArrayList<>();
            for (Purpose purpose : priorPurposes) {
                for (MitoTrip priorTrip : person.getTripsForPurpose(purpose)) {
                    possibleBaseZones.add(priorTrip.getTripDestination());
                }
            }
            if (!possibleBaseZones.isEmpty()) {
                return MitoUtil.select(random, possibleBaseZones);
            } else {
                return person.getHousehold().getHomeZone();
            }
        }
    }

    private MitoZone findDestination(int origin) {
        final IndexedDoubleMatrix1D row = baseProbabilities.get(RRT).viewRow(origin);
        double[] baseProbs = row.toNonIndexedArray();
        IntStream.range(0, baseProbs.length).parallel().forEach(i -> {
            //divide travel time by 2 as home based trips' budget account for the return trip as well
            double diff = travelDistances.getTravelDistance(zonesCopy.get(origin).getId(), zonesCopy.get(row.getIdForInternalIndex(i)).getId()) * speedInv - mean;
            double factor = SQRT_INV * FastMath.exp(-(diff * diff) / VARIANCE_DOUBLED);
            baseProbs[i] = baseProbs[i] * factor;
        });

        int destinationInternalId = MitoUtil.select(baseProbs, random);
        return zonesCopy.get(row.getIdForInternalIndex(destinationInternalId));
    }

    private void postProcessTrip(MitoTrip trip) {
        actualBudgetSum += travelDistances.getTravelDistance(trip.getTripOrigin().getZoneId(),
                trip.getTripDestination().getZoneId()) * speedInv;
        idealBudgetSum += personBudgetPerTrip;
    }
}
