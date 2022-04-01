package de.tum.bgu.msm.modules.tripDistribution.destinationChooser;

import de.tum.bgu.msm.data.*;
import de.tum.bgu.msm.data.travelDistances.TravelDistances;
import de.tum.bgu.msm.modules.tripDistribution.TripDistribution;
import de.tum.bgu.msm.util.MitoUtil;
import de.tum.bgu.msm.util.concurrent.RandomizableConcurrentFunction;
import de.tum.bgu.msm.util.matrices.IndexedDoubleMatrix2D;
import org.apache.log4j.Logger;

import java.util.*;

import static de.tum.bgu.msm.data.Purpose.*;

/**
 * @author Nico
 */
public final class RrtDistribution extends RandomizableConcurrentFunction<Void> {

    private final static Logger logger = Logger.getLogger(RrtDistribution.class);

    private final Map<Integer, IndexedDoubleMatrix2D> baseProbabilitiesMap;
    private final TravelDistances travelDistances;

    private final Collection<MitoHousehold> householdPartition;
    private final Map<Integer, MitoZone> zonesCopy;


    public RrtDistribution(DataSet dataSet, Collection<MitoHousehold> householdPartition) {
        super(MitoUtil.getRandomObject().nextLong());
        this.baseProbabilitiesMap = TripDistribution.utilityMatrices.get(RRT);
        this.zonesCopy = new HashMap<>(dataSet.getZones());
        this.travelDistances = dataSet.getTravelDistancesNMT();
        this.householdPartition = householdPartition;
    }

    @Override
    public Void call() {
        int failedTripsCounter= 0;

        for (MitoHousehold household : householdPartition) {
            for(MitoPerson person : household.getPersons().values()) {
                if(person.hasTripsForPurpose(RRT)) {
                    EnumSet<Mode> restrictedModeSet = person.getModeRestriction().getRestrictedModeSet();

                    int index;
                    if(restrictedModeSet.contains(Mode.walk) && restrictedModeSet.contains(Mode.bicycle)) {
                        index = 1;
                    } else if (restrictedModeSet.contains(Mode.walk)) {
                        index = 0;
                    } else if (restrictedModeSet.contains(Mode.bicycle)) {
                        index = 2;
                    } else {
                        throw new RuntimeException("Bicycle and walk not in choice set for RRT trips for person " + person.getId());
                    }

                    for (MitoTrip trip : person.getTripsForPurpose(RRT)) {
                        Location origin = findOrigin(person);
                        trip.setTripOrigin(origin);
                        if (origin == null) {
                            logger.debug("No origin found for trip" + trip);
                            failedTripsCounter++;
                            continue;
                        }
                        MitoZone destination = findDestination(origin.getZoneId(),index);
                        trip.setTripDestination(destination);
                        if (destination == null) {
                            logger.debug("No destination found for trip" + trip);
                            failedTripsCounter++;
                            continue;
                        }
                        double distance = travelDistances.getTravelDistance(origin.getZoneId(),destination.getZoneId());

                        TripDistribution.distributedTrips.get(RRT).incrementAndGet(index);
                        TripDistribution.distributedDistances.get(RRT).addAndGet(index, distance);
                    }
                }

            }
        }
        TripDistribution.failedTrips.get(RRT).addAndGet(failedTripsCounter);
        return null;
    }

    private Location findOrigin(MitoPerson person) {
        if (MitoUtil.getRandomObject().nextDouble() < 0.85) {
            return person.getHousehold().getHomeZone();
        } else {
            final List<Location> possibleBaseZones = new ArrayList<>();
            for (Purpose purpose : EnumSet.of(HBW, HBE, HBS, HBR, HBO)) {
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

    private MitoZone findDestination(int origin, int index) {
        IndexedDoubleMatrix2D baseProbabilities = baseProbabilitiesMap.get(index);
        return zonesCopy.get(baseProbabilities.getIdForInternalColumnIndex(MitoUtil.select(baseProbabilities.viewRow(origin).toNonIndexedArray(), random)));
    }

}
