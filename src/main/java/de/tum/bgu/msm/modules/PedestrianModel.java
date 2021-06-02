package de.tum.bgu.msm.modules;

import cern.colt.matrix.tfloat.impl.SparseFloatMatrix2D;
import com.google.common.collect.Iterables;
import com.google.common.math.LongMath;
import de.tum.bgu.msm.data.DataSet;
import de.tum.bgu.msm.data.*;
import de.tum.bgu.msm.moped.MoPeDModel;
import de.tum.bgu.msm.moped.data.Purpose;
import de.tum.bgu.msm.moped.data.*;
import de.tum.bgu.msm.moped.io.input.InputManager;
import de.tum.bgu.msm.moped.util.concurrent.ConcurrentExecutor;
import de.tum.bgu.msm.resources.Properties;
import de.tum.bgu.msm.resources.Resources;
import de.tum.bgu.msm.util.MitoUtil;
import org.apache.log4j.Logger;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.index.quadtree.Quadtree;
import org.matsim.api.core.v01.Coord;
import org.matsim.core.utils.geometry.CoordUtils;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;


public class PedestrianModel {

    private static final Logger logger = Logger.getLogger( PedestrianModel.class );
    private static AtomicInteger failedMatchingMopedZoneCounter = new AtomicInteger(0);
    private MoPeDModel mopedModel;
    private final DataSet dataSet;
    private final String propertiesPath;
    private Map<Integer, MopedHousehold>  households = new HashMap<>();
    private Quadtree mopedZoneQuadTree = new Quadtree();
    private SparseFloatMatrix2D mopedTravelDistance;
    private int counter = 0;

    public PedestrianModel(DataSet dataSet) {
        this.dataSet = dataSet;
        this.propertiesPath = Resources.instance.getString(Properties.MOPED_PROPERTIES); //TODO: propoertiesPath MITO?
    }

    public void initializeMoped(){
        this.mopedModel = MoPeDModel.initializeModelFromMito(propertiesPath);
        mopedModel.getManager().readZoneData();
        updateData(dataSet.getYear());
    }

    private void updateData(int year) {

        prepareMopedZoneSearchTree();
        logger.info("  Converting mito household to moped");
        convertHhs();
        logger.info(counter + " trips has been converted to moped");
        logger.info("  MITO data being sent to MoPeD");
        InputManager.InputFeed feed = new InputManager.InputFeed(households, year);
        mopedModel.feedDataFromMITO(feed);
    }

    /*public void runMopedMandatory() {
        logger.info("  Running Moped Walk Mode Choice for Mandatory trips for the year: " + dataSet.getYear());
        mopedModel.runAgentBasedModelForMandatoryTrips();
        feedDataBackToMito(Purpose.getMandatoryPurposes());
        writeOutMoPeDTrips(dataSet, Resources.instance.getString(Properties.SCENARIO_NAME),"mandatory");
    }*/

    public void runMopedHomeBasedDiscretionary() {
        logger.info("  Running pedestrian model MITO for the year: " + dataSet.getYear());
        mopedModel.runAgentBasedModelForHomeBasedDiscretionaryTrips();
        feedDataBackToMito(Purpose.getHomeBasedDiscretionaryPurposes());
        writeOutMoPeDTrips(dataSet, Resources.instance.getString(Properties.SCENARIO_NAME),"hbdiscretionary");
    }

    public void runMopedNonHomeBased() {
        updateNonHomeBasedTripList();
        mopedModel.runAgentBasedModelForNonHomeBasedTrips();
        feedDataBackToMito(Purpose.getNonHomeBasedPurposes());
        writeOutMoPeDTrips(dataSet, Resources.instance.getString(Properties.SCENARIO_NAME),"nhb");
    }

