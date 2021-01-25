package de.tum.bgu.msm.modules.travelTimeBudget;

import de.tum.bgu.msm.data.DataSet;
import de.tum.bgu.msm.data.MitoHousehold;
import de.tum.bgu.msm.data.MitoPerson;
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

        for (MitoPerson person : dataSet.getPersons().values()) {
            try {
                double totalTravelTimeBudget = person.getTotalTravelTimeBudget();
                double discretionaryTTB = totalTravelTimeBudget - person.getTravelTimeBudgetForPurpose(Purpose.HBW) -
                        person.getTravelTimeBudgetForPurpose(Purpose.HBE);
                discretionaryTTB = Math.max(discretionaryTTB, 0);

                double calcDiscretionaryTTB = 0;
                for (Purpose purpose : discretionaryPurposes) {
                    calcDiscretionaryTTB += person.getTravelTimeBudgetForPurpose(purpose);
                }
                if(calcDiscretionaryTTB > 0) {
                    for (Purpose purpose : discretionaryPurposes) {
                        double budget = person.getTravelTimeBudgetForPurpose(purpose);
                        if (budget != 0) {
                            budget *= (1 + discretionaryTTB / calcDiscretionaryTTB) / 2 ;
                            person.setTravelTimeBudgetByPurpose(purpose, budget);
                        }
                    }
                }
            } catch (NullPointerException e) {
                System.out.println("oops");
            }
        }
    }

    private void checkHouseholdTTB() {
        logger.info("Checking person budgets add to household budgets");
        for (Purpose purpose : discretionaryPurposes) {
            for (MitoHousehold household : dataSet.getHouseholds().values()) {
                double householdBudget = 0.;
                for(MitoPerson person : household.getPersons().values()) {
                    double personBudget = person.getTravelTimeBudgetForPurpose(purpose);
                    householdBudget += personBudget;
                }
                if(householdBudget != household.getTravelTimeBudgetForPurpose(purpose)) {
                    logger.error(purpose + " person budgets don't add to household budget for household " + household.getId());
                }
            }
        }
    }
}