package de.tum.bgu.msm.modules.tripDistribution.destinationChooser;

import de.tum.bgu.msm.data.*;
import de.tum.bgu.msm.data.travelDistances.TravelDistances;
import de.tum.bgu.msm.modules.tripDistribution.TripDistribution;
import de.tum.bgu.msm.util.MitoUtil;
import de.tum.bgu.msm.util.concurrent.RandomizableConcurrentFunction;
import de.tum.bgu.msm.util.matrices.IndexedDoubleMatrix1D;
import de.tum.bgu.msm.util.matrices.IndexedDoubleMatrix2D;
import org.apache.log4j.Logger;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Nico
 */
public final class HbeHbwDistribution extends RandomizableConcurrentFunction<Void> {

    private final static Logger logger = Logger.getLogger(HbeHbwDistribution.class);

    private final Purpose purpose;
    private final MitoOccupationStatus mitoOccupationStatus;
    private final IndexedDoubleMatrix2D baseProbabilities;
    private final TravelDistances travelDistances;

    private final Collection<MitoHousehold> householdPartition;
    private final Map<Integer, MitoZone> zonesCopy;

    private final int carOwnershipIndex;

    public HbeHbwDistribution(Purpose purpose, DataSet dataSet, int index, Collection<MitoHousehold> householdPartition) {
        super(MitoUtil.getRandomObject().nextLong());
        this.purpose = purpose;
        this.carOwnershipIndex = index;
        this.baseProbabilities = TripDistribution.utilityMatrices.get(purpose).get(index);
        this.householdPartition = householdPartition;
        this.zonesCopy = new HashMap<>(dataSet.getZones());
        this.travelDistances = dataSet.getTravelDistancesAuto();
        if(purpose.equals(Purpose.HBW)) {
            this.mitoOccupationStatus = MitoOccupationStatus.WORKER;
        } else {
            this.mitoOccupationStatus = MitoOccupationStatus.STUDENT;
        }
    }

    @Override
    public Void call() {
        int distributedTripsCounter = 0;
        double distributedDistanceCounter = 0.;
        int failedTripsCounter = 0;

        for (MitoHousehold household : householdPartition) {
            for (MitoPerson person : household.getPersons().values()) {
                for (MitoTrip trip : person.getTripsForPurpose(purpose)) {
                    trip.setTripOrigin(household);
                    Location destination = findDestination(household, trip);
                    trip.setTripDestination(destination);
                    if (destination == null) {
                        logger.debug("No destination found for trip" + trip);
                        failedTripsCounter++;
                        continue;
                    }
                    distributedTripsCounter++;
                    distributedDistanceCounter += travelDistances.getTravelDistance(household.getHomeZone().getZoneId(),destination.getZoneId());
                }
            }
        }
        TripDistribution.distributedTrips.get(purpose).addAndGet(carOwnershipIndex, distributedTripsCounter);
        TripDistribution.distributedDistances.get(purpose).addAndGet(carOwnershipIndex, distributedDistanceCounter);
        TripDistribution.failedTrips.get(purpose).addAndGet(failedTripsCounter);
        return null;
    }

    private Location findDestination(MitoHousehold household, MitoTrip trip) {
        if (isFixedByOccupation(trip)) {
            return trip.getPerson().getOccupation();
        } else {
            IndexedDoubleMatrix1D probabilities = baseProbabilities.viewRow(household.getHomeZone().getId());
            final int internalIndex = MitoUtil.select(probabilities.toNonIndexedArray(), random, probabilities.zSum());
            final MitoZone destination = zonesCopy.get(probabilities.getIdForInternalIndex(internalIndex));
            TripDistribution.randomOccupationTrips.get(purpose).incrementAndGet(carOwnershipIndex);
            TripDistribution.randomOccupationDistances.get(purpose).addAndGet(carOwnershipIndex,
                    travelDistances.getTravelDistance(household.getHomeZone().getZoneId(),destination.getZoneId()));

            return destination;
        }
    }

    private boolean isFixedByOccupation(MitoTrip trip) {
        if (trip.getPerson().getMitoOccupationStatus() == mitoOccupationStatus) {
            return trip.getPerson().getOccupation() != null;
        }
        return false;
    }
}