    private void updateDiscretionaryTripList() {
        int counter = 0;
        for (MitoHousehold hh : dataSet.getHouseholds().values()) {
            //TODO:need to decide how to narrow down the application area, now only run for munich city area
            if(!hh.getHomeZone().isMunichZone()){
                continue;
            }


            if (hasDiscretionaryTrip(hh)) {
                if (LongMath.isPowerOfTwo(counter)) {
                    logger.info(counter + " households done ");
                }
                for (MitoPerson pp : hh.getPersons().values()) {
                    for (MitoTrip tt : pp.getTrips()) {
                        if (de.tum.bgu.msm.data.Purpose.getDiscretionaryPurposes().contains(tt.getTripPurpose())) {
                            MopedHousehold mopedHousehold = mopedModel.getDataSet().getHouseholds().get(hh.getId());
                            if(mopedHousehold==null){
                                throw new RuntimeException("Household:" + hh.getId() + " does not exist in moped household list!");
                            }
                            MopedPerson mopedPerson = mopedModel.getDataSet().getPersons().get(pp.getId());
                            if(mopedPerson==null){
                                throw new RuntimeException("Person:" + pp.getId() + " does not exist in moped person list!");
                            }
                            MopedTrip mopedTrip = convertToMopedTt(tt);

                            if (Purpose.getHomeBasedDiscretionaryPurposes().contains(mopedTrip.getTripPurpose())) {
                                mopedTrip.setTripOrigin(mopedHousehold.getHomeZone());
                            }

                            mopedPerson.addTrip(mopedTrip);
                            if (mopedHousehold.getTripsForPurpose(mopedTrip.getTripPurpose()).isEmpty()) {
                                mopedHousehold.setTripsByPurpose(new ArrayList<>(), mopedTrip.getTripPurpose());
                            }
                            mopedHousehold.getTripsForPurpose(mopedTrip.getTripPurpose()).add(mopedTrip);
                            mopedModel.getDataSet().addTrip(mopedTrip);
                        }
                    }
                }
                counter++;
            }
        }
    }

