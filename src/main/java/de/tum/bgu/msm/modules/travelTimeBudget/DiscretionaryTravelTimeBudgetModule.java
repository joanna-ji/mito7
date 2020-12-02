package de.tum.bgu.msm.modules.travelTimeBudget;

import de.tum.bgu.msm.data.DataSet;
import de.tum.bgu.msm.data.MitoHousehold;
import de.tum.bgu.msm.data.Purpose;
import de.tum.bgu.msm.modules.Module;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static de.tum.bgu.msm.data.Purpose.*;

public class DiscretionaryTravelTimeBudgetModule extends Module {

    private static final Logger logger = Logger.getLogger(DiscretionaryTravelTimeBudgetModule.class);

    private EnumSet<Purpose> discretionaryPurposes = EnumSet.of(HBS, HBR, HBO, RRT, NHBW, NHBO);

    public DiscretionaryTravelTimeBudgetModule(DataSet dataSet) {
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
        results.add(service.submit(new DiscretionaryBudgetCalculator("Total", dataSet)));
        for (Purpose purpose : discretionaryPurposes) {
            results.add(service.submit(new DiscretionaryBudgetCalculator(purpose.toString(), dataSet)));
        }
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

        logger.info("  Adjusting travel time budgets.");
        adjustDiscretionaryPurposeBudgets();
        logger.info("  Finished microscopic travel time budget calculation.");

    }

    private void adjustDiscretionaryPurposeBudgets() {

        for (MitoHousehold household : dataSet.getHouseholds().values()) {
            try {
                double totalTravelTimeBudget = household.getTotalTravelTimeBudget();
                double discretionaryTTB = totalTravelTimeBudget - household.getTravelTimeBudgetForPurpose(Purpose.HBW) -
                        household.getTravelTimeBudgetForPurpose(Purpose.HBE);
                discretionaryTTB = Math.max(discretionaryTTB, 0);

                double calcDiscretionaryTTB = 0;
                for (Purpose purpose : discretionaryPurposes) {
                    calcDiscretionaryTTB += household.getTravelTimeBudgetForPurpose(purpose);
                }
                for (Purpose purpose : discretionaryPurposes) {
                    double budget = household.getTravelTimeBudgetForPurpose(purpose);
                    if (budget != 0) {
                        budget = budget * discretionaryTTB / calcDiscretionaryTTB;
                        household.setTravelTimeBudgetByPurpose(purpose, budget);
                    }
                }
            } catch (NullPointerException e) {
                System.out.println("oops");
            }
        }
    }
}
