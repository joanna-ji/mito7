package de.tum.bgu.msm.util.skim;

import com.google.common.collect.Sets;
import de.tum.bgu.msm.data.DataSet;
import de.tum.bgu.msm.data.travelTimes.SkimTravelTimes;
import de.tum.bgu.msm.io.input.readers.ZonesReader;
import de.tum.bgu.msm.io.output.OmxMatrixWriter;
import de.tum.bgu.msm.resources.Resources;
import de.tum.bgu.msm.util.MitoUtil;
import de.tum.bgu.msm.util.matrices.IndexedDoubleMatrix2D;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Network;
import org.matsim.contrib.dvrp.router.TimeAsTravelDisutility;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;
import org.matsim.core.events.EventsManagerImpl;
import org.matsim.core.mobsim.framework.Mobsim;
import org.matsim.core.mobsim.qsim.QSimBuilder;
import org.matsim.core.network.NetworkChangeEvent;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.io.NetworkChangeEventsParser;
import org.matsim.core.router.costcalculators.FreespeedTravelTimeAndDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.scenario.MutableScenario;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ConvertNetworkChangeEventsToSkim {


    private static final String PROPERTIES_PATH = "mito2.0.properties";
    private static final String NETWORK_PATH = "Network.xml.gz";
    private static final String NETWORK_CHANGE_EVENTS_PATH = "/nce.xml.gz";
    private static final String OUTPUT_SKIM_PATH = "skim.omx";
    private static final int TIME = 8 * 3600;

    public static void main(String[] args) {

        Resources.initializeResources(PROPERTIES_PATH);
        MitoUtil.initializeRandomNumber();

        DataSet dataset = new DataSet();
        new ZonesReader(dataset).read();


        Config config = ConfigUtils.createConfig();
        config.network().setTimeVariantNetwork(true);
        CoordinateTransformation transform = TransformationFactory.getCoordinateTransformation("EPSG:25832", TransformationFactory.DHDN_GK4);
        Network network = NetworkUtils.readNetwork(NETWORK_PATH, config.network(), transform);

        List<NetworkChangeEvent> nces = new ArrayList<>();
        new NetworkChangeEventsParser(network, nces).readFile(NETWORK_CHANGE_EVENTS_PATH);
        for (NetworkChangeEvent nce : nces) {
            NetworkUtils.addNetworkChangeEvent(network, nce);
        }


        MutableScenario scenario = ScenarioUtils.createMutableScenario(ConfigUtils.createConfig());
        scenario.setNetwork(network);
        TravelTime travelTime = new FreespeedTravelTimeAndDisutility(new PlanCalcScoreConfigGroup());


        IndexedDoubleMatrix2D travelTimeSkim = new IndexedDoubleMatrix2D(dataset.getZones().values(), dataset.getZones().values());
        IndexedDoubleMatrix2D travelDistanceSkim = new IndexedDoubleMatrix2D(dataset.getZones().values(), dataset.getZones().values());
        Matsim2Skim matsim2Skim = new Matsim2Skim(network, new TimeAsTravelDisutility(travelTime), travelTime);
        matsim2Skim.calculateMatrixFromMatsim(3, travelTimeSkim, travelDistanceSkim, TIME, dataset.getZones().values());

        SkimTravelTimes skimTravelTimes = new SkimTravelTimes();
        skimTravelTimes.updateSkimMatrix(travelTimeSkim, TransportMode.car);

        int dimension = dataset.getZones().size();
        OmxMatrixWriter.createOmxFile(OUTPUT_SKIM_PATH, dimension);
        skimTravelTimes.printOutCarSkim(TransportMode.car, OUTPUT_SKIM_PATH, "ttCarCongested");
    }

    private static Mobsim queueSim(Scenario scenario) {
        return new QSimBuilder(scenario.getConfig()).build(scenario, new EventsManagerImpl());
    }
}
