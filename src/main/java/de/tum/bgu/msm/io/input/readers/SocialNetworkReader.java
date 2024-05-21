package de.tum.bgu.msm.io.input.readers;

import de.tum.bgu.msm.data.DataSet;
import de.tum.bgu.msm.data.SocialNetworkType;
import de.tum.bgu.msm.io.input.AbstractCsvReader;
import org.apache.log4j.Logger;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SocialNetworkReader extends AbstractCsvReader {

	private static final Logger logger = Logger.getLogger(SocialNetworkReader.class);

	private int posEgo = -1;
	private int posAlter = -1;
	private int countError;
	private SocialNetworkType socialNetworkType;

	public SocialNetworkReader(DataSet dataSet) {
		super(dataSet);
	}

	public void read() {
//		logger.info("  Reading ego-alter household data from csv file");
//		Path filePath = Paths.get("C:\\models\\tengos_episim\\input\\egoAlterHousehold5pct.csv");
//		socialNetworkType = SocialNetworkType.HOUSEHOLD;
//		super.read(filePath, ",");
//		logger.info(countError + " Egos are not existed in the trip person map.");
//		logger.info("  Reading ego-alter job data from csv file");
//		filePath = Paths.get("C:\\models\\tengos_episim\\input\\egoAlterJob5pct.csv");
//		socialNetworkType = SocialNetworkType.COWORKER;
//		super.read(filePath, ",");
//		logger.info(countError + " Egos are not existed in the trip person map.");
//		logger.info("  Reading ego-alter nursing home data from csv file");
//		filePath = Paths.get("C:\\models\\tengos_episim\\input\\egoAlterNursingHome5pct.csv");
//		socialNetworkType = SocialNetworkType.NURSINGHOME;
//		super.read(filePath, ",");
//		logger.info(countError + " Egos are not existed in the trip person map.");
//		logger.info("  Reading ego-alter school data from csv file");
//		filePath = Paths.get("C:\\models\\tengos_episim\\input\\egoAlterSchool5pct.csv");
//		socialNetworkType = SocialNetworkType.SCHOOLMATE;
//		super.read(filePath, ",");
//		logger.info(countError + " Egos are not existed in the trip person map.");
//		logger.info("  Reading ego-alter dwelling data from csv filemum");
//		filePath = Paths.get("C:\\models\\tengos_episim\\input\\egoAlterDwelling5pct.csv");
//		socialNetworkType = SocialNetworkType.NEIGHBOR;
//		super.read(filePath, ",");
//		logger.info(countError + " Egos are not existed in the trip person map.");
		logger.info("  Reading ego-alter friend data from csv file");
		Path filePath = Paths.get("C:\\models\\tengos_episim\\input\\social_net_edge_list_v_3.0\\egoAlterFriends5pct.csv");
		socialNetworkType = SocialNetworkType.FRIEND;
		super.read(filePath, ",");
		logger.info(countError + " Egos are not existed in the trip person map.");
	}

	@Override
	public void processHeader(String[] header) {
		List<String> headerList = Arrays.asList(header);
		posEgo = headerList.indexOf("ego");
		posAlter = headerList.indexOf("alter");
	}

	@Override
	public void processRecord(String[] record) {
		final int ego = Integer.parseInt(record[posEgo]);
		final int alter = Integer.parseInt(record[posAlter]);
		if(dataSet.getPersons().get(ego)==null){
			countError++;
			//logger.error("Ego: " + ego + " is not in the person map!");
		}else {
			if(dataSet.getPersons().get(ego).getAlterLists().get(socialNetworkType)==null){
				dataSet.getPersons().get(ego).getAlterLists().put(socialNetworkType,new ArrayList<>());
			}
			dataSet.getPersons().get(ego).getAlterLists().get(socialNetworkType).add(alter);
		}

	}
}
