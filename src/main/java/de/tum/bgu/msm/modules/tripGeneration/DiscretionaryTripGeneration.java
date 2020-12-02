package de.tum.bgu.msm.modules.tripGeneration;

import de.tum.bgu.msm.TravelDemandGenerator;
import de.tum.bgu.msm.data.DataSet;
import de.tum.bgu.msm.data.Purpose;
import de.tum.bgu.msm.modules.Module;
import de.tum.bgu.msm.modules.tripGeneration.airport.AirportTripGeneration;
import de.tum.bgu.msm.resources.Properties;
import de.tum.bgu.msm.resources.Resources;
import org.apache.log4j.Logger;

import java.util.EnumSet;

import static de.tum.bgu.msm.data.Purpose.*;

/**
 * Runs trip generation for the Transport in Microsimulation Orchestrator (MITO)
 * @author Rolf Moeckel
 * Created on Sep 18, 2016 in Munich, Germany
 *
 */

public class DiscretionaryTripGeneration extends Module {

    private static final Logger logger = Logger.getLogger(TravelDemandGenerator.class);
    private final EnumSet<Purpose> DISCRETIONARY_PURPOSES = EnumSet.of(HBS, HBR, HBO, RRT, NHBW, NHBO);
    private final boolean addAirportDemand;

    private double scaleFactorForTripGeneration;
    private final TripsByPurposeGeneratorFactory tripsByPurposeGeneratorFactory;

    public DiscretionaryTripGeneration(DataSet dataSet, TripsByPurposeGeneratorFactory tripsByPurposeGeneratorFactory) {
        super(dataSet);
        addAirportDemand = Resources.instance.getBoolean(Properties.ADD_AIRPORT_DEMAND, false);
        scaleFactorForTripGeneration = Resources.instance.getDouble(Properties.SCALE_FACTOR_FOR_TRIP_GENERATION, 1.0);
        this.tripsByPurposeGeneratorFactory = tripsByPurposeGeneratorFactory;
    }

    @Override
    public void run() {
        logger.info("  Started discretionary trip generation model.");
        generateRawTrips();
        if (addAirportDemand){
            generateAirportTrips(scaleFactorForTripGeneration);
        }
        calculateAttractions();
        balanceTrips();
        logger.info("  Completed discretionary trip generation model.");
    }

    private void generateRawTrips() {
        RawTripGenerator rawTripGenerator = new RawTripGenerator(dataSet, DISCRETIONARY_PURPOSES, tripsByPurposeGeneratorFactory);
        rawTripGenerator.run(scaleFactorForTripGeneration);
    }

    private void generateAirportTrips(double scaleFactorForTripGeneration) {
        AirportTripGeneration airportTripGeneration = new AirportTripGeneration(dataSet);
        airportTripGeneration.run(scaleFactorForTripGeneration);
    }

    private void calculateAttractions() {
        AttractionCalculator calculator = new AttractionCalculator(dataSet, DISCRETIONARY_PURPOSES);
        calculator.run();
    }

    private void balanceTrips() {
        TripBalancer tripBalancer = new TripBalancer(dataSet, DISCRETIONARY_PURPOSES);
        tripBalancer.run();
    }

}
