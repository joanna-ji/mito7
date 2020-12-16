package de.tum.bgu.msm.modules.modeChoice;

import de.tum.bgu.msm.data.*;
import de.tum.bgu.msm.io.input.readers.CoefficientReader;
import de.tum.bgu.msm.modules.Module;
import de.tum.bgu.msm.resources.Resources;
import de.tum.bgu.msm.util.MitoUtil;
import de.tum.bgu.msm.util.concurrent.ConcurrentExecutor;
import de.tum.bgu.msm.util.concurrent.RandomizableConcurrentFunction;
import org.apache.log4j.Logger;

import java.nio.file.Path;
import java.util.*;

import static de.tum.bgu.msm.data.Mode.*;

public class DominantCommuteMode extends Module {

    private final static Logger logger = Logger.getLogger(ModeChoice.class);
    private final static EnumSet<Mode> MODES = EnumSet.of(autoDriver,autoPassenger,publicTransport,bicycle,walk);
    private final static Path dominantModeCoefPath = Resources.instance.getDominantCommuteModeCoefficients();
    private final EnumMap<Mode, Map<String, Double>> coefficients = new EnumMap<>(Mode.class);

    public DominantCommuteMode(DataSet dataSet) {
        super(dataSet);
        for(Mode mode : MODES) {
            coefficients.put(mode, new CoefficientReader(dataSet, mode, dominantModeCoefPath).readCoefficients());
        }
    }

    @Override
    public void run() {
        logger.info(" Calculating dominant commute mode probabilities. Modes considered - 1. Auto driver, 2. Auto passenger, 3. Public Transport, 4. Bicycle, 5. Walk");
        dominantCommuteMode();
    }

    private void dominantCommuteMode() {
        ConcurrentExecutor<Void> executor = ConcurrentExecutor.fixedPoolService(Purpose.values().length);
        executor.addTaskToQueue(new dominantCommuteModeChoice(dataSet, coefficients));
        executor.execute();
    }

    static class dominantCommuteModeChoice extends RandomizableConcurrentFunction<Void> {

        private final DataSet dataSet;
        private final LogitCalculator logitCalculator;
        private int nonCommuters;

        dominantCommuteModeChoice(DataSet dataSet, EnumMap<Mode, Map<String, Double>> coefficients) {
            super(MitoUtil.getRandomObject().nextLong());
            this.dataSet = dataSet;
            this.logitCalculator = new LogitCalculator(dataSet, coefficients);
        }

        @Override
        public Void call() {
            nonCommuters = 0;
            try {
                for (MitoPerson person : dataSet.getMobilePersons().values()) {
                    if (person.getTripsForPurpose(Purpose.HBW).size() + person.getTripsForPurpose(Purpose.HBE).size() == 0) {
                        nonCommuters++;
                    } else {
                        chooseDominantCommuteMode(person, logitCalculator.getProbabilities(person));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            logger.info(nonCommuters + " non-commuters skipped");
            return null;
        }

        private void chooseDominantCommuteMode(MitoPerson person, EnumMap<Mode, Double> probabilities) {
            if (probabilities == null) {
                nonCommuters++;
                return;
            }

            double sum = MitoUtil.getSum(probabilities.values());
            if (Math.abs(sum - 1) > 0.01) {
                logger.warn("Mode probabilities don't add to 1 for person " + person.getId());
            }
            if (sum > 0) {
                final Mode select = MitoUtil.select(probabilities, random);
                person.setDominantCommuteMode(select);
            } else {
                logger.error("Zero/negative probabilities for person " + person.getId());
                person.setDominantCommuteMode(null);
            }
        }
    }
}

