package de.tum.bgu.msm.io;

import com.pb.common.datafile.TableDataSet;
import de.tum.bgu.msm.Properties;
import de.tum.bgu.msm.data.DataSet;
import de.tum.bgu.msm.data.Zone;
import org.apache.log4j.Logger;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by Nico on 17.07.2017.
 */
public class ZonesReader extends AbstractInputReader {

    private static Logger logger = Logger.getLogger(ZonesReader.class);

    private final Map<Integer, Zone> zones = new HashMap<>();

    public ZonesReader(DataSet dataSet) {
        super(dataSet);
    }

    @Override
    public void read() {
        readZones();
        readReductionDampers();
    }

    private void readZones() {
        // read in zones from file
        TableDataSet zonalData = CSVReader.readAsTableDataSet(Properties.getString(Properties.ZONES));
        for (int i = 1; i <= zonalData.getRowCount(); i++) {
            Zone zone = new Zone(zonalData.getColumnAsInt("ZoneId")[i - 1], zonalData.getValueAt(i, "ACRES"));
            zones.put(zone.getZoneId(), zone);
        }
        dataSet.setZones(zones);
    }

    private void readReductionDampers() {
        if (Properties.getBoolean(Properties.REMOVE_TRIPS_AT_BORDER)) {
            TableDataSet reductionNearBorder = CSVReader.readAsTableDataSet(Properties.getString(Properties.REDUCTION_NEAR_BORDER_DAMPERS));
            for (int i = 1; i <= reductionNearBorder.getRowCount(); i++) {
                int id = (int) reductionNearBorder.getValueAt(i, "Zone");
                float damper = reductionNearBorder.getValueAt(i, "damper");
                if (zones.containsKey(id)) {
                    zones.get(id).setReductionAtBorderDamper(damper);
                } else {
                    logger.warn("Damper of " + damper + " refers to non-existing zone " + id + ". Ignoring it.");
                }
            }
        }
    }
}
