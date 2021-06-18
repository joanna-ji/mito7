package de.tum.bgu.msm.data;

import org.matsim.api.core.v01.population.Person;

import java.util.HashMap;
import java.util.Map;

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
    private double lightInjuryRisk;
    private double severeInjuryRisk;
    private double fatalityRisk;
    private double physicalActivityMmetHours;
    private double matsimTravelTime;
    private double matsimTravelDistance;
    private Map<String, Double> exposureMap = new HashMap<>();


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

    public double getPhysicalActivityMmetHours() {
        return physicalActivityMmetHours;
    }

    public void setPhysicalActivityMmetHours(double mmetHours) {
        this.physicalActivityMmetHours = mmetHours;
    }

    public Map<String, Double> getExposureMap() {
        return exposureMap;
    }

    public void setExposureMap(Map<String, Double> exposureMap) {
        this.exposureMap = exposureMap;
    }

    public double getFatalityRisk() {
        return fatalityRisk;
    }

    public void setFatalityRisk(double fatalityRisk) {
        this.fatalityRisk = fatalityRisk;
    }

    public double getMatsimTravelTime() {
        return matsimTravelTime;
    }

    public void setMatsimTravelTime(double matsimTravelTime) {
        this.matsimTravelTime = matsimTravelTime;
    }

    public double getMatsimTravelDistance() {
        return matsimTravelDistance;
    }

    public void setMatsimTravelDistance(double matsimTravelDistance) {
        this.matsimTravelDistance = matsimTravelDistance;
    }
}
