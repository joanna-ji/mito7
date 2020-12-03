package de.tum.bgu.msm.modules.modeChoice;

import org.apache.log4j.Logger;
import org.matsim.core.utils.collections.Tuple;

import java.util.*;


public final class LogitProbabilitiesCalculator {

    private static final Logger logger = Logger.getLogger(LogitProbabilitiesCalculator.class);

    public static <E extends Enum<E>> EnumMap<E, Double> multinomialLogit(EnumMap<E, Double> utilities) {

        Class<E> enumClass = utilities.keySet().iterator().next().getDeclaringClass();

        EnumMap<E, Double> probabilities = new EnumMap<>(enumClass);
        EnumMap<E, Double> expUtils = new EnumMap<>(enumClass);
        double expUtilsSum = 0.;

        for(E option : utilities.keySet()) {
            double expUtil = Math.exp(utilities.get(option));
            expUtils.put(option, expUtil);
            expUtilsSum += expUtil;
        }

        for(E option : utilities.keySet()) {
            probabilities.put(option, expUtils.get(option) / expUtilsSum);
        }

        return probabilities;
    }

    public static <E extends Enum<E>> EnumMap<E, Double> nestedLogit(EnumMap<E, Double> utilities,
                                                                     List<Tuple<EnumSet<E>, Double>> nests) {

        Class<E> enumClass = utilities.keySet().iterator().next().getDeclaringClass();

        EnumMap<E, Double> expOptionUtils = new EnumMap(enumClass);
        EnumMap<E, Double> expNestSums = new EnumMap(enumClass);
        EnumMap<E, Double> expNestUtils = new EnumMap(enumClass);
        EnumMap<E, Double> probabilities = new EnumMap(enumClass);
        double expSumRoot = 0;

        for(Tuple<EnumSet<E>,Double> nest : nests) {
            double expNestSum = 0;
            EnumSet<E> nestOptions = nest.getFirst();
            double nestingCoefficient = nest.getSecond();
            for(E option : nestOptions) {
                double expOptionUtil = Math.exp(utilities.get(option) / nestingCoefficient);
                expOptionUtils.put(option, expOptionUtil);
                expNestSum += expOptionUtil;
            }
            double expNestUtil = Math.exp(nestingCoefficient * Math.log(expNestSum));
            for(E option : nestOptions) {
                expNestSums.put(option, expNestSum);
                expNestUtils.put(option, expNestUtil);
            }
            expSumRoot += expNestUtil;
        }

        for (E option : utilities.keySet()) {
            double expSumNest = expNestSums.get(option);
            if(expSumNest == 0) {
                probabilities.put(option, 0.);
            } else {
                probabilities.put(option, (expOptionUtils.get(option) * expNestUtils.get(option)) / (expSumNest * expSumRoot));
            }
        }
        return probabilities;
    }

}
