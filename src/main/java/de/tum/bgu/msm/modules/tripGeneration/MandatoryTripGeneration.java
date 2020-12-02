package de.tum.bgu.msm.modules.tripGeneration;

import de.tum.bgu.msm.TravelDemandGenerator;
import de.tum.bgu.msm.data.DataSet;
import de.tum.bgu.msm.data.Purpose;
import de.tum.bgu.msm.modules.Module;
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

public class MandatoryTripGeneration extends Module {

    private static final Logger logger = Logger.getLogger(TravelDemandGenerator.class);
    private final EnumSet<Purpose> MANDATORY_PURPOSES = EnumSet.of(HBW, HBE);

    private double scaleFactorForTripGeneration;
    private final TripsByPurposeGeneratorFactory tripsByPurposeGeneratorFactory;

    public MandatoryTripGeneration(DataSet dataSet, TripsByPurposeGeneratorFactory tripsByPurposeGeneratorFactory) {
        super(dataSet);
        scaleFactorForTripGeneration = Resources.instance.getDouble(Properties.SCALE_FACTOR_FOR_TRIP_GENERATION, 1.0);
        this.tripsByPurposeGeneratorFactory = tripsByPurposeGeneratorFactory;
    }

    @Override
    public void run() {
        logger.info("  Started microscopic trip generation model.");
        generateRawTrips();
        calculateAttractions();
        balanceTrips();
        logger.info("  Completed microscopic trip generation model.");
    }

    private void generateRawTrips() {
        RawTripGenerator rawTripGenerator = new RawTripGenerator(dataSet, MANDATORY_PURPOSES, tripsByPurposeGeneratorFactory);
        rawTripGenerator.run(scaleFactorForTripGeneration);
    }

    private void calculateAttractions() {
        AttractionCalculator calculator = new AttractionCalculator(dataSet, MANDATORY_PURPOSES);
        calculator.run();
    }

    private void balanceTrips() {
        TripBalancer tripBalancer = new TripBalancer(dataSet, MANDATORY_PURPOSES);
        tripBalancer.run();
    }

}
