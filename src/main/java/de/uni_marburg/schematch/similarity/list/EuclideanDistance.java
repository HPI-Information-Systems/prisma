package de.uni_marburg.schematch.similarity.list;

import java.util.Arrays;
import java.util.List;

public class EuclideanDistance<T> extends ProbabilityMassFunction<T> {
    @Override
    protected float distanceMeasure(List<T> source, List<T> target) {
        double[] pmfA = probabilityMassFunction(source);
        double[] pmfB = probabilityMassFunction(target);
        int maxLength = Math.max(pmfA.length, pmfB.length);
        pmfA = Arrays.copyOf(pmfA, maxLength);
        pmfB = Arrays.copyOf(pmfB, maxLength);
        double x = 0;
        for (int i = 0; i < pmfA.length; i++) {
            x += Math.pow(pmfA[i] - pmfB[i], 2);
        }
        return (float) Math.sqrt(x);
    }
}