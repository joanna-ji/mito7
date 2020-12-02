package de.tum.bgu.msm.modules.travelTimeBudget;

import de.tum.bgu.msm.data.*;
import de.tum.bgu.msm.io.input.readers.CoefficientReader;
import de.tum.bgu.msm.resources.Resources;

import java.util.Map;

public class DiscretionaryBudgetCalculator implements Runnable {

    private final String purpose;
    private final DataSet dataSet;
    private final Map<String, Double> ttbCoefficients;

    public DiscretionaryBudgetCalculator(String purpose, DataSet dataSet) {
        this.purpose = purpose;
        this.dataSet = dataSet;
        this.ttbCoefficients = new CoefficientReader(dataSet, purpose,
                Resources.instance.getTravelTimeBudgetCoefficients()).readCoefficients();
    }

    @Override
    public void run() {
        for (MitoHousehold household : dataSet.getHouseholds().values()) {
            double householdBudget = 0.;
            for(MitoPerson person : household.getPersons().values()) {
                double personBudget = calculateBudget(person);
                person.setTravelTimeBudgetByPurpose(purpose, personBudget);
                householdBudget += personBudget;
            }
            household.setTravelTimeBudgetByPurpose(purpose, householdBudget);
        }
    }

    private double calculateBudget(MitoPerson pp) {
        if(pp.getTrips().size() == 0) {
            return  0.;
        }
        if(!purpose.equals("Total")) {
            if(pp.getTripsForPurpose(Purpose.valueOf(purpose)).size() == 0) {
                return 0.;
            }
        }
        double response = getResponse(pp, ttbCoefficients);
        double wbScale = ttbCoefficients.get("wbScale");

        int homeBasedFactor;
        if(purpose.equals("HBS") || purpose.equals("HBR") || purpose.equals("HBO")) {
            homeBasedFactor = 2;
        } else {
            homeBasedFactor = 1;
        }
        return Math.pow(-Math.log(0.5), wbScale) * Math.exp(response) * homeBasedFactor;
    }

