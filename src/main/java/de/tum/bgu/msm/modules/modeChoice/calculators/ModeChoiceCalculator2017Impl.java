package de.tum.bgu.msm.modules.modeChoice.calculators;

import de.tum.bgu.msm.data.*;
import de.tum.bgu.msm.data.travelTimes.TravelTimes;
import de.tum.bgu.msm.io.input.readers.ModeChoiceCoefficientReader;
import de.tum.bgu.msm.modules.modeChoice.ModeChoiceCalculator;
import de.tum.bgu.msm.resources.Resources;
import org.matsim.core.utils.collections.Tuple;

import java.util.*;

import static de.tum.bgu.msm.data.Mode.*;

public class ModeChoiceCalculator2017Impl extends ModeChoiceCalculator {

    private static final double SPEED_WALK_KMH = 4;
    private static final double SPEED_BICYCLE_KMH = 10;

    private final Map<Mode, Map<String, Double>> coef;

    public ModeChoiceCalculator2017Impl(Purpose purpose, DataSet dataSet) {
        super();
        coef = new ModeChoiceCoefficientReader(dataSet, purpose, Resources.instance.getModeChoiceCoefficients(purpose)).readCoefficientsForThisPurpose();
        setNests(coef);
    }

    private void setNests(Map<Mode, Map<String, Double>> coefficients) {
        List<Tuple<EnumSet<Mode>, Double>> nests = new ArrayList<>();
        nests.add(new Tuple<>(EnumSet.of(autoDriver,autoPassenger),coefficients.get(autoDriver).get("nestingCoefficient")));
        nests.add(new Tuple<>(EnumSet.of(train, tramOrMetro, bus, taxi), coefficients.get(train).get("nestingCoefficient")));
        nests.add(new Tuple<>(EnumSet.of(walk,bicycle), coefficients.get(walk).get("nestingCoefficient")));
        super.setNests(nests);
    }

    public EnumMap<Mode, Double> calculateUtilities(Purpose purpose, MitoHousehold household, MitoPerson person, MitoZone originZone, MitoZone destinationZone, TravelTimes travelTimes, double travelDistanceAuto, double travelDistanceNMT, double peakHour_s) {
        int age = person.getAge();
        int isMale = person.getMitoGender() == MitoGender.MALE ? 1 : 0;
        int hhSize = household.getHhSize();
        int hhAutos = household.getAutos();
        MitoOccupationStatus occupationStatus = person.getMitoOccupationStatus();

        boolean hhHasBicycles = false;
        for (MitoPerson p : household.getPersons().values()) {
            if (p.hasBicycle()) {
                hhHasBicycles = true;
            }
        }
        int economicStatus = household.getEconomicStatus();

        EnumMap<Mode, Double> generalizedCosts = calculateGeneralizedCosts(purpose, household, person,
                originZone, destinationZone, travelTimes, travelDistanceAuto, travelDistanceNMT, peakHour_s);


        EnumMap<Mode, Double> utilities = new EnumMap<>(Mode.class);

        for (Mode mode : person.getModeRestriction().getRestrictedModeSet()){
            final Map<String, Double> modeCoef = coef.get(mode);
            double utility = modeCoef.get("intercept");
            if (isMale ==  1){
                utility += modeCoef.get("gender_male");
            } else {
                utility += modeCoef.get("gender_female");
            }
            switch (occupationStatus){
                case WORKER:
                    utility += modeCoef.get("is_employed");
                    break;
                case UNEMPLOYED:
                    utility += modeCoef.get("is_homemaker_or_other");
                    break;
                case STUDENT:
                    utility += modeCoef.get("is_student");
                    break;
                case RETIRED:
                    utility += modeCoef.get("is_retired_or_pensioner");
                    break;
            }
            if (age < 18){
                utility += modeCoef.get("age_0_to_17");
            } else if (age < 30){
                utility += modeCoef.get("age_18_to_29");
            } else if (age < 40){
                utility += modeCoef.get("age_30_to_39");
            } else if (age < 50){
                utility += modeCoef.get("age_40_to_49");
            } else if (age < 60) {
                utility += modeCoef.get("age_50_to_59");
            } else {
                utility += modeCoef.get("age_above_60");
            }
            switch(economicStatus){
                case 0:
                    utility += modeCoef.get("is_economic_status_very_low");
                    break;
                case 1:
                    utility += modeCoef.get("is_economic_status_low");
                    break;
                case 2:
                    utility += modeCoef.get("is_economic_status_medium");
                    break;
                case 3:
                    utility += modeCoef.get("is_economic_status_high");
                    break;
                case 4:
                    utility += modeCoef.get("is_economic_status_very_high");
                    break;
            }
            switch (hhSize){
                case 1:
                    utility += modeCoef.get("is_hh_one_person");
                    break;
                case 2:
                    utility += modeCoef.get("is_hh_two_persons");
                    break;
                case 3:
                    utility += modeCoef.get("is_hh_three_persons");
                    break;
                default:
                    utility += modeCoef.get("is_hh_four_or_more_persons");
                    break;
            }
            switch (hhAutos){
                case 0:
                    utility += modeCoef.get("hh_no_car");
                    break;
                case 1:
                    utility += modeCoef.get("hh_one_car");
                    break;
                default:
                    utility += modeCoef.get("hh_two_or_more_cars");
                    break;
            }
            if (hhHasBicycles){
                utility += modeCoef.get("hh_has_bike");
            } else {
                utility += modeCoef.get("hh_no_bike");
            }
            double gc = generalizedCosts.get(mode);
            utility += modeCoef.get("exp_generalized_time_min") * Math.exp(gc * modeCoef.get("alpha"));

            utilities.put(mode, utility);
        }

        return utilities;
    }

