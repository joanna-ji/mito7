package de.tum.bgu.msm.modules.travelTimeBudget;

import de.tum.bgu.msm.data.*;
import de.tum.bgu.msm.data.travelTimes.TravelTimes;
import de.tum.bgu.msm.resources.Properties;
import de.tum.bgu.msm.resources.Resources;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.TransportMode;

import java.util.Collection;

public class MandatoryBudgetCalculator implements Runnable {

    private static final Logger logger = Logger.getLogger(MandatoryTravelTimeBudgetModule.class);

    private final double defaultBudget;
    private final Collection<MitoHousehold> households;
    private final Purpose purpose;
    private final MitoOccupationStatus mitoOccupationStatus;
    private final TravelTimes travelTimes;
    private final double timeOfDay;
    private int defaultBudgeted = 0;

    MandatoryBudgetCalculator(Collection<MitoHousehold> households, Purpose purpose, TravelTimes travelTimes, double timeOfDay) {
        this.households = households;
        this.purpose = purpose;
        this.defaultBudget = Resources.instance.getDouble(Properties.DEFAULT_BUDGET + purpose, 30.);
        this.travelTimes = travelTimes;
        this.timeOfDay = timeOfDay;
        if(purpose == Purpose.HBW) {
            mitoOccupationStatus = MitoOccupationStatus.WORKER;
        } else if(purpose == Purpose.HBE) {
            mitoOccupationStatus = MitoOccupationStatus.STUDENT;
        } else {
            throw new RuntimeException("MandatoryBudgetCalculator can only be initialized with HBW or HBE purpose!");
        }
    }

    @Override
    public void run() {
        for (MitoHousehold household : households) {
            double householdBudget = 0;
            for (MitoPerson person : household.getPersons().values()) {
                double personBudget = 0;
                for (MitoTrip trip : person.getTripsForPurpose(purpose)) {
                    if (specifiedByOccupation(trip)) {
                        //Multiply by 2, as the budget should contain the return trip of home based trips as well
                        personBudget += 2 * travelTimes.getTravelTime(household.getHomeZone(),
                                trip.getPerson().getOccupation(), timeOfDay, TransportMode.car);
                    } else {
                        personBudget += defaultBudget;
                        defaultBudgeted ++;
                    }
                }
                person.setTravelTimeBudgetByPurpose(purpose, personBudget);
                householdBudget += personBudget;
            }
            household.setTravelTimeBudgetByPurpose(purpose, householdBudget);
        }
        if (defaultBudgeted > 0) {
            logger.warn("There have been " + defaultBudgeted + " " + purpose
                    + " trips that were accounted for with the default budget of "
                    + defaultBudget + " minutes in the " + purpose + " travel time budgets"
                    + " because no " + mitoOccupationStatus + " was assigned (or occupation zone missing).");
        }
    }

    private boolean specifiedByOccupation(MitoTrip trip) {
        return trip.getPerson().getMitoOccupationStatus().equals(mitoOccupationStatus)
                && trip.getPerson().getOccupation() != null
                && trip.getPerson().getOccupation().getOccupationZone() != null;
    }
}
