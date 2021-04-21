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
public final class NhbwNhboOrigin extends RandomizableConcurrentFunction<Void> {

    private final static Logger logger = Logger.getLogger(NhbwNhboOrigin.class);

    private final Purpose purpose;
    private final List<Purpose> priorPurposes;

    private final MitoOccupationStatus relatedMitoOccupationStatus;

    EnumMap<Purpose, Map<Integer, IndexedDoubleMatrix2D>> baseProbabilities;

    private final Collection<MitoHousehold> householdPartition;
    private final Map<Integer, MitoZone> zonesCopy;

    private final int carOwnershipIndex;


    public NhbwNhboOrigin(Purpose purpose, DataSet dataSet, int index, Collection<MitoHousehold> householdPartition) {
        super(MitoUtil.getRandomObject().nextLong());
        this.purpose = purpose;
        this.carOwnershipIndex = index;
        this.baseProbabilities = TripDistribution.utilityMatrices;
        this.householdPartition = householdPartition;
        this.zonesCopy = new HashMap<>(dataSet.getZones());
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


        for (MitoHousehold household : householdPartition) {
            for(MitoPerson person : household.getPersons().values()) {
                for (MitoTrip trip : person.getTripsForPurpose(purpose)) {
                    findOrigin(person,trip);
                }
            }
        }

        return null;
    }

    private void findOrigin(MitoPerson person, MitoTrip trip) {
        final List<MitoTrip> possibleBaseTrips = new ArrayList<>();
        for (Purpose purpose : priorPurposes) {
            for (MitoTrip priorTrip : person.getTripsForPurpose(purpose)) {
                if(priorTrip.getTripDestination()==null){
                    TripDistribution.countNoDestinationErrorTrips.incrementAndGet();
                    //logger.error("Trip id: " +trip.getId() + "|Purpose: " + trip.getTripPurpose() + ", select trip: " + priorTrip.getId() + " as prior trip, but it has no trip destination." + priorTrip.getTripPurpose() + "|" +priorTrip.getTripDestinationMopedZoneId());
                }else{
                    possibleBaseTrips.add(priorTrip);
                }

            }
        }

        if (!possibleBaseTrips.isEmpty()) {
            MitoTrip selectedTrip = MitoUtil.select(random, possibleBaseTrips);
            trip.setTripOrigin(selectedTrip.getTripDestination());
            trip.setTripOriginMopedZoneId(selectedTrip.getTripDestinationMopedZoneId());
            TripDistribution.countOriginPriorTrips.incrementAndGet();
            return;
        }

        if (trip.getPerson().getMitoOccupationStatus() == relatedMitoOccupationStatus &&
                trip.getPerson().getOccupation() != null) {
            trip.setTripOrigin(trip.getPerson().getOccupation());
            TripDistribution.countOriginOccupation.incrementAndGet();
            return;
        }

        final Purpose selectedPurpose = MitoUtil.select(random, priorPurposes);
        trip.setTripOrigin(findRandomOrigin(person.getHousehold(), selectedPurpose));
        TripDistribution.countOriginRandom.incrementAndGet();
    }

    private MitoZone findRandomOrigin(MitoHousehold household, Purpose priorPurpose) {
        TripDistribution.completelyRandomNhbTrips.get(purpose).incrementAndGet();
        final IndexedDoubleMatrix1D originProbabilities = baseProbabilities.get(priorPurpose).get(carOwnershipIndex).viewRow(household.getHomeZone().getId());
        final int destinationInternalId = MitoUtil.select(originProbabilities.toNonIndexedArray(), random);
        return zonesCopy.get(originProbabilities.getIdForInternalIndex(destinationInternalId));
    }
}
