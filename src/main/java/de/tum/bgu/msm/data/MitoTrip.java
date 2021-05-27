package de.tum.bgu.msm.data;

import org.matsim.api.core.v01.population.Person;

/**
 * Holds trip objects for the Microsimulation Transport Orchestrator (MITO)
 *
 * @author Rolf Moeckel
 * Created on Mar 26, 2017 in Munich, Germany
 */
public class MitoTrip implements Id {

    private final int tripId;
    private final Purpose tripPurpose;

    private Location tripOrigin;
    private Location tripDestination;

    private MitoPerson person;

    private Mode tripMode;

    private Day departureDay;
    private int departureInMinutes;
    private int departureInMinutesReturnTrip = -1;

    private int tripOriginMopedZoneId;
    private int tripDestinationMopedZoneId;
    private double mopedTripDistance;

    private Person matsimPerson;

    //for health model
    private double matsimTravelTimeInMinutes;
    private double lightInjuryRisk;
    private double severeInjuryRisk;
    private double pmExposure;
    private double no2Exposure;

    public MitoTrip(int tripId, Purpose tripPurpose) {
        this.tripId = tripId;
        this.tripPurpose = tripPurpose;
    }

    @Override
    public int getId() {
        return tripId;
    }

    public Location getTripOrigin() {
        return tripOrigin;
    }

    public void setTripOrigin(Location origin) {
        this.tripOrigin = origin;
    }

    public Purpose getTripPurpose() {
        return tripPurpose;
    }

    public Location getTripDestination() {
        return this.tripDestination;
    }

    public void setTripDestination(Location destination) {
        this.tripDestination = destination;
    }

    public MitoPerson getPerson() {
        return person;
    }

    public void setPerson(MitoPerson person) {
        this.person = person;
    }

    public Mode getTripMode() {
        return tripMode;
    }

    public void setTripMode(Mode tripMode) {
        this.tripMode = tripMode;
    }

    public void setDepartureDay(Day departureDay) {
        this.departureDay = departureDay;
    }

    public void setDepartureInMinutes(int departureInMinutes) {
        this.departureInMinutes = departureInMinutes;
    }

    public void setDepartureInMinutesReturnTrip(int departureInMinutesReturnTrip) {
        this.departureInMinutesReturnTrip = departureInMinutesReturnTrip;
    }

    public Day getDepartureDay() {
        return departureDay;
    }

    public int getDepartureTimeInMinutes() {
        return departureInMinutes;
    }

    public int getDepartureInMinutesReturnTrip() {
        return departureInMinutesReturnTrip;
    }

    public int getTripId() {
        return tripId;
    }

    public Person getMatsimPerson() {
        return matsimPerson;
    }

    public void setMatsimPerson(Person matsimPerson) {
        this.matsimPerson = matsimPerson;
    }

    public boolean isHomeBased() {
        return  !this.getTripPurpose().equals(Purpose.RRT) &&
                !this.getTripPurpose().equals(Purpose.NHBW) &&
                !this.getTripPurpose().equals(Purpose.NHBO) &&
                !this.getTripPurpose().equals(Purpose.AIRPORT);
    }

    @Override
    public String toString() {
        return "Trip [id: " + this.tripId + " purpose: " + this.tripPurpose + "]";
    }

    @Override
    public int hashCode() {
        return tripId;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof MitoTrip) {
            return tripId == ((MitoTrip) o).tripId;
        } else {
            return false;
        }
    }

    public int getTripOriginMopedZoneId() {
        return tripOriginMopedZoneId;
    }

    public void setTripOriginMopedZoneId(int tripOriginMopedZoneId) {
        this.tripOriginMopedZoneId = tripOriginMopedZoneId;
    }

    public int getTripDestinationMopedZoneId() {
        return tripDestinationMopedZoneId;
    }

    public void setTripDestinationMopedZoneId(int tripDestinationMopedZoneId) {
        this.tripDestinationMopedZoneId = tripDestinationMopedZoneId;
    }

    public double getMopedTripDistance() {
        return mopedTripDistance;
    }

    public void setMopedTripDistance(double mopedTripDistance) {
        this.mopedTripDistance = mopedTripDistance;
    }

    public double getLightInjuryRisk() {
        return lightInjuryRisk;
    }

    public void setLightInjuryRisk(double lightInjuryRisk) {
        this.lightInjuryRisk = lightInjuryRisk;
    }

    public double getSevereInjuryRisk() {
        return severeInjuryRisk;
    }

    public void setSevereInjuryRisk(double severeInjuryRisk) {
        this.severeInjuryRisk = severeInjuryRisk;
    }

    public double getPmExposure() {
        return pmExposure;
    }

    public void setPmExposure(double pmExposure) {
        this.pmExposure = pmExposure;
    }

    public double getNo2Exposure() {
        return no2Exposure;
    }

    public void setNo2Exposure(double no2Exposure) {
        this.no2Exposure = no2Exposure;
    }

    public double getMatsimTravelTimeInMinutes() {
        return matsimTravelTimeInMinutes;
    }

    public void setMatsimTravelTimeInMinutes(double matsimTravelTimeInMinutes) {
        this.matsimTravelTimeInMinutes = matsimTravelTimeInMinutes;
    }
}
