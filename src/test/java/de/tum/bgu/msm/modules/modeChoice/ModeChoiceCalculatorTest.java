package de.tum.bgu.msm.modules.modeChoice;

import de.tum.bgu.msm.Implementation;
import de.tum.bgu.msm.data.*;
import de.tum.bgu.msm.resources.Resources;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.InputStreamReader;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

public class ModeChoiceCalculatorTest {

    private ModeChoiceJSCalculator calculator;

    private final double[] reference = new double[]{0.35258741,0.21025060,0.07781168,0.01532404,0.00755559,0.01247863,0.00364841,0.35258208,0.01560092};

    @Before
    public void setup() {

        Resources.initializeResources("./testInput/test.properties", Implementation.MUNICH);

        Reader reader = new InputStreamReader(this.getClass().getResourceAsStream("ModeChoiceAV"));
        calculator = new ModeChoiceJSCalculator(reader);
    }

    @Test
    public void test() {
        MitoZone origin = new MitoZone(1, 100, null);
        origin.setDistanceToNearestRailStop(0.5f);
        //origin.setAreaTypeHBWModeChoice(AreaTypeForModeChoice.HBW_mediumSizedCity);
        MitoHousehold hh = new MitoHousehold(1, 20000, 1, null);
        MitoPerson pp = new MitoPerson(1, Occupation.STUDENT, 1, 20, Gender.FEMALE, true);
        hh.addPerson(pp);
        MitoTrip trip = new MitoTrip(1, Purpose.HBS);
        trip.setTripOrigin(origin);

        Map<String, Double> travelTimeByMode = new HashMap<>();
        travelTimeByMode.put("autoD", 15.);
        travelTimeByMode.put("autoP", 15.);
        travelTimeByMode.put("bus", 30.);
        travelTimeByMode.put("tramMetro", 25.);
        travelTimeByMode.put("train", 40.);
        //for(int i= 0; i< 1000000; i ++) {
            double[] result = calculator.calculateProbabilities(hh, pp, trip, travelTimeByMode, 5.,5.);
        //}
        for(int i = 0; i < result.length; i++) {
            Assert.assertEquals("Result " + i + " is totally wrong.",reference[i], result[i], 0.000001);
        }

    }

}