package de.uni_marburg.schematch.matchtask;

import de.uni_marburg.schematch.data.Dataset;
import de.uni_marburg.schematch.data.Scenario;
import de.uni_marburg.schematch.evaluation.Evaluator;
import de.uni_marburg.schematch.evaluation.metric.Metric;
import de.uni_marburg.schematch.evaluation.performance.Performance;
import de.uni_marburg.schematch.matching.Matcher;
import de.uni_marburg.schematch.matchtask.matchstep.MatchStep;
import de.uni_marburg.schematch.matchtask.matchstep.MatchingStep;
import de.uni_marburg.schematch.matchtask.matchstep.SimMatrixBoostingStep;
import de.uni_marburg.schematch.matchtask.matchstep.TablePairGenerationStep;
import de.uni_marburg.schematch.matchtask.tablepair.TablePair;
import de.uni_marburg.schematch.utils.*;
import lombok.Data;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Central class for the schema matching process. For each scenario, there is a match task which
 * sequentially runs all configured matching steps.
 */
@Data
public class MatchTask {
    final static Logger log = LogManager.getLogger(MatchTask.class);

    private final Dataset dataset;
    private final Scenario scenario;
    private final List<MatchStep> matchSteps;
    private final List<Metric> metrics;
    private List<TablePair> tablePairs; // is set by tablepair gen match step
    private Map<MatchStep, Map<Matcher, float[][]>> simMatrices;
    private Map<Metric, Map<MatchStep, Map<Matcher, Performance>>> performances;
    private int[][] groundTruthMatrix;
    private int numSourceColumns, numTargetColumns;
    private Evaluator evaluator;
    private int cacheRead;
    private int cacheWrite;
    private boolean transformedDatasets; // Similarity Matrices need to be transformed into original SM before evaluation.

    public MatchTask(Dataset dataset, Scenario scenario, List<MatchStep> matchSteps, List<Metric> metrics) {
        this.dataset = dataset;
        this.scenario = scenario;
        this.matchSteps = matchSteps;
        this.metrics = metrics;
        this.numSourceColumns = scenario.getSourceDatabase().getNumColumns();
        this.numTargetColumns = scenario.getTargetDatabase().getNumColumns();
        this.simMatrices = new HashMap<>();
        this.performances = new HashMap<>();
        for (Metric metric : metrics) {
            this.performances.put(metric, new HashMap<>());
        }
        this.cacheRead = 0;
        this.cacheWrite = 0;
    }

    /**
     * Sequentially runs all {@link #matchSteps}. For each step, it first calls {@link MatchStep#run},
     * then {@link MatchStep#save}, and finally {@link MatchStep#evaluate}. Each method call checks with
     * their respective config parameter whether it should be executed or not.
     */
    public void runSteps() {
        for (MatchStep matchStep : matchSteps) {
            this.simMatrices.put(matchStep, new HashMap<>());
            for (Metric metric : metrics) {
                this.performances.get(metric).put(matchStep, new HashMap<>());
            }
            matchStep.run(this);
            if (matchStep instanceof TablePairGenerationStep && ConfigUtils.anyEvaluate()) {
                this.readGroundTruth();
                this.evaluator = new Evaluator(this.metrics, this.scenario, this.groundTruthMatrix);
            }
            matchStep.save(this);
            matchStep.evaluate(this);
        }
        if (ConfigUtils.anyReadCache()) {
            log.info("Read " + this.cacheRead + " similarity matrices from cache for scenario " + this.getScenario().getPath());
        }
        if (ConfigUtils.anyWriteCache()) {
            log.info("Wrote " + (this.cacheWrite  - this.cacheRead) + " new similarity matrices to cache for scenario " + this.getScenario().getPath());
        }
    }

