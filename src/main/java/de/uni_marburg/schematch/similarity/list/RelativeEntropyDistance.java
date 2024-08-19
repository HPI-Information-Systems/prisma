package de.uni_marburg.schematch.similarity.list;


import java.util.Arrays;
import java.util.List;


public class RelativeEntropyDistance<T> extends ProbabilityMassFunction<T> {
    @Override
    protected float distanceMeasure(List<T> source, List<T> target) {
        double[] pmfA = probabilityMassFunction(source);
        double[] pmfB = probabilityMassFunction(target);
        if (pmfA.length > pmfB.length) {
            double[] helper = Arrays.copyOf(pmfB, pmfA.length);
            pmfB = pmfA.clone();
            pmfA = helper;
        } else if (pmfB.length > pmfA.length) {
            pmfA = Arrays.copyOf(pmfA, pmfB.length);
        }
        double x = 0;
        for (int i = 0; i < pmfA.length; i++) {
            x += pmfA[i] * Math.log(pmfA[i] / pmfB[i]);
        }
        return (float) x;
    }
}