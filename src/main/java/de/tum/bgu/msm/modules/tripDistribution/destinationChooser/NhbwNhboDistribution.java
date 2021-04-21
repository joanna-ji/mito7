package de.tum.bgu.msm.modules.tripDistribution.destinationChooser;

import com.google.common.collect.ImmutableList;
import de.tum.bgu.msm.data.*;
import de.tum.bgu.msm.data.travelDistances.TravelDistances;
import de.tum.bgu.msm.modules.tripDistribution.TripDistribution;
import de.tum.bgu.msm.util.MitoUtil;
import de.tum.bgu.msm.util.concurrent.RandomizableConcurrentFunction;
import de.tum.bgu.msm.util.matrices.IndexedDoubleMatrix1D;
import de.tum.bgu.msm.util.matrices.IndexedDoubleMatrix2D;
import org.apache.log4j.Logger;

import java.util.*;

import static de.tum.bgu.msm.data.Purpose.*;

/**
 * @author Nico
 */
public final class NhbwNhboDistribution extends RandomizableConcurrentFunction<Void> {

    private final static Logger logger = Logger.getLogger(NhbwNhboDistribution.class);

    private final Purpose purpose;
    private final List<Purpose> priorPurposes;

    private final MitoOccupationStatus relatedMitoOccupationStatus;
    private final TravelDistances travelDistances;

    EnumMap<Purpose, Map<Integer, IndexedDoubleMatrix2D>> baseProbabilities;

    private final Collection<MitoHousehold> householdPartition;
    private final Map<Integer, MitoZone> zonesCopy;

    private final int carOwnershipIndex;


    public NhbwNhboDistribution(Purpose purpose, DataSet dataSet, int index, Collection<MitoHousehold> householdPartition) {
        super(MitoUtil.getRandomObject().nextLong());
        this.purpose = purpose;
        this.carOwnershipIndex = index;
        this.baseProbabilities = TripDistribution.utilityMatrices;
        this.householdPartition = householdPartition;
        this.zonesCopy = new HashMap<>(dataSet.getZones());
        this.travelDistances = dataSet.getTravelDistancesAuto();
        if(this.purpose.equals(NHBW)) {
            this.priorPurposes = Collections.singletonList(HBW);
            this.relatedMitoOccupationStatus = MitoOccupationStatus.WORKER;
        } else {
            this.priorPurposes = ImmutableList.of(HBO, HBE, HBS, HBR);
            this.relatedMitoOccupationStatus = null;
        }
    }

    @Override
    public Void call() {
        int distributedTripsCounter = 0;
        int failedTripsCounter = 0;
        double distributedDistanceCounter = 0.;

        for (MitoHousehold household : householdPartition) {
            for(MitoPerson person : household.getPersons().values()) {
                for (MitoTrip trip : person.getTripsForPurpose(purpose)) {
                    if (!Mode.walk.equals(trip.getTripMode())) {
                        Location origin = findOrigin(person);
                        trip.setTripOrigin(origin);
                        if (origin == null) {
                            logger.debug("No origin found for trip" + trip);
                            failedTripsCounter++;
                            continue;
                        }
                        MitoZone destination = findDestination(origin.getZoneId());
                        trip.setTripDestination(destination);
                        if (destination == null) {
                            logger.debug("No destination found for trip" + trip);
                            failedTripsCounter++;
                            continue;
                        }
                        distributedTripsCounter++;
                        distributedDistanceCounter += travelDistances.getTravelDistance(origin.getZoneId(), destination.getZoneId());
                    }
                }
            }
        }
        TripDistribution.distributedTrips.get(purpose).addAndGet(carOwnershipIndex, distributedTripsCounter);
        TripDistribution.distributedDistances.get(purpose).addAndGet(carOwnershipIndex, distributedDistanceCounter);
        TripDistribution.failedTrips.get(purpose).addAndGet(failedTripsCounter);
        return null;
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
        IndexedDoubleMatrix2D probabilityMatrix = baseProbabilities.get(purpose).get(carOwnershipIndex);
        if(probabilityMatrix == null) {
            logger.error("Null probability matrix for purpose " + purpose + " car ownership index " + carOwnershipIndex);
        }
        return zonesCopy.get(probabilityMatrix.getIdForInternalColumnIndex(MitoUtil.select(probabilityMatrix.viewRow(origin).toNonIndexedArray(), random)));
    }

    private MitoZone findRandomOrigin(MitoHousehold household, Purpose priorPurpose) {
        TripDistribution.completelyRandomNhbTrips.get(purpose).incrementAndGet();
        final IndexedDoubleMatrix1D originProbabilities = baseProbabilities.get(priorPurpose).get(carOwnershipIndex).viewRow(household.getHomeZone().getId());
        final int destinationInternalId = MitoUtil.select(originProbabilities.toNonIndexedArray(), random);
        return zonesCopy.get(originProbabilities.getIdForInternalIndex(destinationInternalId));
    }
}
