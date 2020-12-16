package de.tum.bgu.msm.modules.modeChoice;

import de.tum.bgu.msm.data.*;
import de.tum.bgu.msm.io.input.readers.CoefficientReader;
import de.tum.bgu.msm.modules.Module;
import de.tum.bgu.msm.resources.Resources;
import de.tum.bgu.msm.util.MitoUtil;
import de.tum.bgu.msm.util.concurrent.ConcurrentExecutor;
import de.tum.bgu.msm.util.concurrent.RandomizableConcurrentFunction;
import org.apache.commons.math3.analysis.function.Logit;
import org.apache.log4j.Logger;

import java.nio.file.Path;
import java.util.*;

import static de.tum.bgu.msm.data.ModeRestriction.*;


public class ModeRestrictionChoice extends Module {

    private final static Logger logger = Logger.getLogger(ModeChoice.class);
    private final static Path modeRestrictionCoefPath = Resources.instance.getModeRestrictionCoefficients();
    private final EnumMap<ModeRestriction, Map<String, Double>> coefficients = new EnumMap<>(ModeRestriction.class);


    public ModeRestrictionChoice(DataSet dataSet) {
        super(dataSet);
        for(ModeRestriction modeRestriction : ModeRestriction.values()) {
            coefficients.put(modeRestriction, new CoefficientReader(dataSet, modeRestriction, modeRestrictionCoefPath).readCoefficients());
        }
    }

    @Override
    public void run() {
        logger.info(" Calculating mode restriction.");
        modeRestriction();
    }

    private void modeRestriction() {
        ConcurrentExecutor<Void> executor = ConcurrentExecutor.fixedPoolService(Purpose.values().length);
        executor.addTaskToQueue(new modeRestrictionChoice(dataSet, coefficients));
        executor.execute();
    }

    static class modeRestrictionChoice extends RandomizableConcurrentFunction<Void> {

        private final DataSet dataSet;
        private final LogitCalculator logitCalculator;
        private int nonMobilePersons;



        modeRestrictionChoice(DataSet dataSet, EnumMap<ModeRestriction, Map<String, Double>> coefficients) {
            super(MitoUtil.getRandomObject().nextLong());
            this.dataSet = dataSet;
            this.logitCalculator = new LogitCalculator(dataSet, coefficients);
        }

        @Override
        public Void call() {
            nonMobilePersons = 0;
            try {
                for (MitoPerson person : dataSet.getMobilePersons().values()) {
                    if(person.getTrips().size() == 0) {
                        nonMobilePersons++;
                    } else {
                        chooseModeRestriction(person, logitCalculator.getProbabilities(person, determineAvailability(person)));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            logger.info(nonMobilePersons + " non-mobile persons skipped");
            return null;
        }

        private EnumSet<ModeRestriction> determineAvailability(MitoPerson person) {

            EnumSet<ModeRestriction> availableChoices = EnumSet.noneOf(ModeRestriction.class);
            Mode dominantCommuteMode = person.getDominantCommuteMode();

            boolean commuter = dominantCommuteMode != null;
            boolean hasRrtTrips = person.getTripsForPurpose(Purpose.RRT).size() > 0;

            EnumSet<ModeRestriction> possibleOptions;
            if(hasRrtTrips) {
                possibleOptions = EnumSet.of(autoPtWalk, autoPtWalkCycle, ptWalk, ptWalkCycle);
            } else {
                possibleOptions = EnumSet.allOf(ModeRestriction.class);
            }

            if(commuter) {
                for (ModeRestriction modeRestriction : possibleOptions) {
                    if (modeRestriction.getRestrictedModeSet().contains(dominantCommuteMode)) {
                        availableChoices.add(modeRestriction);
                    }
                }
            } else {
                availableChoices.addAll(possibleOptions);
            }
            return availableChoices;
        }

        private void chooseModeRestriction(MitoPerson person, EnumMap<ModeRestriction, Double> probabilities) {
            double sum = MitoUtil.getSum(probabilities.values());
            if (Math.abs(sum - 1) > 0.1) {
                logger.warn("Mode probabilities don't add to 1 for person " + person.getId());
            }
            if (sum > 0) {
                final ModeRestriction select = MitoUtil.select(probabilities, random);
                person.setModeRestriction(select);
            } else {
                logger.error("Zero/negative probabilities for person " + person.getId());
                person.setModeRestriction(null);
            }
        }
    }
}