package de.tum.bgu.msm.io;

import de.tum.bgu.msm.MitoUtil;
import de.tum.bgu.msm.Properties;
import de.tum.bgu.msm.data.DataSet;
import de.tum.bgu.msm.data.MitoPerson;
import org.apache.log4j.Logger;

/**
 * Created by Nico on 17.07.2017.
 */
public class JobReader extends AbstractInputReader {

    private static Logger logger = Logger.getLogger(JobReader.class);

    public JobReader(DataSet dataSet) {
        super(dataSet);
    }


    @Override
    public void read() {
        logger.info("  Reading job micro data from ascii file");
        String fileName = Properties.getString(Properties.JOBS);
        CSVReader reader = new CSVReader(fileName, ",", new JobCSVAdapter());
        reader.read();
    }

    class JobCSVAdapter implements CSVAdapter {

        private int posId = -1;
        private int posZone = -1;
        private int posWorker = -1;

        @Override
        public void processHeader(String[] header) {
            posId = MitoUtil.findPositionInArray("id", header);
            posZone = MitoUtil.findPositionInArray("zone", header);
            posWorker = MitoUtil.findPositionInArray("personId", header);
        }

        @Override
        public void processRecord(String[] record) {
            int id = Integer.parseInt(record[posId]);
            int zone = Integer.parseInt(record[posZone]);
            int worker = Integer.parseInt(record[posWorker]);
            if (worker > 0) {
                MitoPerson pp = dataSet.getPersons().get(worker);
                if (pp.getWorkplace() != id) {
                    logger.error("Person " + worker + " has workplace " + pp.getWorkplace() + " in person file but workplace "
                            + id + " in job file.");
                }
                pp.setWorkzone(zone);
            }
        }
    }
}
