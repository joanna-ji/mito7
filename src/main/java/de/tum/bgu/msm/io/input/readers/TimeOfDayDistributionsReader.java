package de.tum.bgu.msm.io.input.readers;

import cern.colt.matrix.tdouble.DoubleMatrix1D;
import cern.colt.matrix.tdouble.impl.DenseDoubleMatrix1D;
import de.tum.bgu.msm.data.DataSet;
import de.tum.bgu.msm.data.Purpose;
import de.tum.bgu.msm.io.input.AbstractCsvReader;
import de.tum.bgu.msm.resources.Properties;
import de.tum.bgu.msm.resources.Resources;
import de.tum.bgu.msm.util.MitoUtil;

import java.util.EnumMap;

public class TimeOfDayDistributionsReader extends AbstractCsvReader {

    private final EnumMap<Purpose, DoubleMatrix1D> arrivalTimeCumProbByPurpose = new EnumMap<>(Purpose.class);
    private final EnumMap<Purpose, DoubleMatrix1D> durationCumProbByPurpose = new EnumMap<>(Purpose.class);
    private final EnumMap<Purpose, DoubleMatrix1D> departureTimeCumProbByPurpose = new EnumMap<>(Purpose.class);

    private int minuteIndex;
    private int hbw_arrival_index;
    private int hbe_arrival_index;
    private int hbs_arrival_index;
    private int hbr_arrival_index;
    private int hbo_arrival_index;
    private int nhbo_arrival_index;
    private int nhbw_arrival_index;

    private int rrt_departure_index;

    private int hbw_duration_index;
    private int hbe_duration_index;
    private int hbs_duration_index;
    private int hbr_duration_index;
    private int hbo_duration_index;

    public TimeOfDayDistributionsReader(DataSet dataSet) {

        super(dataSet);
        for (Purpose purpose : Purpose.values()) {
            arrivalTimeCumProbByPurpose.put(purpose, new DenseDoubleMatrix1D(1440 + 1));
            durationCumProbByPurpose.put(purpose, new DenseDoubleMatrix1D(1440 + 1));
            departureTimeCumProbByPurpose.put(purpose, new DenseDoubleMatrix1D(1440 + 1));
        }
    }

    @Override
    protected void processHeader(String[] header) {
        minuteIndex = MitoUtil.findPositionInArray("minute", header);
        hbw_arrival_index = MitoUtil.findPositionInArray("arrival_hbw", header);
        hbe_arrival_index = MitoUtil.findPositionInArray("arrival_hbe", header);
        hbs_arrival_index = MitoUtil.findPositionInArray("arrival_hbs", header);
        hbr_arrival_index = MitoUtil.findPositionInArray("arrival_hbr", header);
        hbo_arrival_index = MitoUtil.findPositionInArray("arrival_hbo", header);
        nhbo_arrival_index = MitoUtil.findPositionInArray("arrival_nhbo", header);
        nhbw_arrival_index = MitoUtil.findPositionInArray("arrival_nhbw", header);

        rrt_departure_index = MitoUtil.findPositionInArray("departure_rrt", header);

        hbw_duration_index = MitoUtil.findPositionInArray("duration_hbw", header);
        hbe_duration_index = MitoUtil.findPositionInArray("duration_hbe", header);
        hbs_duration_index = MitoUtil.findPositionInArray("duration_hbs", header);
        hbr_duration_index = MitoUtil.findPositionInArray("duration_hbr", header);
        hbo_duration_index = MitoUtil.findPositionInArray("duration_hbo", header);
    }

    @Override
    protected void processRecord(String[] record) {
        int minute = Integer.parseInt(record[minuteIndex]);

        arrivalTimeCumProbByPurpose.get(Purpose.HBW).setQuick(minute, Double.parseDouble(record[hbw_arrival_index]));
        arrivalTimeCumProbByPurpose.get(Purpose.HBE).setQuick(minute, Double.parseDouble(record[hbe_arrival_index]));
        arrivalTimeCumProbByPurpose.get(Purpose.HBS).setQuick(minute, Double.parseDouble(record[hbs_arrival_index]));
        arrivalTimeCumProbByPurpose.get(Purpose.HBR).setQuick(minute, Double.parseDouble(record[hbr_arrival_index]));
        arrivalTimeCumProbByPurpose.get(Purpose.HBO).setQuick(minute, Double.parseDouble(record[hbo_arrival_index]));
        arrivalTimeCumProbByPurpose.get(Purpose.NHBO).setQuick(minute, Double.parseDouble(record[nhbo_arrival_index]));
        arrivalTimeCumProbByPurpose.get(Purpose.NHBW).setQuick(minute, Double.parseDouble(record[nhbw_arrival_index]));

        departureTimeCumProbByPurpose.get(Purpose.RRT).setQuick(minute, Double.parseDouble(record[rrt_departure_index]));

        durationCumProbByPurpose.get(Purpose.HBW).setQuick(minute, Double.parseDouble(record[hbw_duration_index]));
        durationCumProbByPurpose.get(Purpose.HBE).setQuick(minute, Double.parseDouble(record[hbe_duration_index]));
        durationCumProbByPurpose.get(Purpose.HBS).setQuick(minute, Double.parseDouble(record[hbs_duration_index]));
        durationCumProbByPurpose.get(Purpose.HBR).setQuick(minute, Double.parseDouble(record[hbr_duration_index]));
        durationCumProbByPurpose.get(Purpose.HBO).setQuick(minute, Double.parseDouble(record[hbo_duration_index]));
    }

    @Override
    public void read() {
        super.read(Resources.instance.getTimeOfDayDistributionsFilePath(), ",");
        dataSet.setArrivalMinuteCumProbByPurpose(arrivalTimeCumProbByPurpose);
        dataSet.setDurationMinuteCumProbByPurpose(durationCumProbByPurpose);
        dataSet.setDepartureMinuteCumProbByPurpose(departureTimeCumProbByPurpose);
    }


}
