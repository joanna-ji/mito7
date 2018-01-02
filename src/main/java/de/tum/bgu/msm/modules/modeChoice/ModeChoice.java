package de.tum.bgu.msm.modules.modeChoice;

import com.google.common.collect.ArrayTable;
import com.google.common.collect.Table;
import de.tum.bgu.msm.data.*;
import de.tum.bgu.msm.modules.Module;


import de.tum.bgu.msm.util.concurrent.ConcurrentFunction;
import de.tum.bgu.msm.util.concurrent.ConcurrentFunctionExecutor;
import de.tum.bgu.msm.util.concurrent.RandomizableConcurrentFunction;
import org.apache.log4j.Logger;

import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class ModeChoice extends Module {

    private final static Logger logger = Logger.getLogger(ModeChoice.class);

    private final ModeChoiceJSCalculator calculator;

    private static final int NUMBER_OF_MOTORIZED_MODES = 5;

    private Table<Purpose, Integer, double[]> modeChoiceProbabilities;


    public ModeChoice(DataSet dataSet) {
        super(dataSet);
        Reader reader = new InputStreamReader(this.getClass().getResourceAsStream("ModeChoice"));
        calculator = new ModeChoiceJSCalculator(reader);
        modeChoiceProbabilities = ArrayTable.create(Arrays.asList(Purpose.values()), dataSet.getTrips().keySet());
    }

    @Override
    public void run() {
        calculateChoiceProbabilities();
    }

    private void calculateChoiceProbabilities(){
        logger.info(" Calculating mode choice probabilities for each trip. Modes considered - 1. Auto driver, 2. Auto passenger, 3. Bicycle, 4. Bus, 5. Train, 6. Tram or Metro, 7. Walk ");
        ConcurrentFunctionExecutor executor = new ConcurrentFunctionExecutor();

        for (Purpose purpose : Purpose.values()){
            executor.addFunction(new ConcurrentFunction() {
                @Override
                public void execute() {
                    final Map<String,Double> travelTimeByMode = new HashMap<>(NUMBER_OF_MOTORIZED_MODES);
                    for (MitoHousehold household : dataSet.getHouseholds().values()){
                        for (MitoTrip trip : household.getTripsForPurpose(purpose)){
                            travelTimeByMode.put("autoD",dataSet.getTravelTimes("auto").getTravelTime(trip.getTripOrigin().getZoneId(), trip.getTripDestination().getZoneId()));
                            travelTimeByMode.put("autoP",dataSet.getTravelTimes("auto").getTravelTime(trip.getTripOrigin().getZoneId(), trip.getTripDestination().getZoneId()));
                            travelTimeByMode.put("bus",dataSet.getTravelTimes("auto").getTravelTime(trip.getTripOrigin().getZoneId(), trip.getTripDestination().getZoneId()));
                            travelTimeByMode.put("tramMetro",dataSet.getTravelTimes("auto").getTravelTime(trip.getTripOrigin().getZoneId(), trip.getTripDestination().getZoneId()));
                            travelTimeByMode.put("train",dataSet.getTravelTimes("auto").getTravelTime(trip.getTripOrigin().getZoneId(), trip.getTripDestination().getZoneId()));
                            final double travelDistanceAuto = dataSet.getTravelDistancesAuto().getTravelDistance(trip.getTripOrigin().getZoneId(), trip.getTripDestination().getZoneId());
                            final double travelDistanceNMT = dataSet.getTravelDistancesNMT().getTravelDistance(trip.getTripOrigin().getZoneId(), trip.getTripDestination().getZoneId());
                            modeChoiceProbabilities.put(purpose, trip.getTripId(),calculator.calculateProbabilities(household, trip.getPerson(), trip, travelTimeByMode, travelDistanceAuto, travelDistanceNMT));
                        }
                    }
                }
            });
            executor.execute();
        }
    }

    private void assignMode(double[] probabilities) {
        // Check randomizable concurrent function from TripDistributor class
    }
}
