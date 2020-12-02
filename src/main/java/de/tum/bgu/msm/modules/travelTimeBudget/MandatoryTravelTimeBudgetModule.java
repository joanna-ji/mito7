package de.tum.bgu.msm.modules.travelTimeBudget;

import de.tum.bgu.msm.data.DataSet;
import de.tum.bgu.msm.data.Purpose;
import de.tum.bgu.msm.modules.Module;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Runs calculation of travel time budget for the Microsimulation Transport Orchestrator (MITO)
 *
 * @author Rolf Moeckel
 * Created on Apr 2, 2017 in Mannheim, Germany
 */
public class MandatoryTravelTimeBudgetModule extends Module {

    private static final Logger logger = Logger.getLogger(MandatoryTravelTimeBudgetModule.class);

    public MandatoryTravelTimeBudgetModule(DataSet dataSet) {
        super(dataSet);
    }

    @Override
    public void run() {
        calculateTravelTimeBudgets();
    }


    private void calculateTravelTimeBudgets() {
        logger.info("Started microscopic travel time budget calculation.");
        final ExecutorService service = Executors.newFixedThreadPool(Purpose.values().length);
        List<Future<?>> results = new ArrayList<>();
        results.add(service.submit(new MandatoryBudgetCalculator(dataSet.getHouseholds().values(), Purpose.HBW, dataSet.getTravelTimes(), dataSet.getPeakHour())));
        results.add(service.submit(new MandatoryBudgetCalculator(dataSet.getHouseholds().values(), Purpose.HBE, dataSet.getTravelTimes(), dataSet.getPeakHour())));
        service.shutdown();
        results.forEach(r -> {
            try {
                r.get();
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                service.shutdownNow();
            }
        });

        logger.info("  Finished mandatory microscopic travel time budget calculation.");

    }
}
