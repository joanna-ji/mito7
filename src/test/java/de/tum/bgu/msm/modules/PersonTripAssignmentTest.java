package de.tum.bgu.msm.modules;

import de.tum.bgu.msm.MitoUtil;
import de.tum.bgu.msm.data.DataSet;
import de.tum.bgu.msm.data.MitoHousehold;
import de.tum.bgu.msm.data.MitoPerson;
import de.tum.bgu.msm.data.MitoTrip;
import de.tum.bgu.msm.resources.Purpose;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class PersonTripAssignmentTest {

    private DataSet dataSet;

    @Before
    public void setupAndRun() {
        MitoUtil.initializeRandomNumber(new Random(42));
        dataSet = new DataSet();

        MitoHousehold household = new MitoHousehold(1, 5, 3, 2, 0, 1, 2, 2, 2, 1, 1, 1);
        household.getPersons().add(new MitoPerson(1, 1, 1, 1, 35,1, true));
        household.getPersons().add(new MitoPerson(2, 1, 1, 1,30,2, true));
        household.getPersons().add(new MitoPerson(3, 1, 3, 1, 10,2, false));
        household.getPersons().add(new MitoPerson(4, 1, 3, 1, 15,1, false));
        household.getPersons().add(new MitoPerson(5, 1, -1, 1, 70,2, false));
        dataSet.getHouseholds().put(household.getHhId(), household);

        MitoTrip tripHBW = new MitoTrip(1, 1, Purpose.HBW, 1);
        household.getTripsByPurpose().put(Purpose.HBW, Collections.singletonList(tripHBW));
        MitoTrip tripHBE = new MitoTrip(2, 1, Purpose.HBE, 1);
        household.getTripsByPurpose().put(Purpose.HBE, Collections.singletonList(tripHBE));
        MitoTrip tripHBS = new MitoTrip(3, 1, Purpose.HBS, 1);
        household.getTripsByPurpose().put(Purpose.HBS, Collections.singletonList(tripHBS));
        MitoTrip tripHBO = new MitoTrip(4, 1, Purpose.HBO, 1);
        household.getTripsByPurpose().put(Purpose.HBO, Collections.singletonList(tripHBO));
        MitoTrip tripNHBW = new MitoTrip(5, 1, Purpose.NHBW, 1);
        household.getTripsByPurpose().put(Purpose.NHBW, Collections.singletonList(tripNHBW));
        MitoTrip tripNHBO = new MitoTrip(6, 1, Purpose.NHBO, 1);
        household.getTripsByPurpose().put(Purpose.NHBO, Collections.singletonList(tripNHBO));

        dataSet.getTrips().put(1, tripHBW);
        dataSet.getTrips().put(2, tripHBE);
        dataSet.getTrips().put(3, tripHBS);
        dataSet.getTrips().put(4, tripHBO);
        dataSet.getTrips().put(5, tripNHBW);
        dataSet.getTrips().put(6, tripNHBO);

        PersonTripAssignment assignment = new PersonTripAssignment(dataSet);
        assignment.run();
    }

    @Test
    public void testAssignment() {
        for(MitoTrip trip: dataSet.getTrips().values()) {
            assertNotNull("No Person set for trip " + trip, trip.getPerson());
//            if(dataSet.getPurposes()[trip.getTripPurpose()].equals("HBW")) {
//                assertEquals(1, trip.getPerson().getOccupation());
//            } else if( dataSet.getPurposes()[trip.getTripPurpose()].equals("HBE")) {
//                assertEquals(3, trip.getPerson().getOccupation());
//            }
        }
    }
}
