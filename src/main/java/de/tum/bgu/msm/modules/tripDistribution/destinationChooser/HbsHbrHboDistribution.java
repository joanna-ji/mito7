package de.tum.bgu.msm.modules.tripDistribution.destinationChooser;

import de.tum.bgu.msm.data.*;
import de.tum.bgu.msm.data.travelDistances.TravelDistances;
import de.tum.bgu.msm.modules.tripDistribution.TripDistribution;
import de.tum.bgu.msm.util.MitoUtil;
import de.tum.bgu.msm.util.concurrent.RandomizableConcurrentFunction;
import de.tum.bgu.msm.util.matrices.IndexedDoubleMatrix2D;
import org.apache.log4j.Logger;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Nico
 */
public class HbsHbrHboDistribution extends RandomizableConcurrentFunction<Void> {

    private final static Logger logger = Logger.getLogger(HbsHbrHboDistribution.class);

    private final Purpose purpose;

    private final IndexedDoubleMatrix2D baseProbabilities;
    private final TravelDistances travelDistances;

    private final Collection<MitoHousehold> householdPartition;
    private final Map<Integer, MitoZone> zonesCopy;

    private final int carOwnershipIndex;

    public HbsHbrHboDistribution(Purpose purpose, DataSet dataSet, int index, Collection<MitoHousehold> householdPartition) {
        super(MitoUtil.getRandomObject().nextLong());
        this.purpose = purpose;
        this.carOwnershipIndex = index;
        this.householdPartition = householdPartition;
        this.baseProbabilities = TripDistribution.utilityMatrices.get(purpose).get(index);
        this.zonesCopy = new HashMap<>(dataSet.getZones());
        this.travelDistances = dataSet.getTravelDistancesAuto();
    }

    @Override
    public Void call() {
        int distributedTripsCounter = 0;
        int failedTripsCounter= 0;
        double distributedDistanceCounter = 0.;

        for (MitoHousehold household : householdPartition) {
            for(MitoPerson person : household.getPersons().values()) {
                for (MitoTrip trip : person.getTripsForPurpose(purpose)) {
                    trip.setTripOrigin(household);
                    MitoZone destination = findDestination(household.getHomeZone().getId());
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

    private MitoZone findDestination(int origin) {
        return zonesCopy.get(baseProbabilities.getIdForInternalColumnIndex(MitoUtil.select(baseProbabilities.viewRow(origin).toNonIndexedArray(), random)));
    }
}

