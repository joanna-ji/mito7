package de.tum.bgu.msm.modules.modeChoice.calculators;

import de.tum.bgu.msm.data.*;
import de.tum.bgu.msm.data.travelTimes.TravelTimes;
import de.tum.bgu.msm.modules.modeChoice.ModeChoiceCalculator;

import java.util.EnumMap;

public class AirportModeChoiceCalculator extends ModeChoiceCalculator {

///////////////////////////////////////////////// AIRPORT Mode Choice /////////////////////////////////////////////////////

    @Override
    public EnumMap<Mode, Double> calculateUtilities(Purpose purpose, MitoHousehold household, MitoPerson person, MitoZone originZone, MitoZone destinationZone, TravelTimes travelTimes, double travelDistanceAuto, double travelDistanceNMT, double peakHour_s) {

        if(purpose != Purpose.AIRPORT) {
            throw  new IllegalArgumentException("Airport mode choice calculator can only be used for airport purposes.");
        }

        double asc_autoDriver = 0.;
        double asc_autoPassenger = -0.0657 - 1.120;
        double asc_autoOther = -1.37275 - 2.606;
        double asc_bus = 2.246879 - 5.946;
        double asc_train = 2.992159 - 4.392;

        //times are in minutes
        double beta_time = -0.0002 * 60;
        double exp_time_autoDriver = 10.44896;
        double exp_time_autoPassenger = 10.44896;
        double exp_time_autoOther = 10.44896;
        double exp_time_bus = 0;
        double exp_time_train = 3.946016;

        //distance is in minutes
        double beta_distance = -0.00002;
        double exp_distance_autoDriver = 0;
        double exp_distance_autoPassenger = 1.939056;
        double exp_distance_autoOther = 4.649129;
        double exp_distance_bus = 9.662964;
        double exp_distance_train = 7.706087;

        //Order of variables in the return variable Auto driver, Auto passenger, bicyle, bus, train, tram or metro, walk

        double u_autoDriver = asc_autoDriver + exp_time_autoDriver * Math.exp(beta_time * travelTimes.getTravelTime(originZone, destinationZone, peakHour_s, "car")) +
                exp_distance_autoDriver * Math.exp(beta_distance * travelDistanceAuto);
        double u_autoPassenger = asc_autoPassenger + exp_time_autoPassenger * Math.exp(beta_time * travelTimes.getTravelTime(originZone, destinationZone, peakHour_s, "car")) +
                exp_distance_autoPassenger * Math.exp(beta_distance * travelDistanceAuto);
        double u_autoOther = asc_autoOther + exp_time_autoOther * Math.exp(beta_time * travelTimes.getTravelTime(originZone, destinationZone, peakHour_s, "car")) +
                exp_distance_autoOther * Math.exp(beta_distance * travelDistanceAuto);
        double u_bus = asc_bus + exp_time_bus * Math.exp(beta_time * travelTimes.getTravelTime(originZone, destinationZone, peakHour_s, "bus")) +
                exp_distance_bus * Math.exp(beta_distance * travelDistanceAuto);
        double u_train = asc_train + exp_time_train * Math.exp(beta_time * travelTimes.getTravelTime(originZone, destinationZone, peakHour_s, "train")) +
                exp_distance_train * Math.exp(beta_distance * travelDistanceAuto);

        //Auto driver, Auto passenger, bicyle, bus, train, tram or metro, walk

        EnumMap<Mode, Double> utilities = new EnumMap<>(Mode.class);
        utilities.put(Mode.autoDriver, Math.log(Math.exp(u_autoDriver) + Math.exp(u_autoOther)));
        utilities.put(Mode.autoPassenger, u_autoPassenger);
        utilities.put(Mode.bicycle, Double.MIN_VALUE);
        utilities.put(Mode.bus, u_bus);
        utilities.put(Mode.train, u_train);
        utilities.put(Mode.tramOrMetro, Double.MIN_VALUE);
        utilities.put(Mode.walk, Double.MIN_VALUE);

        return utilities;
    }
}
