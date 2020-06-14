package de.tum.bgu.msm.modules.travelTimeBudget;

import de.tum.bgu.msm.data.DataSet;
import de.tum.bgu.msm.data.MitoHousehold;
import de.tum.bgu.msm.data.Purpose;
import de.tum.bgu.msm.modules.Module;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Runs calculation of travel time budget for the Microsimulation Transport Orchestrator (MITO)
 *
 * @author Rolf Moeckel
 * Created on Apr 2, 2017 in Mannheim, Germany
 */
public class TravelTimeBudgetModule extends Module {

    private static final Logger logger = Logger.getLogger(TravelTimeBudgetModule.class);

    private EnumSet<Purpose> discretionaryPurposes = EnumSet.of(Purpose.HBS, Purpose.HBO, Purpose.NHBW, Purpose.NHBO);
    private final TravelTimeBudgetCalculatorImpl travelTimeCalc;

    public TravelTimeBudgetModule(DataSet dataSet) {
        super(dataSet);
        travelTimeCalc = new TravelTimeBudgetCalculatorImpl();
    }

    @Override
    public void run() {
        calculateTravelTimeBudgets();
    }


    private void calculateTravelTimeBudgets() {
        logger.info("  Started microscopic travel time budget calculation.");
        final ExecutorService service = Executors.newFixedThreadPool(Purpose.values().length);
        List<Callable<Void>> tasks = new ArrayList();
        for(Purpose purpose: discretionaryPurposes) {
            tasks.add(new DiscretionaryBudgetCalculator(purpose, dataSet.getHouseholds().values()));
        }
        tasks.add(new MandatoryBudgetCalculator(dataSet.getHouseholds().values(), Purpose.HBW, dataSet.getTravelTimes(), dataSet.getPeakHour()));
        tasks.add(new MandatoryBudgetCalculator(dataSet.getHouseholds().values(), Purpose.HBE, dataSet.getTravelTimes(), dataSet.getPeakHour()));
        try {
            service.invokeAll(tasks);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
        	service.shutdownNow();
        }

        logger.info("  Adjusting travel time budgets.");
        adjustDiscretionaryPurposeBudgets();
        logger.info("  Finished microscopic travel time budget calculation.");

    }

    private void adjustDiscretionaryPurposeBudgets() {
        for (MitoHousehold household : dataSet.getHouseholds().values()) {
            double totalTravelTimeBudget = travelTimeCalc.calculateBudget(household, "Total");
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
            }
        }
    }
}