    private void updateNonHomeBasedTripList() {

        List<MitoTrip> nonHomeBasedMitoTrips = dataSet.getTrips().values().stream().filter(tt -> tt.getTripPurpose().equals(de.tum.bgu.msm.data.Purpose.NHBW)||tt.getTripPurpose().equals(de.tum.bgu.msm.data.Purpose.NHBO)).collect(Collectors.toList());

        final int partitionSize = (int) ((double) nonHomeBasedMitoTrips.size() / Runtime.getRuntime().availableProcessors()) + 1;
        Iterable<List<MitoTrip>> partitions = Iterables.partition(nonHomeBasedMitoTrips, partitionSize);
        logger.info(nonHomeBasedMitoTrips.size() + " non home based trips are going to be processed...");
        logger.info("partition size: " + partitionSize);

        ConcurrentExecutor<Void> executor = ConcurrentExecutor.fixedPoolService(Runtime.getRuntime().availableProcessors());

        AtomicInteger partitionCounter = new AtomicInteger(0);
        AtomicInteger missingTrips = new AtomicInteger(0);

        for (final List<MitoTrip> partition : partitions) {
            executor.addTaskToQueue(() -> {
                try {
                    partitionCounter.incrementAndGet();
                    int ttCounter = 0;
                    for (MitoTrip mitoTrip :partition){
                        if (LongMath.isPowerOfTwo(ttCounter)) {
                            logger.info(ttCounter + " trips done in " + partitionCounter.get());
                        }
                        MopedTrip mopedTrip = mopedModel.getDataSet().getTrips().get(mitoTrip.getId());
                        if(mopedTrip==null){
                            if(ModeRestriction.getWalkModeRestriction().contains(mitoTrip.getPerson().getModeRestriction())){
                                missingTrips.incrementAndGet();
                            }
                            continue;
                        }
                        if(mopedTrip.getTripOrigin()==null&&mitoTrip.getTripOrigin()!=null){
                            if(mitoTrip.getTripOriginMopedZoneId()!=0){
                                mopedTrip.setTripOrigin(mopedModel.getDataSet().getZone(mitoTrip.getTripOriginMopedZoneId()));
                            }else{
                                Coord originCoord;
                                if(mitoTrip.getTripOrigin() instanceof MicroLocation) {
                                    originCoord = CoordUtils.createCoord(((MicroLocation) mitoTrip.getTripOrigin()).getCoordinate());
                                } else {
                                    //TODO: random point? moped destination choice will also be random. Centriod of zone?
                                    originCoord = CoordUtils.createCoord(dataSet.getZones().get(mitoTrip.getTripOrigin().getZoneId()).getRandomCoord(MitoUtil.getRandomObject()));
                                }
                                MopedZone origin = locateMicrolationToMopedZone(new Coordinate(originCoord.getX(),originCoord.getY()));
                                mopedTrip.setTripOrigin(origin);
                            }
                        }
                        ttCounter++;
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                    logger.warn(e.getLocalizedMessage());
                    throw new RuntimeException(e);
                }
                return null;
            });
        }
        executor.execute();


        logger.warn(missingTrips.get() + "non home based mito trips cannot be found in moped trip list.");

    }

    private void feedDataBackToMito(List<Purpose> purposes) {

        final int partitionSize = (int) ((double) mopedModel.getDataSet().getTrips().values().size() / Runtime.getRuntime().availableProcessors()) + 1;
        Iterable<List<MopedTrip>> partitions = Iterables.partition(mopedModel.getDataSet().getTrips().values(), partitionSize);
        logger.info(mopedModel.getDataSet().getTrips().values().size() + " moped trips are going to be processed...");
        logger.info("partition size: " + partitionSize);

        ConcurrentExecutor<Void> executor = ConcurrentExecutor.fixedPoolService(Runtime.getRuntime().availableProcessors());

        AtomicInteger partitionCounter = new AtomicInteger(0);
        AtomicInteger countMopedWalkTrips = new AtomicInteger(0);

        for (final List<MopedTrip> partition : partitions) {
            executor.addTaskToQueue(() -> {
                try {
                    partitionCounter.incrementAndGet();
                    int ttCounter = 0;
                    for(MopedTrip mopedTrip : partition){
                        if (LongMath.isPowerOfTwo(ttCounter)) {
                            logger.info(ttCounter + " trips done in " + partitionCounter.get());
                        }

                        if(!purposes.contains(mopedTrip.getTripPurpose())){
                            continue;
                        }

                        MitoTrip mitoTrip = dataSet.getTrips().get(mopedTrip.getId());

                        if(mopedTrip.isWalkMode()){
                            if(mopedTrip.getTripOrigin()!=null){
                                mitoTrip.setTripOriginMopedZoneId(mopedTrip.getTripOrigin().getZoneId());
                            }else{
                                logger.warn("trip id: " + mopedTrip.getTripId()+ " purpose: " + mopedTrip.getTripPurpose() + " has no origin, but is walk mode.");
                                continue;
                            }

                            if(mopedTrip.getTripDestination()!=null) {
                                mitoTrip.setTripDestination(dataSet.getZones().get(mopedTrip.getTripDestination().getMitoZoneId()));
                                mitoTrip.setTripDestinationMopedZoneId(mopedTrip.getTripDestination().getZoneId());
                                mitoTrip.setMopedTripDistance(mopedTrip.getTripDistance());
                                //TODO: travel time budget?
                                //double newTravelBudget = dataSet.getHouseholds().get(mopedTrip.getPerson().getMopedHousehold().getId()).getTravelTimeBudgetForPurpose(mitoTrip.getTripPurpose()) - mopedTrip.getTripDistance()/83.3;//average walk speed 5km/hr
                                //dataSet.getHouseholds().get(mopedTrip.getPerson().getMopedHousehold().getId()).setTravelTimeBudgetByPurpose(mitoTrip.getTripPurpose(),newTravelBudget);
                                mitoTrip.setTripMode(Mode.walk);
                                countMopedWalkTrips.incrementAndGet();

                            }else{
                                logger.warn("trip id: " + mopedTrip.getTripId()+ " purpose: " + mopedTrip.getTripPurpose() + " has no destination, but is walk mode. Origin zone: " + mopedTrip.getTripOrigin().getZoneId());
                                continue;
                            }
                        }
                        ttCounter++;
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                    logger.warn(e.getLocalizedMessage());
                    throw new RuntimeException(e);
                }
                return null;
            });
        }
        executor.execute();

        logger.info(countMopedWalkTrips.get() + " moped walk trips have been fed back to MITO");
    }

    private void prepareMopedZoneSearchTree() {
        for(MopedZone mopedZone: mopedModel.getDataSet().getZones().values()){
            this.mopedZoneQuadTree.insert(((Geometry)(mopedZone.getShapeFeature().getDefaultGeometry())).getEnvelopeInternal(),mopedZone);
        }
    }

    private void convertHhs() {

        final int partitionSize = (int) ((double) dataSet.getHouseholds().values().size() / Runtime.getRuntime().availableProcessors()) + 1;
        Iterable<List<MitoHousehold>> partitions = Iterables.partition(dataSet.getHouseholds().values(), partitionSize);
        logger.info(dataSet.getHouseholds().values().size() + " households are going to be converted...");
        logger.info("partition size: " + partitionSize);

        ConcurrentExecutor<Void> executor = ConcurrentExecutor.fixedPoolService(Runtime.getRuntime().availableProcessors());

        AtomicInteger partitionCounter = new AtomicInteger(0);
        AtomicInteger ppCounter = new AtomicInteger(0);
        AtomicInteger ttCounter = new AtomicInteger(0);

        for (final List<MitoHousehold> partition : partitions) {
            executor.addTaskToQueue(() -> {
                try {
                    partitionCounter.incrementAndGet();
                    int hhCounter = 0;
                    for (MitoHousehold hh : partition) {
                        //TODO:need to decide how to narrow down the application area, now only run for munich city area
//                        if(!hh.getHomeZone().isMunichZone()){
//                            continue;
//                        }
                        if (LongMath.isPowerOfTwo(hhCounter)) {
                            logger.info(hhCounter + " households done in " + partitionCounter.get());
                        }

                        if (hasTrip(hh)) {

                            MopedHousehold mopedHousehold = convertToMopedHh(hh);
                            for (MitoPerson pp : hh.getPersons().values()) {
                                if(!ModeRestriction.getWalkModeRestriction().contains(pp.getModeRestriction())){
                                    continue;
                                }
                                MopedPerson mopedPerson = convertToMopedPp(pp);
                                mopedPerson.setMopedHousehold(mopedHousehold);
                                for (MitoTrip tt : pp.getTrips()) {
                                    if(tt.getTripPurpose().equals(de.tum.bgu.msm.data.Purpose.RRT)){
                                        continue;
                                        //TODO:no RRT trip defined in Moped model
                                    }
                                    MopedTrip mopedTrip = convertToMopedTt(tt);

                                    if(Purpose.getHomeBasedDiscretionaryPurposes().contains(mopedTrip.getTripPurpose())){
                                        mopedTrip.setTripOrigin(mopedHousehold.getHomeZone());
                                        tt.setTripOriginMopedZoneId(mopedHousehold.getHomeZone().getZoneId());
                                        tt.setTripOrigin(hh);
                                    }

                                    mopedPerson.addTrip(mopedTrip);
                                    if (mopedHousehold.getTripsForPurpose(mopedTrip.getTripPurpose()).isEmpty()) {
                                        mopedHousehold.setTripsByPurpose(new ArrayList<>(), mopedTrip.getTripPurpose());
                                    }
                                    mopedHousehold.getTripsForPurpose(mopedTrip.getTripPurpose()).add(mopedTrip);
                                    ttCounter.incrementAndGet();
                                }
                                mopedHousehold.addPerson(mopedPerson);
                                ppCounter.incrementAndGet();
                            }
                            households.put(mopedHousehold.getId(), mopedHousehold);
                            hhCounter++;
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    logger.warn(e.getLocalizedMessage());
                    throw new RuntimeException(e);
                }
                return null;
            });
        }
        executor.execute();

        logger.info(ppCounter.get() + " persons have been transfered to Moped...");
        logger.info(ttCounter.get() + " trips have been transfered to Moped...");
        logger.warn(failedMatchingMopedZoneCounter.get() + " home/job locations failed to be located to a moped zone!");
    }

    private boolean hasTrip(MitoHousehold hh) {
        for(de.tum.bgu.msm.data.Purpose purpose : de.tum.bgu.msm.data.Purpose.values()){
            if(!hh.getTripsForPurpose(purpose).isEmpty()){
                return true;
            }
        }

        return false;
    }

    private boolean hasDiscretionaryTrip(MitoHousehold hh) {
        for(de.tum.bgu.msm.data.Purpose purpose : de.tum.bgu.msm.data.Purpose.getDiscretionaryPurposes()){
            if(!hh.getTripsForPurpose(purpose).isEmpty()){
                return true;
            }
        }

        return false;
    }

    private MopedTrip convertToMopedTt(MitoTrip tt) {
        Purpose mopedPurpose = Purpose.valueOf(tt.getTripPurpose().name());
        counter++;
        return new MopedTrip(tt.getTripId(),mopedPurpose);
    }

    private MopedPerson convertToMopedPp(MitoPerson pp) {
        Gender mopedGender = Gender.valueOf(pp.getMitoGender().name());
        Occupation mopedOccupation = Occupation.valueOf(pp.getMitoOccupationStatus().name());
        MopedPerson mopedPerson = new MopedPerson(pp.getId(),pp.getAge(),mopedGender,mopedOccupation,pp.hasDriversLicense(),pp.hasTransitPass(),pp.isDisable());
        if(pp.getMitoOccupationStatus().equals(MitoOccupationStatus.WORKER)||pp.getMitoOccupationStatus().equals(MitoOccupationStatus.STUDENT)){
            MopedZone occupationZone = locateMicrolationToMopedZone(new Coordinate(pp.getOccupation().getCoordinate().x,pp.getOccupation().getCoordinate().y));
            mopedPerson.setOccupationZone(occupationZone);
        }
        return mopedPerson;
    }

    private MopedHousehold convertToMopedHh(MitoHousehold mitoHh) {
        int children = DataSet.getChildrenForHousehold(mitoHh);
        MopedZone homeZone = locateMicrolationToMopedZone(new Coordinate(mitoHh.getCoordinate().x,mitoHh.getCoordinate().y));
        return new MopedHousehold(mitoHh.getId(),mitoHh.getMonthlyIncome_EUR(),mitoHh.getAutos(),children,homeZone);
    }

    private MopedZone locateMicrolationToMopedZone(Coordinate coordinate){
        GeometryFactory gf = new GeometryFactory();
        Point point = gf.createPoint(coordinate);
        List<MopedZone> mopedZones = mopedZoneQuadTree.query(point.getEnvelopeInternal());

        for (MopedZone mopedZone : mopedZones){
            if(((Geometry)mopedZone.getShapeFeature().getDefaultGeometry()).contains(point)){
                return mopedZone;
            }
        }

        //TODO: how to deal with null?
        //logger.warn("Coordinate x: " + coordinate.x + ", y: " + coordinate.y + " can not be located to a moped zone.");
        failedMatchingMopedZoneCounter.incrementAndGet();
        return null;
    }


    private void writeOutMoPeDTrips(DataSet dataSet, String scenarioName, String purpose) {
        String outputSubDirectory = "scenOutput/" + scenarioName + "/";

        logger.info("  Writing moped trips file");
        String file = Resources.instance.getString(Properties.BASE_DIRECTORY) + "/" + outputSubDirectory + dataSet.getYear() + "/microData/mopedTrips_" +purpose+ ".csv";
        PrintWriter pwh = MitoUtil.openFileForSequentialWriting(file, false);
        pwh.println("id,origin,originMoped,destination,destinationMoped,purpose,person,distance,mode");
        logger.info("total trip: " + dataSet.getTrips().values().size());
        for (MitoTrip trip : dataSet.getTrips().values()) {
            pwh.print(trip.getId());
            pwh.print(",");
            Location origin = trip.getTripOrigin();
            String originId = "null";
            if(origin != null) {
                originId = String.valueOf(origin.getZoneId());
            }
            pwh.print(originId);
            pwh.print(",");
            pwh.print(trip.getTripOriginMopedZoneId());
            pwh.print(",");
            Location destination = trip.getTripDestination();
            String destinationId = "null";
            if(destination != null) {
                destinationId = String.valueOf(destination.getZoneId());
            }
            pwh.print(destinationId);
            pwh.print(",");
            pwh.print(trip.getTripDestinationMopedZoneId());
            pwh.print(",");
            pwh.print(trip.getTripPurpose());
            pwh.print(",");
            pwh.print(trip.getPerson().getId());
            pwh.print(",");
            pwh.print(trip.getMopedTripDistance());
            pwh.print(",");
            pwh.println(trip.getTripMode());

        }
        pwh.close();
    }


}