    private double getResponse(MitoPerson pp, Map<String, Double> coefficients) {
        MitoHousehold hh = pp.getHousehold();
        double response = 0.;

        // Intercept
        response += coefficients.get("(Intercept)");

        // Household size
        int householdSize = hh.getHhSize();
        if (householdSize == 2) {
            response += coefficients.get("hh.size_2");
        }
        else if (householdSize == 3) {
            response += coefficients.get("hh.size_3");
        }
        else if (householdSize == 4) {
            response += coefficients.get("hh.size_4");
        }
        else if (householdSize >= 5) {;
            response += coefficients.get("hh.size_5");
        }

        // Number of children in household
        int householdChildren = DataSet.getChildrenForHousehold(hh);
        if(householdChildren == 1) {
            response += coefficients.get("hh.children_1");
        }
        else if (householdChildren == 2) {
            response += coefficients.get("hh.children_2");
        }
        else if (householdChildren >= 3) {
            response += coefficients.get("hh.children_3");
        }

        // Household in urban region
        if(!(hh.getHomeZone().getAreaTypeR().equals(AreaTypes.RType.RURAL))) {
            response += coefficients.get("hh.urban");
        }

        // Household autos
        int householdAutos = hh.getAutos();
        if(householdAutos == 1) {
            response += coefficients.get("hh.cars_1");
        }
        else if(householdAutos == 2) {
            response += coefficients.get("hh.cars_2");
        }
        else if(householdAutos >= 3) {
            response += coefficients.get("hh.cars_3");
        }

        // Autos per adult
        int householdAdults = householdSize - householdChildren;
        double autosPerAdult = Math.min((double) hh.getAutos() / (double) householdAdults , 1.0);
        response += autosPerAdult * coefficients.get("hh.autosPerAdult");

        // Age
        int age = pp.getAge();
        if (age <= 18) {
            response += coefficients.get("p.age_gr_1");
        }
        else if (age <= 29) {
            response += coefficients.get("p.age_gr_2");
        }
        else if (age <= 49) {
            response += 0.;
        }
        else if (age <= 59) {
            response += coefficients.get("p.age_gr_4");
        }
        else if (age <= 69) {
            response += coefficients.get("p.age_gr_5");
        }
        else {
            response += coefficients.get("p.age_gr_6");
        }

        // Female
        if (pp.getMitoGender().equals(MitoGender.FEMALE)) {
            response += coefficients.get("p.female");
        }

        // Has drivers Licence
        if (pp.hasDriversLicense()) {
            response += coefficients.get("p.driversLicense");
        }

        // Has bicycle
        if (pp.hasBicycle()) {
            response += coefficients.get("p.ownBicycle");
        }

        // Occupation Status
        if (pp.getMitoOccupationStatus().equals(MitoOccupationStatus.STUDENT)) {
            response += coefficients.get("p.occupationStatus_Student");
        } else if (pp.getMitoOccupationStatus().equals(MitoOccupationStatus.UNEMPLOYED)) {
            response += coefficients.get("p.occupationStatus_Unemployed");
        }

        // Number of trips
        int hbwTrips = pp.getTripsForPurpose(Purpose.HBW).size();
        int hbeTrips = pp.getTripsForPurpose(Purpose.HBE).size();

        if(purpose.equals("Total")) {
            int hbsTrips = pp.getTripsForPurpose(Purpose.HBS).size();
            int hbrTrips = pp.getTripsForPurpose(Purpose.HBR).size();
            int hboTrips = pp.getTripsForPurpose(Purpose.HBO).size();
            int rrtTrips = pp.getTripsForPurpose(Purpose.RRT).size();
            int nhbwTrips = pp.getTripsForPurpose(Purpose.NHBW).size();
            int nhboTrips = pp.getTripsForPurpose(Purpose.NHBO).size();

            int modelledTrips = 2 * (hbwTrips + hbeTrips + hbsTrips + hbrTrips + hboTrips) + rrtTrips + nhbwTrips + nhboTrips;
            response += Math.log(modelledTrips) * coefficients.get("log(p.trips)");
            response += hbwTrips * coefficients.get("p.trips_HBW");
            response += hbeTrips * coefficients.get("p.trips_HBE");
            response += hbsTrips * coefficients.get("p.trips_HBS");
            response += hbrTrips * coefficients.get("p.trips_HBR");
            response += hboTrips * coefficients.get("p.trips_HBO");
            response += rrtTrips * coefficients.get("p.trips_RRT");
            response += nhbwTrips * coefficients.get("p.trips_NHBW");
            response += nhboTrips * coefficients.get("p.trips_NHBO");
        } else {
            String coeffName = "log(p.trips_" + purpose + ")";
            int tripsOfThisPurpose = pp.getTripsForPurpose(Purpose.valueOf(purpose)).size();
            response += Math.log(tripsOfThisPurpose) * coefficients.get(coeffName);
        }

        // Work trips
        if (hbwTrips >= 1) {
            response += coefficients.get("p.isMobile_HBW");
        }

        // Education Trips
        if (hbeTrips >= 1) {
            response += coefficients.get("p.isMobile_HBE");
        }

        // Usual commute mode
        Mode dominantCommuteMode = pp.getDominantCommuteMode();
        if(dominantCommuteMode != null) {
            if (dominantCommuteMode.equals(Mode.autoDriver)) {
                response += coefficients.get("p.usualCommuteMode_carD");
            } else if (dominantCommuteMode.equals(Mode.autoPassenger)) {
                response += coefficients.get("p.usualCommuteMode_carP");
            } else if (dominantCommuteMode.equals(Mode.publicTransport)) {
                response += coefficients.get("p.usualCommuteMode_PT");
            } else if (dominantCommuteMode.equals(Mode.bicycle)) {
                response += coefficients.get("p.usualCommuteMode_cycle");
            } else if (dominantCommuteMode.equals(Mode.walk)) {
                response += coefficients.get("p.usualCommuteMode_walk");
            }
        }

        return response;
    }
}
