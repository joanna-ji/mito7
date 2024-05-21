package de.tum.bgu.msm.run.scenarios.socialNetwork;

import de.tum.bgu.msm.data.DataSet;
import de.tum.bgu.msm.data.Purpose;
import de.tum.bgu.msm.io.output.SummarizeData;
import de.tum.bgu.msm.io.output.SummarizeDataToVisualize;
import de.tum.bgu.msm.io.output.TripGenerationWriter;
import de.tum.bgu.msm.modules.Module;
import de.tum.bgu.msm.modules.modeChoice.ModeChoice;
import de.tum.bgu.msm.modules.modeChoice.ModeRestrictionChoice;
import de.tum.bgu.msm.modules.plansConverter.MatsimPopulationGenerator;
import de.tum.bgu.msm.modules.plansConverter.externalFlows.LongDistanceTraffic;
import de.tum.bgu.msm.modules.scaling.TripScaling;
import de.tum.bgu.msm.modules.timeOfDay.TimeOfDayChoice;
import de.tum.bgu.msm.modules.tripDistribution.TripDistribution;
import de.tum.bgu.msm.modules.tripDistribution.coordinatedDestination.DestinationCoordinationWithCliques2;
import de.tum.bgu.msm.modules.tripGeneration.DiscretionaryTripGeneration;
import de.tum.bgu.msm.modules.tripGeneration.MandatoryTripGeneration;
import de.tum.bgu.msm.modules.tripGeneration.TripsByPurposeGeneratorFactoryHurdle;
import de.tum.bgu.msm.resources.Properties;
import de.tum.bgu.msm.resources.Resources;
import org.apache.log4j.Logger;

/**
 * Generates travel demand for the Microscopic Transport Orchestrator (MITO)
 *
 * @author Rolf Moeckel
 * Created on Sep 18, 2016 in Munich, Germany
 */
public final class TravelDemandGenerator2017SocialNetwork {

    private static final Logger logger = Logger.getLogger(TravelDemandGenerator2017SocialNetwork.class);
    private final DataSet dataSet;

    private final Module mandatoryTripGeneration;
    private final Module tripDistribution;
    private final Module discretionaryTripGeneration;
    private final Module modeRestriction;
    private final Module modeChoice;
    private final Module timeOfDayChoice;
    private final Module tripScaling;
    private final Module matsimPopulationGenerator;
    private final Module longDistanceTraffic;
    private final Module destinationCoordination;
    private final Module destinationCoordinationCliques;

    private TravelDemandGenerator2017SocialNetwork(
            DataSet dataSet,
            Module mandatoryTripGeneration,
            Module tripDistribution,
            Module discretionaryTripGeneration,
            Module modeRestriction,
            Module modeChoice,
            Module timeOfDayChoice,
            Module tripScaling,
            Module matsimPopulationGenerator,
            Module longDistanceTraffic,
            Module destinationCoordination,
            Module destinationCoordinationCliques) {

        this.dataSet = dataSet;
        this.mandatoryTripGeneration = mandatoryTripGeneration;
        this.tripDistribution = tripDistribution;
        this.discretionaryTripGeneration = discretionaryTripGeneration;
        this.modeRestriction = modeRestriction;
        this.modeChoice = modeChoice;
        this.timeOfDayChoice = timeOfDayChoice;
        this.tripScaling = tripScaling;
        this.matsimPopulationGenerator = matsimPopulationGenerator;
        this.longDistanceTraffic = longDistanceTraffic;
        this.destinationCoordination = destinationCoordination;
        this.destinationCoordinationCliques = destinationCoordinationCliques;
    }


    public static class Builder {

        private final DataSet dataSet;

        private Module mandatoryTripGeneration;
        private Module tripDistribution;
        private Module dominantCommuteMode;
        private Module discretionaryTripGeneration;
        private Module modeRestriction;
        private Module modeChoice;
        private Module timeOfDayChoice;
        private Module tripScaling;
        private Module matsimPopulationGenerator;
        private Module longDistanceTraffic;
        private Module destinationCoordination;
        private Module destinationCoordinationCliques;

        public Builder(DataSet dataSet) {
            this.dataSet = dataSet;
            mandatoryTripGeneration = new MandatoryTripGeneration(dataSet, new TripsByPurposeGeneratorFactoryHurdle());
            tripDistribution = new TripDistribution(dataSet);
            discretionaryTripGeneration = new DiscretionaryTripGeneration(dataSet, new TripsByPurposeGeneratorFactoryHurdle());
            modeRestriction = new ModeRestrictionChoice(dataSet);
            modeChoice = new ModeChoice(dataSet);
            timeOfDayChoice = new TimeOfDayChoice(dataSet);
            tripScaling = new TripScaling(dataSet);
            matsimPopulationGenerator = new MatsimPopulationGenerator(dataSet);
            if (Resources.instance.getBoolean(Properties.ADD_EXTERNAL_FLOWS, false)) {
                longDistanceTraffic = new LongDistanceTraffic(dataSet, Double.parseDouble(Resources.instance.getString(Properties.TRIP_SCALING_FACTOR)));
            }
            //toggle following two lines on/off to run destination coordination with or without cliques
            //destinationCoordination = new DestinationCoordination(dataSet); //without cliques
            destinationCoordinationCliques = new DestinationCoordinationWithCliques2(dataSet); //with cliques
        }

