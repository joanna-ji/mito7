package de.tum.bgu.msm.data;

import org.apache.log4j.Logger;

import java.util.List;

/**
 * Holds person objects for the Microsimulation Transport Orchestrator (MITO)
 * @author Rolf Moeckel
 * Created on June 8, 2017 in Munich, Germany
 *
 */
public class MitoPerson implements Id {

    private static final Logger logger = Logger.getLogger(MitoPerson.class);

    private final int id;
    private final MitoGender mitoGender;
    private final MitoOccupationStatus mitoOccupationStatus;
    private final MitoOccupation occupation;
    private final int age;
    private final boolean driversLicense;

    private  MitoHousehold household;

    private List<MitoTrip> trips;

    public MitoPerson(int id, MitoOccupationStatus mitoOccupationStatus, MitoOccupation occupation, int age, MitoGender mitoGender, boolean driversLicense) {
        this.id = id;
        this.mitoOccupationStatus = mitoOccupationStatus;
        this.occupation = occupation;
        this.age = age;
        this.mitoGender = mitoGender;
        this.driversLicense = driversLicense;
    }

    public MitoOccupation getOccupation() {
        return occupation;
    }

    public MitoOccupationStatus getMitoOccupationStatus() {
        return mitoOccupationStatus;
    }

    public MitoHousehold getHousehold() {
        return household;
    }

    public void setHousehold(MitoHousehold household) {
        this.household = household;
    }

    @Override
    public int getId() {
        return this.id;
    }

    public int getAge() {
        return age;
    }

    public MitoGender getMitoGender() {
        return mitoGender;
    }

    public boolean hasDriversLicense() {
        return driversLicense;
    }

    public List<MitoTrip> getTrips() {
        return trips;
    }

    public void setTrips(List<MitoTrip> trips) {
        this.trips = trips;
    }

    public void addTrip(MitoTrip trip) { trips.add(trip); }

    @Override
    public int hashCode() {
        return id;
    }
}
