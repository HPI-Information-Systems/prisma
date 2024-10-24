package de.uni_marburg.schematch.matching.sota;

import de.uni_marburg.schematch.data.Database;
import de.uni_marburg.schematch.matching.Matcher;
import de.uni_marburg.schematch.matchtask.MatchTask;
import de.uni_marburg.schematch.matchtask.matchstep.MatchingStep;
import de.uni_marburg.schematch.similarity.list.EuclideanDistance;
import de.uni_marburg.schematch.similarity.list.ProbabilityMassFunction;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.ArrayUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Uninterpreted Schema Matching with Embedded Value Mapping under Opaque Column Names and Data Values
 */
@NoArgsConstructor
public class EmbeddedMappingMatcher extends Matcher {

    @Override
    public float[][] match(MatchTask matchTask, MatchingStep matchStep) {
        return match(matchTask, matchStep, 1000);
    }

    public float[][] match(MatchTask matchTask, MatchingStep matchStep, int maxIterations) {
        var scenario = matchTask.getScenario();
        getLogger().debug("Running embedded matching matcher for scenario '{}'.",
                scenario.getName());
        Database t1 = scenario.getSourceDatabase();
        Database t2 = scenario.getTargetDatabase();
        double[][] similarityByColumn = new double[t1.getNumColumns()][t2.getNumColumns()];
        ProbabilityMassFunction<String> similarityMeasure = new EuclideanDistance<>();

        getLogger().debug("Start creation of similarity matrix based on the probability mass function for scenario '{}'.",
                scenario.getName());
        for (int i = 0; i < similarityByColumn.length; i++) {
            for (int j = 0; j < similarityByColumn[i].length; j++) {
                similarityByColumn[i][j] = similarityMeasure.compare(t1.getColumnByIndex(i).getValues(), t2.getColumnByIndex(j).getValues());
            }
        }

        // randomization
        // long seed = 42;
        // List<Integer> rowMapping = IntStream.range(0, t1.getNumColumns()).boxed().collect(Collectors.toList());
        // Collections.shuffle(rowMapping, new Random(seed));
        // double[][] shuffledSimilarityByColumn = new double[t1.getNumColumns()][t2.getNumColumns()];
        // for (int i = 0; i < t1.getNumColumns(); i++) {
        //     shuffledSimilarityByColumn[i] = similarityByColumn[rowMapping.get(i)];
        // }
        // similarityByColumn = shuffledSimilarityByColumn;

        getLogger().debug("Successfully created similarity Matrix based on the probability mass function for scenario '{}'.",
                scenario.getName());
        int maxColumns = Integer.max(similarityByColumn.length, similarityByColumn[0].length);
        boolean[][] initialMatch = new boolean[maxColumns][maxColumns];
        IntStream.range(0, maxColumns).forEach(i -> initialMatch[maxColumns - 1 - i][i] = true);
        boolean[][] bestMatch = initialMatch;
        double disBestMatch = lookUpDissimilarity(similarityByColumn, bestMatch);

        //now to some two-opt switching
        int currentIteration = 0;
        boolean furtherImprovement = true;
        while (furtherImprovement && currentIteration <= maxIterations) {
            currentIteration++;
            furtherImprovement = false;
            getLogger().debug("Start two-opt switching the scenario '{}'.",
                    scenario.getName());
            for (var outer = 0; outer < maxColumns; outer++) {
                for (var inner = outer; inner < maxColumns; inner++) {
                    if (bestMatch[outer][inner]) continue;

                    boolean[][] newMatch = twoOptSwitch(bestMatch, maxColumns, inner, outer);

                    double disNewMatch = lookUpDissimilarity(similarityByColumn, newMatch);
                    if (disNewMatch < disBestMatch) { //this can be optimized by only looking up the delta in dissimilarity
                        getLogger().debug("EmbeddedMappingMatcher improve match by switching: '{}' x '{}'", outer, inner);
                        bestMatch = newMatch;
                        disBestMatch = disNewMatch;
                        furtherImprovement = true;
                    }
                }
            }
        }
        getLogger().debug("Finished two-opt switching the scenario '{}'.",
                scenario.getName());

        float[][] finalResultAsPrimitiveFloat = new float[t1.getNumColumns()][t2.getNumColumns()];
        for (int i = 0; i < finalResultAsPrimitiveFloat.length; i++) {
            for (int j = 0; j < finalResultAsPrimitiveFloat[i].length; j++) {
                finalResultAsPrimitiveFloat[i][j] = bestMatch[i][j] ? 1.0f : 0.0f;
            }
        }

        // undo randomization
        ArrayList<float[]> finale = new ArrayList<>(Arrays.stream(finalResultAsPrimitiveFloat).toList());
        //unshuffle(finale, rowMapping);
        return finale.toArray(new float[0][0]);
    }

    private static void unshuffle(ArrayList<float[]> list, List<Integer> rowMapping) {
        ArrayList<float[]> unshuffledList = new ArrayList<>(Collections.nCopies(list.size(), null));
        for (int i = 0; i < list.size(); ++i) {
            unshuffledList.set(rowMapping.get(i), list.get(i));
        }
        for (int i = 0; i < list.size(); i++) {
            list.set(i, unshuffledList.get(i));
        }
    }

    // 1 0 x    0 0 1      1 x 0    0 1 0
    // 0 1 0 -> 0 1 0  or  0 1 0 -> 1 0 0  or  ...
    // 0 0 1    1 0 0      0 0 1    0 0 1
    private static boolean[][] twoOptSwitch(boolean[][] bestMatch, int maxColumns, int inner, int outer) {
        boolean[][] newMatch = new boolean[bestMatch.length][bestMatch[0].length];
        for (int i = 0; i < bestMatch.length; i++) {
            System.arraycopy(bestMatch[i], 0, newMatch[i], 0, bestMatch[i].length);
        }
        boolean[] lookUpHelp = new boolean[maxColumns];
        lookUpHelp[inner] = true;
        int oldOuter = IntStream.range(0, maxColumns).filter(index -> Arrays.equals(bestMatch[index], lookUpHelp)).findFirst().orElseThrow();
        int oldInner = ArrayUtils.indexOf(bestMatch[outer], true);
        newMatch[outer][inner] = true;
        newMatch[oldOuter][inner] = false;
        newMatch[outer][oldInner] = false;
        newMatch[oldOuter][oldInner] = true;
        return newMatch;
    }

    private double lookUpDissimilarity(double[][] simMatrix, boolean[][] match) {
        return IntStream.range(0, simMatrix.length).mapToDouble(sourceColumn ->
                        IntStream.range(0, simMatrix[sourceColumn].length).mapToDouble(targetColumn ->
                                        match[sourceColumn][targetColumn] ? simMatrix[sourceColumn][targetColumn] : 0)
                                .sum())
                .sum();
    }
}