package de.uni_marburg.schematch.similarity.list;

import de.uni_marburg.schematch.similarity.SimilarityMeasure;

import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public abstract class ProbabilityMassFunction<T> implements SimilarityMeasure<List<T>> {
    @Override
    public float compare(List<T> source, List<T> target) {
        return distanceMeasure(source, target);
    }

    protected abstract float distanceMeasure(List<T> source, List<T> target);

    /**
     * Convert the elements of the column to their string representation, count occurrences, calculate the frequency,
     * and then sort and convert to an array of doubles starting with the highest probability and descending
     *
     * @param column the values to have their probabilities calculated relative to each other
     * @return array of probabilites for each value (values themselves not relevant and thus discarded)
     *
     * disclaimer: this method was partially written in C# and transpiled into Java using GitHub Copilot
     */
    protected double[] probabilityMassFunction(List<T> column) {
        return column.stream().map(T::toString) // Convert each element to String
                .collect(Collectors.groupingBy(Function.identity(),
                        Collectors.collectingAndThen(Collectors.counting(),
                                count -> (double) count / (double) column.size()))) // Calculate frequency
                .values().stream().sorted(Comparator.reverseOrder()) // Sort frequencies
                .mapToDouble(Number::doubleValue) // Convert back to double
                .toArray(); // Convert to array
    }
}