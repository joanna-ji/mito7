package de.tum.bgu.msm.modules.timeOfDay;

import cern.colt.matrix.tdouble.DoubleMatrix1D;
import com.google.common.math.LongMath;
import de.tum.bgu.msm.data.*;
import de.tum.bgu.msm.modules.Module;
import de.tum.bgu.msm.resources.Properties;
import de.tum.bgu.msm.resources.Resources;
import de.tum.bgu.msm.util.MitoUtil;
import org.apache.log4j.Logger;

import java.util.EnumMap;


public final class TimeOfDayChoice extends Module {

    private static final Logger logger = Logger.getLogger(TimeOfDayChoice.class);

    private EnumMap<Purpose, EnumMap<Day, Double>> dayProbabilitiesByPurpose;
    private EnumMap<Purpose, DoubleMatrix1D> arrivalMinuteCumProbByPurpose;
    private EnumMap<Purpose, DoubleMatrix1D> durationMinuteCumProbByPurpose;
    private EnumMap<Purpose, DoubleMatrix1D> departureMinuteCumProbByPurpose;

    private final static double SPEED_WALK_M_MIN = 83.33;
    private final static double SPEED_BICYCLE_M_MIN = 200;

    private long counter = 0;
    private int issues = 0;

    public TimeOfDayChoice(DataSet dataSet) {
        super(dataSet);
    }

    @Override
    public void run() {
        dayProbabilitiesByPurpose = dataSet.getDepartureDayProbabilitiesByPurpose();
        arrivalMinuteCumProbByPurpose = dataSet.getArrivalMinuteCumProbByPurpose();
        durationMinuteCumProbByPurpose = dataSet.getDurationMinuteCumProbByPurpose();
        departureMinuteCumProbByPurpose = dataSet.getDepartureMinuteCumProbByPurpose();

        chooseDepartureTimes();
        logger.info("Time of day and week choice completed");

    }

    private void chooseDepartureTimes() {

        dataSet.getTrips().values().forEach(trip -> {

            // choose day of week
            trip.setDepartureDay(chooseDepartureDay(trip));

            // choose time of day
            if (trip.getTripOrigin() != null && trip.getTripDestination() != null
                    && trip.getTripMode() != null) {
                if (trip.getTripPurpose().equals(Purpose.RRT)){
                    trip.setDepartureInMinutes(chooseDepartureTime(trip));
                } else {
                    int arrivalTimeInMinutes = chooseArrivalTime(trip);
                    int departureTimeInMinutes = arrivalTimeInMinutes - (int) estimateTravelTimeForDeparture(trip, arrivalTimeInMinutes);
                    if(departureTimeInMinutes < 0) {
                        trip.setDepartureInMinutes(departureTimeInMinutes + 1440);
                    } else {
                        trip.setDepartureInMinutes(departureTimeInMinutes);
                    }
                    if (trip.isHomeBased()) {
                        trip.setDepartureInMinutesReturnTrip(chooseDepartureTimeForReturnTrip(trip, arrivalTimeInMinutes));
                    }
                }



            } else {
                issues++;
            }
            counter++;
            if (LongMath.isPowerOfTwo(counter)) {
                logger.info(counter + " times of day assigned");
            }
        });
        logger.warn(issues + " trips have no time of day since they have no origin, destination or mode");
    }

    private Day chooseDepartureDay(MitoTrip mitoTrip) {
        return MitoUtil.select(dayProbabilitiesByPurpose.get(mitoTrip.getTripPurpose()), MitoUtil.getRandomObject());
    }

    private int chooseDepartureTime(MitoTrip mitoTrip) {
        return MitoUtil.select(departureMinuteCumProbByPurpose.get(mitoTrip.getTripPurpose()).toArray(), MitoUtil.getRandomObject());
    }

    private int chooseArrivalTime(MitoTrip mitoTrip) {
        Purpose tripPurpose = mitoTrip.getTripPurpose();
        if(tripPurpose == Purpose.HBW || tripPurpose == Purpose.HBE) {
            MitoOccupation occupation = mitoTrip.getPerson().getOccupation();
            if(occupation != null){
                return occupation.getStartTime_min().orElseGet(() -> MitoUtil.select(arrivalMinuteCumProbByPurpose.get(tripPurpose).toArray(), MitoUtil.getRandomObject()));
            }
        }
        return MitoUtil.select(arrivalMinuteCumProbByPurpose.get(tripPurpose).toArray(), MitoUtil.getRandomObject());
    }

    private int chooseDepartureTimeForReturnTrip(MitoTrip mitoTrip, int arrivalTime) {
        Purpose tripPurpose = mitoTrip.getTripPurpose();
        int departureTime;
        if(tripPurpose == Purpose.HBW || tripPurpose == Purpose.HBE) {
            MitoOccupation occupation = mitoTrip.getPerson().getOccupation();
            if(occupation != null) {
                departureTime = occupation.getEndTime_min().orElseGet(() -> arrivalTime + MitoUtil.select(durationMinuteCumProbByPurpose.get(tripPurpose).toArray(), MitoUtil.getRandomObject()));
            } else {
                int duration = MitoUtil.select(durationMinuteCumProbByPurpose.get(mitoTrip.getTripPurpose()).toArray(), MitoUtil.getRandomObject());
                departureTime = arrivalTime + duration;
            }
        } else {
            int duration = MitoUtil.select(durationMinuteCumProbByPurpose.get(mitoTrip.getTripPurpose()).toArray(), MitoUtil.getRandomObject());
            departureTime = arrivalTime + duration;
        }
        //if departure is after midnight
        if (departureTime > 1440) {
            return departureTime - 1440;
        } else {
            return departureTime;
        }
    }

    private double estimateTravelTimeForDeparture(MitoTrip trip, double arrivalInMinutes) {
        if (trip.getTripMode().equals(Mode.walk)) {
            return dataSet.getTravelDistancesNMT().getTravelDistance(trip.getTripOrigin().getZoneId(), trip.getTripDestination().getZoneId()) / SPEED_WALK_M_MIN;
        } else if (trip.getTripMode().equals(Mode.bicycle)) {
            return dataSet.getTravelDistancesNMT().getTravelDistance(trip.getTripOrigin().getZoneId(), trip.getTripDestination().getZoneId()) / SPEED_BICYCLE_M_MIN;
        } else {
            //both transit and car use here travel times by car
            return dataSet.getTravelTimes().getTravelTime(trip.getTripOrigin(), trip.getTripDestination(), arrivalInMinutes * 60, "car");
        }
    }
}
