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
public final class NhbwNhboDistribution extends RandomizableConcurrentFunction<Void> {

    private final static double VARIANCE_DOUBLED = 500 * 2;
    private final static double SQRT_INV = 1.0 / Math.sqrt(Math.PI * VARIANCE_DOUBLED);

    private final static Logger logger = Logger.getLogger(NhbwNhboDistribution.class);

    private final Purpose purpose;
    private final List<Purpose> priorPurposes;
    private final double peakHour;
    private final double speedInv;

    private final MitoOccupationStatus relatedMitoOccupationStatus;
    private final EnumMap<Purpose, IndexedDoubleMatrix2D> baseProbabilities;
    private final TravelDistances travelDistances;

    private final Collection<MitoHousehold> householdPartition;
    private final Map<Integer, MitoZone> zonesCopy;

    private double idealBudgetSum = 0;
    private double actualBudgetSum = 0;
    private double personBudgetPerTrip;
    private double mean;

    private NhbwNhboDistribution(Purpose purpose, List<Purpose> priorPurposes, MitoOccupationStatus relatedMitoOccupationStatus,
                                 EnumMap<Purpose, IndexedDoubleMatrix2D> baseProbabilities,  Collection<MitoHousehold> householdPartition, Map<Integer, MitoZone> zones,
                                 TravelDistances travelDistances, double peakHour, double speedInv) {
        super(MitoUtil.getRandomObject().nextLong());
        this.purpose = purpose;
        this.priorPurposes = priorPurposes;
        this.relatedMitoOccupationStatus = relatedMitoOccupationStatus;
        this.baseProbabilities = baseProbabilities;
        this.zonesCopy = new HashMap<>(zones);
        this.travelDistances = travelDistances;
        this.peakHour = peakHour;
        this.householdPartition = householdPartition;
        this.speedInv = speedInv;
    }

    public static NhbwNhboDistribution nhbw(EnumMap<Purpose, IndexedDoubleMatrix2D> baseProbabilites,  Collection<MitoHousehold> householdPartition, Map<Integer, MitoZone> zones,
                                            TravelDistances travelDistances, double peakHour) {
        return new NhbwNhboDistribution(Purpose.NHBW, Collections.singletonList(HBW),
                MitoOccupationStatus.WORKER, baseProbabilites, householdPartition, zones, travelDistances, peakHour,60/26.439);
    }

    public static NhbwNhboDistribution nhbo(EnumMap<Purpose, IndexedDoubleMatrix2D> baseProbabilites,  Collection<MitoHousehold> householdPartition, Map<Integer, MitoZone> zones,
                                            TravelDistances travelDistances, double peakHour) {
        return new NhbwNhboDistribution(Purpose.NHBO, ImmutableList.of(HBO, HBE, HBS, HBR),
                null, baseProbabilites, householdPartition, zones, travelDistances, peakHour,60/22.794);
    }

    @Override
    public Void call() {
        long counter = 0;
        for (MitoHousehold household : householdPartition) {
            if (LongMath.isPowerOfTwo(counter)) {
                logger.info(counter + " households done for Purpose " + purpose
                        + "\nIdeal budget sum: " + idealBudgetSum + " | actual budget sum: " + actualBudgetSum);
            }
            for(MitoPerson person : household.getPersons().values()) {
                if(person.getTripsForPurpose(purpose).size() > 0) {
                    if(person.getTravelTimeBudgetForPurpose(purpose) > 0) {
                        updateBudgets(person);
                        for (MitoTrip trip : person.getTripsForPurpose(purpose)) {
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
                        logger.warn("Person " + person.getId() + " has " + purpose + " trips but no TTB!");
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
        personBudgetPerTrip = person.getTravelTimeBudgetForPurpose(purpose) / person.getTripsForPurpose(purpose).size();
        mean = personBudgetPerTrip * ratio;
    }

    private Location findOrigin(MitoPerson person) {
        final List<Location> possibleBaseZones = new ArrayList<>();
        for (Purpose purpose : priorPurposes) {
            for (MitoTrip priorTrip : person.getTripsForPurpose(purpose)) {
                possibleBaseZones.add(priorTrip.getTripDestination());
            }
        }
        if (!possibleBaseZones.isEmpty()) {
            return MitoUtil.select(random, possibleBaseZones);
        }
        if (person.getMitoOccupationStatus() == relatedMitoOccupationStatus &&
                person.getOccupation() != null) {
            return person.getOccupation();
        }

        final Purpose selectedPurpose = MitoUtil.select(random, priorPurposes);
        return findRandomOrigin(person.getHousehold(), selectedPurpose);
    }

    private MitoZone findDestination(int origin) {
        final IndexedDoubleMatrix1D row = baseProbabilities.get(purpose).viewRow(origin);
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

    private MitoZone findRandomOrigin(MitoHousehold household, Purpose priorPurpose) {
        DiscretionaryTripDistribution.completelyRandomNhbTrips.incrementAndGet();
        if(baseProbabilities.get(priorPurpose) == null) {
            logger.info("prior purpose is null!");
        }
        if(household.getHomeZone() == null) {
            logger.info("home zone is null!");
        }
        final IndexedDoubleMatrix1D originProbabilities = baseProbabilities.get(priorPurpose).viewRow(household.getHomeZone().getId());
        final int destinationInternalId = MitoUtil.select(originProbabilities.toNonIndexedArray(), random);
        return zonesCopy.get(originProbabilities.getIdForInternalIndex(destinationInternalId));
    }

    private void postProcessTrip(MitoTrip trip) {
        actualBudgetSum += travelDistances.getTravelDistance(trip.getTripOrigin().getZoneId(),
                trip.getTripDestination().getZoneId()) * speedInv;
        idealBudgetSum += personBudgetPerTrip;
    }
}
