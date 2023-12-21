package de.uni_marburg.schematch.matchtask.matchstep;

import de.uni_marburg.schematch.matching.Matcher;
import de.uni_marburg.schematch.matchtask.MatchTask;
import de.uni_marburg.schematch.utils.Configuration;
import de.uni_marburg.schematch.utils.OutputWriter;
import de.uni_marburg.schematch.utils.ResultsUtils;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@EqualsAndHashCode(callSuper = true)
public class MatchingStep extends MatchStep {
    private final static Logger log = LogManager.getLogger(MatchingStep.class);

    private final int line;
    private final Map<String, List<Matcher>> matchers;
    private final Map<Matcher, float[][]> simMatrices;

    public MatchingStep(boolean doRun, boolean doSave, boolean doEvaluate, int line, Map<String, List<Matcher>> matchers) {
        super(doRun, doSave, doEvaluate);
        this.line = line;
        this.matchers = matchers;
        this.simMatrices = new HashMap<>();
    }

    @Override
    public String toString() {
        return super.toString() + "Line" + line;
    }

    @Override
    public void run(MatchTask matchTask) {
        log.debug("Running " + this.line + ". line matching on scenario: " + matchTask.getScenario().getPath());

        for (String matcherName : this.matchers.keySet()) {
            for (Matcher matcher : this.matchers.get(matcherName)) {
                log.trace("Processing " + this.line + ". line matcher " + matcherName);
                float[][] simMatrix = matcher.match(matchTask, this);
                this.setSimMatrix(matcher, simMatrix);
            }
        }
    }

    @Override
    public void save(MatchTask matchTask) {
        if ((line == 1 && !Configuration.getInstance().isSaveOutputFirstLineMatchers()) ||
                line == 2 && !Configuration.getInstance().isSaveOutputSecondLineMatchers()) {
            return;
        }

        log.debug("Saving " + this.line + ". line matching output for scenario: " + matchTask.getScenario().getPath());

        Path basePath = ResultsUtils.getOutputBaseResultsPathForMatchStepInScenario(matchTask, this);

        for (String matcherName : this.matchers.keySet()) {
            for (Matcher matcher : this.matchers.get(matcherName)) {
                float[][] simMatrix = this.getSimMatrix(matcher);
                OutputWriter.writeSimMatrix(basePath.resolve(matcher.toString()).resolve(".csv"), simMatrix);
            }
        }
    }

    @Override
    public void evaluate(MatchTask matchTask) {
        if (!Configuration.getInstance().isEvaluateFirstLineMatchers()) {
           return;
        }

        log.debug("Evaluating " + this.line + ". line matching output for scenario: " + matchTask.getScenario().getPath());

        /*List<TablePair> tablePairs = matchTask.getTablePairs();

        for (TablePair tablePair : tablePairs) {
            int[][] gtMatrix = tablePair.getGroundTruth();
            for (String matcherName : this.matchers.keySet()) {
                for (Matcher matcher : this.matchers.get(matcherName)) {
                    float[][] simMatrix = tablePair.getResultsForFirstLineMatcher(matcher);
                    tablePair.addPerformanceForFirstLineMatcher(matcher, EvaluatorOld.evaluateMatrix(simMatrix, gtMatrix));
                }
            }
        }

        EvalWriter evalWriter = new EvalWriter(matchTask, this);
        evalWriter.writeMatchStepPerformance();*/
    }

    public void setSimMatrix(Matcher matcher, float[][] simMatrix) {
        this.simMatrices.put(matcher, simMatrix);
    }

    public float[][] getSimMatrix(Matcher matcher) {
        return this.simMatrices.get(matcher);
    }
}