        public TravelDemandGenerator2017SocialNetwork build() {
            return new TravelDemandGenerator2017SocialNetwork(dataSet,
                    mandatoryTripGeneration,
                    tripDistribution,
                    discretionaryTripGeneration,
                    modeRestriction,
                    modeChoice,
                    timeOfDayChoice,
                    tripScaling,
                    matsimPopulationGenerator,
                    longDistanceTraffic,
                    destinationCoordination,
                    destinationCoordinationCliques);
        }

        public void setMandatoryTripGeneration(Module mandatoryTripGeneration) {
            this.mandatoryTripGeneration = mandatoryTripGeneration;
        }

        public void setTripDistribution(Module tripDistribution) {
            this.tripDistribution = tripDistribution;
        }

        public void setDiscretionaryTripGeneration(Module discretionaryTripGeneration) {
            this.discretionaryTripGeneration = discretionaryTripGeneration;
        }

        public void setModeRestriction(Module modeRestriction) {
            this.modeRestriction = modeRestriction;
        }

        public void setModeChoice(Module modeChoice) {
            this.modeChoice = modeChoice;
        }

        public void setTimeOfDayChoice(Module timeOfDayChoice) {
            this.timeOfDayChoice = timeOfDayChoice;
        }

        public void setTripScaling(Module tripScaling) {
            this.tripScaling = tripScaling;
        }

        public void setMatsimPopulationGenerator(Module matsimPopulationGenerator) {
            this.matsimPopulationGenerator = matsimPopulationGenerator;
        }

        public void setLongDistanceTraffic(Module longDistanceTraffic) {
            this.longDistanceTraffic = longDistanceTraffic;
        }

        public DataSet getDataSet() {
            return dataSet;
        }

        public Module getMandatoryTripGeneration() {
            return mandatoryTripGeneration;
        }

        public Module getTripDistribution() {
            return tripDistribution;
        }

        public Module getModeChoice() {
            return modeChoice;
        }

        public Module getTimeOfDayChoice() {
            return timeOfDayChoice;
        }

        public Module getTripScaling() {
            return tripScaling;
        }

        public Module getMatsimPopulationGenerator() {
            return matsimPopulationGenerator;
        }

        public Module getLongDistanceTraffic() {
            return longDistanceTraffic;
        }
    }

    public void generateTravelDemand(String scenarioName) {

        long startTime = System.currentTimeMillis();
        logger.info("Running Module: Mandatory Microscopic Trip Generation");
        mandatoryTripGeneration.run();
        if (dataSet.getTrips().isEmpty()) {
            logger.warn("No trips created. End of program.");
            return;
        }
        long endTime = System.currentTimeMillis();
        double duration = (endTime - startTime) / 1000;
        logger.info("Completed TG in " + duration + " seconds");

        logger.info("Running Module: Trip distribution (mandatory)");
        tripDistribution.run();

        logger.info("Running Module: Discretionary trip generation");
        discretionaryTripGeneration.run();

        logger.info("Running Module: Mode Restriction");
        modeRestriction.run();

        logger.info("Running Module: Trip distribution (discretionary)");
        tripDistribution.run();

        logger.info("Running Module: Trip to Mode Assignment (Mode Choice)");
        modeChoice.run();

        logger.info("Running time of day choice");
        timeOfDayChoice.run();

        logger.info("Running trip scaling");
        tripScaling.run();

        matsimPopulationGenerator.run();

        if (Resources.instance.getBoolean(Properties.ADD_EXTERNAL_FLOWS, false)) {
            longDistanceTraffic.run();
        }

        TripGenerationWriter.writeTripsByPurposeAndZone(dataSet, scenarioName);
        SummarizeDataToVisualize.writeFinalSummary(dataSet, scenarioName);

        if (Resources.instance.getBoolean(Properties.PRINT_MICRO_DATA, true)) {
            SummarizeData.writeOutSyntheticPopulationWithTrips(dataSet);
            SummarizeData.writeAllTrips(dataSet, scenarioName);
        }
        if (Resources.instance.getBoolean(Properties.CREATE_CHARTS, true)) {
            //DistancePlots.writeDistanceDistributions(dataSet, scenarioName);
            //ModeChoicePlots.writeModeChoice(dataSet, scenarioName);
            SummarizeData.writeCharts(dataSet, scenarioName);
        }
        if (Resources.instance.getBoolean(Properties.WRITE_MATSIM_POPULATION, true)) {
            //SummarizeData.writeMatsimPlans(dataSet, scenarioName);
        }
    }
}
