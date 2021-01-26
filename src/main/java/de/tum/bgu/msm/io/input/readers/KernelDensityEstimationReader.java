package de.tum.bgu.msm.io.input.readers;

import de.tum.bgu.msm.data.DataSet;
import de.tum.bgu.msm.data.Id;
import de.tum.bgu.msm.data.Purpose;
import de.tum.bgu.msm.io.input.AbstractCsvReader;
import de.tum.bgu.msm.util.MitoUtil;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class KernelDensityEstimationReader extends AbstractCsvReader {

    private final double[] distribution = new double[8192];
    private final Purpose purpose;

    private int purposeIndex;
    private int row = 0;

    private final Path path;


    public KernelDensityEstimationReader(DataSet dataSet, Purpose purpose, Path path) {
        super(dataSet);
        this.purpose = purpose;
        this.path = path;
        read();
    }

    @Override
    protected void processHeader(String[] header) {
        purposeIndex = MitoUtil.findPositionInArray(purpose.toString(), header);
    }

    @Override
    protected void processRecord(String[] record) {
        double value = Double.parseDouble(record[purposeIndex]);
        distribution[row] = value;
        row++;
    }

    @Override
    public void read() {
        super.read(path, ",");
    }

    public double[] readDistribution(){
        read();
        return distribution;
    };
}