    /**
     * Reads ground truth matrices for all table pairs with at least one ground truth correspondence and updates
     * the table pair objects accordingly. Table pairs with no ground truth correspondence will have {@code null}
     * as their ground truth matrix
     */
    public void readGroundTruth() {
        log.debug("Reading ground truth for scenario: " + this.scenario.getPath());
        String basePath = scenario.getPath() + File.separator + Configuration.getInstance().getDefaultGroundTruthDir();

        this.groundTruthMatrix = new int[this.numSourceColumns][this.numTargetColumns];

        for (TablePair tablePair : this.tablePairs) {
            String pathToTablePairGT = basePath + File.separator + tablePair.toString() + ".csv";
            int[][] gtMatrix = InputReader.readGroundTruthFile(pathToTablePairGT);
            if (gtMatrix == null) {
                gtMatrix = tablePair.getEmptyGTMatrix();
            }
            else if (gtMatrix.length != tablePair.getSourceTable().getNumColumns()) {
                throw new IllegalStateException("Number of rows in ground truth does not match number of columns in source table: " + pathToTablePairGT);
            }
            else if (gtMatrix[0].length != tablePair.getTargetTable().getNumColumns()) {
                throw new IllegalStateException("Number of columns in ground truth does not match number of columns in target table: " + pathToTablePairGT);
            }

            int sourceTableOffset = tablePair.getSourceTable().getOffset();
            int targetTableOffset = tablePair.getTargetTable().getOffset();
            ArrayUtils.insertSubmatrixInMatrix(gtMatrix, this.groundTruthMatrix, sourceTableOffset, targetTableOffset);
        }
    }

    public float[][] getEmptySimMatrix() {
        return new float[this.numSourceColumns][this.numTargetColumns];
    }

    public void setSimMatrix(MatchStep matchStep, Matcher matcher, float[][] simMatrix) {
        this.simMatrices.get(matchStep).put(matcher, simMatrix);
    }

    public float[][] getSimMatrix(MatchStep matchStep, Matcher matcher) {
        return this.simMatrices.get(matchStep).get(matcher);
    }

    public float[][] getSimMatrixFromPreviousMatchStep(MatchStep matchStep, Matcher matcher) {
        MatchStep previousMatchStep = null;
        for (MatchStep currMatchStep : this.matchSteps) {
            if (currMatchStep == matchStep) {
                break;
            } else {
                previousMatchStep = currMatchStep;
            }
        }
        return this.getSimMatrix(previousMatchStep, matcher);
    }

    public List<Matcher> getFirstLineMatchers() {
        for (MatchStep matchStep : this.matchSteps) {
            if (matchStep instanceof MatchingStep && ((MatchingStep) matchStep).getLine() == 1) {
                return ((MatchingStep) matchStep).getMatchers();
            }
        }
        return null;
    }
    public List<Matcher> getSecondLineMatchers() {
        for (MatchStep matchStep : this.matchSteps) {
            if (matchStep instanceof MatchingStep && ((MatchingStep) matchStep).getLine() == 2) {
                return ((MatchingStep) matchStep).getMatchers();
            }
        }
        return null;
    }

    public List<Matcher> getMatchersForLine(int line) {
        return switch (line) {
            case 1 -> getFirstLineMatchers();
            case 2 -> getSecondLineMatchers();
            default -> throw new IllegalStateException("Unexpected line value: " + line);
        };
    }

    public List<Matcher> getMatchersForMatchStep(MatchStep matchStep) {
        if (matchStep instanceof MatchingStep ms) {
            return ms.getMatchers();
        } else if (matchStep instanceof SimMatrixBoostingStep smbs) {
            return getMatchersForLine(smbs.getLine());
        } else {
            throw new IllegalStateException("Cannot get matchers for match step: " + matchStep);
        }
    }

    public void setPerformanceForMatcher(Metric metric, MatchStep matchStep, Matcher matcher, Performance performance) {
        this.performances.get(metric).get(matchStep).put(matcher, performance);
    }

    public Performance getPerformanceForMatcher(Metric metric, MatchStep matchStep, Matcher matcher) {
        return this.performances.get(metric).get(matchStep).get(matcher);
    }

    public Map<MatchStep, Map<Matcher, Performance>> getPerformancesForMetric(Metric metric) {
        return this.performances.get(metric);
    }

    public void incrementCacheRead() {
        this.cacheRead += 1;
    }

    public void incrementCacheWrite() {
        this.cacheWrite += 1;
    }
}
