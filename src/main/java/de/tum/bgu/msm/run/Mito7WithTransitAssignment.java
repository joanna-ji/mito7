package de.tum.bgu.msm.run;

import ch.sbb.matsim.mobsim.qsim.SBBTransitModule;
import ch.sbb.matsim.mobsim.qsim.pt.SBBTransitEngineQSimModule;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptorModule;
import de.tum.bgu.msm.MitoModel2017;
import de.tum.bgu.msm.data.DataSet;
import de.tum.bgu.msm.data.Day;
import de.tum.bgu.msm.resources.Properties;
import de.tum.bgu.msm.resources.Resources;
import de.tum.bgu.msm.trafficAssignment.CarSkimUpdater;
import de.tum.bgu.msm.trafficAssignment.ConfigureMatsim;
import de.tum.bgu.msm.trafficAssignment.ConfigureMatsimPt;
import de.tum.bgu.msm.util.munich.MunichImplementationConfig;
import org.apache.log4j.Logger;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.Controler;
import org.matsim.core.scenario.MutableScenario;
import org.matsim.core.scenario.ScenarioUtils;

import java.util.EnumMap;

public class Mito7WithTransitAssignment {

    private static final Logger logger = Logger.getLogger(Mito7WithTransitAssignment.class);

    private static boolean runPtAssignment = true;

    private static double planScale = 0.05;
    private static int lastItration = 5;
    private static double reroute = 0.2;
    private static boolean useSBB = true;
    private static boolean deterministic = false;
    private static int maxPlan = 5;
    private static double maxSearchRadius = 1000;
    private static double betaTransfer = 300;

    public static void main(String[] args) {
        logger.info("Started the Microsimulation Transport Orchestrator (MITO) based on 2017 models");
        MitoModel2017 model = MitoModel2017.standAloneModel(args[0], MunichImplementationConfig.get());

        model.run();
        final DataSet dataSet = model.getData();

        boolean runAssignment = Resources.instance.getBoolean(Properties.RUN_TRAFFIC_ASSIGNMENT, false);

        if (runAssignment) {
            logger.info("Running traffic assignment in MATsim");

            Config config;
            if (runPtAssignment) {
                logger.info("Running traffic assignment in MATsim car and pt");
                config = ConfigureMatsimPt.configureMatsim(lastItration,reroute,planScale,10, maxPlan);
                if(useSBB) {
                    ConfigureMatsimPt.setSBBConfig(config, deterministic, maxSearchRadius, betaTransfer);
                }
                String outputSubDirectory = "scenOutput/" + model.getScenarioName() + "/" + dataSet.getYear();

                long start = System.currentTimeMillis();

                final EnumMap<Day, Controler> controlers = new EnumMap<>(Day.class);
                for (Day day : Day.values()) {
                    logger.info("Starting " + day.toString().toUpperCase() + " MATSim simulation");
                    config.controler().setOutputDirectory(Resources.instance.getBaseDirectory().toString() + "/" + outputSubDirectory + "/trafficAssignment/" + day.toString());
                    MutableScenario matsimScenario = (MutableScenario) ScenarioUtils.loadScenario(config);
                    matsimScenario.setPopulation(dataSet.getPopulation(day));
                    controlers.put(day, new Controler(matsimScenario));
                    if(useSBB){

                        // To use the deterministic pt simulation (Part 1 of 2):
                        controlers.get(day).addOverridingModule(new SBBTransitModule());

                        // To use the fast pt router (Part 1 of 1)
                        controlers.get(day).addOverridingModule(new SwissRailRaptorModule());

                        // To use the deterministic pt simulation (Part 2 of 2):
                        controlers.get(day).configureQSimComponents(components -> {
                            new SBBTransitEngineQSimModule().configure(components);

                            // if you have other extensions that provide QSim components, call their configure-method here
                        });

                    }

                    controlers.get(day).run();
                }

                long runTime = System.currentTimeMillis()-start;
                logger.warn("Run time: " + runTime);
            }else{
                if (args.length > 1 && args[1] != null) {
                    config = ConfigUtils.loadConfig(args[1]);
                    ConfigureMatsim.setDemandSpecificConfigSettings(config);
                } else {
                    logger.warn("Using a fallback config with default values as no initial config has been provided.");
                    config = ConfigureMatsim.configureMatsim();
                }

                String outputSubDirectory = "scenOutput/" + model.getScenarioName() + "/" + dataSet.getYear();
/*                config.controler().setOutputDirectory(Resources.instance.getBaseDirectory().toString() + "/" + outputSubDirectory + "/trafficAssignment");

                MutableScenario matsimScenario = (MutableScenario) ScenarioUtils.loadScenario(config);
                matsimScenario.setPopulation(dataSet.getPopulation(Day.weekday));

                Controler controler = new Controler(matsimScenario);
                controler.run();*/

                final EnumMap<Day, Controler> controlers = new EnumMap<>(Day.class);
                for (Day day : Day.values()) {
                    logger.info("Starting " + day.toString().toUpperCase() + " MATSim simulation");
                    config.controler().setOutputDirectory(Resources.instance.getBaseDirectory().toString() + "/" + outputSubDirectory + "/trafficAssignment/" + day.toString());
                    MutableScenario matsimScenario = (MutableScenario) ScenarioUtils.loadScenario(config);
                    matsimScenario.setPopulation(dataSet.getPopulation(day));

                    controlers.put(day, new Controler(matsimScenario));
                    controlers.get(day).run();
                }

                if (Resources.instance.getBoolean(Properties.PRINT_OUT_SKIM, false)) {
                    CarSkimUpdater skimUpdater = new CarSkimUpdater(controlers.get(Day.weekday), model.getData(), model.getScenarioName());
                    skimUpdater.run();
                }
            }
        }
    }
}
