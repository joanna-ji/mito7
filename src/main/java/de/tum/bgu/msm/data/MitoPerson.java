package de.tum.bgu.msm.data;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Holds person objects for the Microsimulation Transport Orchestrator (MITO)
 * @author Rolf Moeckel
 * Created on June 8, 2017 in Munich, Germany
 *
 */
public class MitoPerson implements Id {

    private final int id;
    private final MitoHousehold household;
    private final MitoGender mitoGender;
    private final MitoOccupationStatus mitoOccupationStatus;
    private final MitoOccupation occupation;
    private final int age;
    private final boolean driversLicense;
    private final boolean bicycle;

    private final EnumMap<Purpose, List<MitoTrip>> tripsByPurpose = new EnumMap<>(Purpose.class);
    private final EnumMap<Purpose, Double> travelTimeBudgetByPurpose= new EnumMap<>(Purpose.class);
    private double totalTravelTimeBudget = 0.;

    private Mode dominantCommuteMode;
    private ModeRestriction modeRestriction;

    private boolean transitPass;
    private boolean disable;

    public MitoPerson(int id, MitoHousehold hh, MitoOccupationStatus mitoOccupationStatus, MitoOccupation occupation, int age, MitoGender mitoGender, boolean driversLicense, boolean ownBicycle) {
        this.id = id;
        this.household = hh;
        this.mitoOccupationStatus = mitoOccupationStatus;
        this.occupation = occupation;
        this.age = age;
        this.mitoGender = mitoGender;
        this.driversLicense = driversLicense;
        this.bicycle = ownBicycle;
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

    public boolean hasBicycle() { return bicycle; }

    public synchronized void setTripsByPurpose(List<MitoTrip> trips, Purpose purpose) {
        tripsByPurpose.put(purpose, trips);
    }

    public void setDominantCommuteMode(Mode mode) {
        this.dominantCommuteMode = mode;
    }

    public Mode getDominantCommuteMode() { return dominantCommuteMode; }

    public void setModeRestriction(ModeRestriction modeRestriction) {this.modeRestriction = modeRestriction; }

    public ModeRestriction getModeRestriction() {return modeRestriction; }

    public List<MitoTrip> getTripsForPurpose(Purpose purpose) {
        if(tripsByPurpose.get(purpose) != null) {
            return tripsByPurpose.get(purpose);
        } else {
            return Collections.emptyList();
        }
    }

    public boolean hasTripsForPurpose(Purpose purpose) {
        return getTripsForPurpose(purpose).size() > 0;
    }

    public List<MitoTrip> getTrips() {
        return tripsByPurpose.values().stream().flatMap(List::stream).collect(Collectors.toList());
    }

    public void addTrip(MitoTrip trip) {
        Purpose purpose = trip.getTripPurpose();
        if(tripsByPurpose.get(purpose) != null) {
            tripsByPurpose.get(purpose).add(trip);
        }
        else {
            tripsByPurpose.put(purpose,new ArrayList<>(Arrays.asList(trip)));
        }
    }

    public synchronized void setTravelTimeBudgetByPurpose(Purpose purpose, double budget) {
        this.travelTimeBudgetByPurpose.put(purpose, budget);
    }

    public synchronized void setTravelTimeBudgetByPurpose(String purpose, double budget) {
        if(purpose.equals("Total")) {
            this.totalTravelTimeBudget = budget;
        } else {
            this.travelTimeBudgetByPurpose.put(Purpose.valueOf(purpose), budget);
        }
    }

    public double getTotalTravelTimeBudget() {
        return totalTravelTimeBudget;
    }

    public double getTravelTimeBudgetForPurpose(Purpose purpose) {
        return travelTimeBudgetByPurpose.get(purpose) == null ? 0. : travelTimeBudgetByPurpose.get(purpose) ;
    }

    public boolean hasTransitPass() {
        return transitPass;
    }

    public boolean isDisable() {
        return disable;
    }



    @Override
    public int hashCode() {
        return id;
    }
}