    public EnumMap<Mode, Double> calculateGeneralizedCosts(Purpose purpose, MitoHousehold household, MitoPerson person, MitoZone originZone,
                                                           MitoZone destinationZone, TravelTimes travelTimes,
                                                           double travelDistanceAuto, double travelDistanceNMT, double peakHour_s) {

        double timeAutoD = travelTimes.getTravelTime(originZone, destinationZone, peakHour_s, "car");
        double timeAutoP = timeAutoD;
        double timeBus = travelTimes.getTravelTime(originZone, destinationZone, peakHour_s, "bus");
        double timeTrain = travelTimes.getTravelTime(originZone, destinationZone, peakHour_s, "train");
        double timeTramMetro = travelTimes.getTravelTime(originZone, destinationZone, peakHour_s, "tramMetro");
        double timeTaxi = timeAutoD;

        int monthlyIncome_EUR = household.getMonthlyIncome_EUR();

        double gcAutoD;
        double gcAutoP;
        double gcBus;
        double gcTrain;
        double gcTramMetro;
        double gcTaxi;
        double gcWalk = travelDistanceNMT / SPEED_WALK_KMH * 60;
        double gcBicycle = travelDistanceNMT / SPEED_BICYCLE_KMH * 60;

        if (monthlyIncome_EUR <= 1500) {
            gcAutoD = timeAutoD + (travelDistanceAuto * coef.get(Mode.autoDriver).get("costPerKm")) / coef.get(Mode.autoDriver).get("vot_under_1500_eur_min");
            gcAutoP = timeAutoP + (travelDistanceAuto * coef.get(Mode.autoPassenger).get("costPerKm")) / coef.get(Mode.autoPassenger).get("vot_under_1500_eur_min");
            gcBus = timeBus + (travelDistanceAuto * coef.get(Mode.bus).get("costPerKm")) / coef.get(Mode.bus).get("vot_under_1500_eur_min");
            gcTrain = timeTrain + (travelDistanceAuto * coef.get(Mode.train).get("costPerKm")) / coef.get(Mode.train).get("vot_under_1500_eur_min");
            gcTramMetro = timeTramMetro + (travelDistanceAuto * coef.get(Mode.tramOrMetro).get("costPerKm")) / coef.get(Mode.tramOrMetro).get("vot_under_1500_eur_min");
            gcTaxi = timeTaxi + (travelDistanceAuto * coef.get(Mode.taxi).get("costPerKm")) / coef.get(Mode.taxi).get("vot_under_1500_eur_min");
        } else if (monthlyIncome_EUR <= 5600) {
            gcAutoD = timeAutoD + (travelDistanceAuto * coef.get(Mode.autoDriver).get("costPerKm")) / coef.get(Mode.autoDriver).get("vot_1500_to_5600_eur_min");
            gcAutoP = timeAutoP + (travelDistanceAuto * coef.get(Mode.autoPassenger).get("costPerKm")) / coef.get(Mode.autoPassenger).get("vot_1500_to_5600_eur_min");
            gcBus = timeBus + (travelDistanceAuto * coef.get(Mode.bus).get("costPerKm")) / coef.get(Mode.bus).get("vot_1500_to_5600_eur_min");
            gcTrain = timeTrain + (travelDistanceAuto * coef.get(Mode.train).get("costPerKm")) / coef.get(Mode.train).get("vot_1500_to_5600_eur_min");
            gcTramMetro = timeTramMetro + (travelDistanceAuto * coef.get(Mode.tramOrMetro).get("costPerKm")) / coef.get(Mode.tramOrMetro).get("vot_1500_to_5600_eur_min");
            gcTaxi = timeTaxi + (travelDistanceAuto * coef.get(Mode.taxi).get("costPerKm")) / coef.get(Mode.taxi).get("vot_1500_to_5600_eur_min");
        } else {
            gcAutoD = timeAutoD + (travelDistanceAuto * coef.get(Mode.autoDriver).get("costPerKm")) / coef.get(Mode.autoDriver).get("vot_above_5600_eur_min");
            gcAutoP = timeAutoP + (travelDistanceAuto * coef.get(Mode.autoPassenger).get("costPerKm")) / coef.get(Mode.autoPassenger).get("vot_above_5600_eur_min");
            gcBus = timeBus + (travelDistanceAuto * coef.get(Mode.bus).get("costPerKm")) / coef.get(Mode.bus).get("vot_above_5600_eur_min");
            gcTrain = timeTrain + (travelDistanceAuto * coef.get(Mode.train).get("costPerKm")) / coef.get(Mode.train).get("vot_above_5600_eur_min");
            gcTramMetro = timeTramMetro + (travelDistanceAuto * coef.get(Mode.tramOrMetro).get("costPerKm")) / coef.get(Mode.tramOrMetro).get("vot_above_5600_eur_min");
            gcTaxi = timeTaxi + (travelDistanceAuto * coef.get(Mode.taxi).get("costPerKm")) / coef.get(Mode.taxi).get("vot_above_5600_eur_min");
        }

        EnumMap<Mode, Double> generalizedCosts = new EnumMap<>(Mode.class);
        generalizedCosts.put(Mode.autoDriver, gcAutoD);
        generalizedCosts.put(Mode.autoPassenger, gcAutoP);
        generalizedCosts.put(Mode.bicycle, gcBicycle);
        generalizedCosts.put(Mode.bus, gcBus);
        generalizedCosts.put(Mode.train, gcTrain);
        generalizedCosts.put(Mode.tramOrMetro, gcTramMetro);
        generalizedCosts.put(Mode.taxi, gcTaxi);
        generalizedCosts.put(Mode.walk, gcWalk);
        return generalizedCosts;

    }
}
