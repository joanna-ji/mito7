package de.tum.bgu.msm.io.output;

import de.tum.bgu.msm.util.matrices.IndexedDoubleMatrix2D;
import omx.OmxFile;
import omx.OmxLookup;
import omx.OmxMatrix;
import omx.hdf5.OmxConstants;

import java.util.HashSet;
import java.util.Set;

public class OmxMatrixWriter {


    public static void createOmxFile(String omxFilePath, int numberOfZones) {

        try (OmxFile omxFile = new OmxFile(omxFilePath)) {
            int dim0 = numberOfZones -1;
            int dim1 = dim0;
            int[] shape = {dim0, dim1};
            int lookup1NA = -1;
            int[] lookup1Data = new int[dim0];
            Set<Integer> lookup1Used = new HashSet<>();
            for (int i = 0; i < lookup1Data.length; i++) {
                int lookup = i + 1;
                lookup1Data[i] = lookup1Used.add(lookup) ? lookup : lookup1NA;
            }
            OmxLookup.OmxIntLookup lookup1 = new OmxLookup.OmxIntLookup("lookup", lookup1Data, lookup1NA);
            omxFile.openNew(shape);
            omxFile.addLookup(lookup1);
            omxFile.save();

        }
    }


    public static void createOmxSkimMatrix(IndexedDoubleMatrix2D matrix, String omxFilePath, String omxMatrixName) {
        try (OmxFile omxFile = new OmxFile(omxFilePath)) {
            omxFile.openReadWrite();
            double mat1NA = -1;

            double[][] array = matrix.toNonIndexedArray();
            int[] indices = matrix.getRowLookupArray();
            OmxLookup lookup = new OmxLookup.OmxIntLookup("zone", indices, -1);

            OmxMatrix.OmxDoubleMatrix mat1 = new OmxMatrix.OmxDoubleMatrix(omxMatrixName, array, mat1NA);
            mat1.setAttribute(OmxConstants.OmxNames.OMX_DATASET_TITLE_KEY.getKey(), "skim_matrix");
            omxFile.addMatrix(mat1);
            omxFile.addLookup(lookup);
            omxFile.save();
            System.out.println(omxFile.summary());
            omxFile.close();
            System.out.println(omxMatrixName + "matrix written");
        }
    }
}
