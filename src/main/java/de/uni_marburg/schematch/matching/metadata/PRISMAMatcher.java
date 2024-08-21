package de.uni_marburg.schematch.matching.metadata;

import de.uni_marburg.schematch.data.Database;
import de.uni_marburg.schematch.data.MetaNodesDatabaseGraph;
import de.uni_marburg.schematch.data.Scenario;
import de.uni_marburg.schematch.data.Table;
import de.uni_marburg.schematch.matching.TablePairMatcher;
import de.uni_marburg.schematch.matchtask.tablepair.TablePair;
import de.uni_marburg.schematch.utils.PythonUtils;
import lombok.*;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.jboss.logging.annotations.ValidIdRanges;

import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@EqualsAndHashCode(callSuper=false)
@NoArgsConstructor
public class PRISMAMatcher extends TablePairMatcher {
    @Getter
    @Setter
    public Boolean postprocessing = true;
    @Getter
    @Setter
    public double GammaStrucAttr = 0.5;
    @Getter
    @Setter
    public String kindOfFeature = "Distribution"; // Entropy
    @Getter
    @Setter
    public Boolean thresholdMatches = true;
    @Getter
    @Setter
    public String gdepThreshold="0.0";

    final private Integer serverPort = 5004;

    @Override
    public float[][] match(TablePair tablePair) {

        // Extract Tables
        Table sourceTable = tablePair.getSourceTable();
        Table targetTable = tablePair.getTargetTable();
        // Extract Columns
        // Extract and load scenario meta data
        Scenario scenario = tablePair.getScenario();
        // Extract and load database meta data
        Database source = scenario.getSourceDatabase();
        Database target = scenario.getTargetDatabase();


        MetaNodesDatabaseGraph sourceGraph = null;
        MetaNodesDatabaseGraph targetGraph = null;
        for(MetaNodesDatabaseGraph graph : source.getGraphs()){
            if(Objects.equals(graph.getGDepThreshold(), this.gdepThreshold)){
                sourceGraph = graph;
                break;
            }
        }
        for(MetaNodesDatabaseGraph graph : target.getGraphs()){
            if(Objects.equals(graph.getGDepThreshold(), this.gdepThreshold)){
                targetGraph = graph;
                break;
            }
        }
        if(sourceGraph == null || targetGraph == null){
            getLogger().error("No graphs built for threshold " + gdepThreshold);
            return tablePair.getEmptySimMatrix();
        }
        float[][] alignment_matrix;
        try {
            HttpResponse<String> response = PythonUtils.sendMatchRequest(serverPort, List.of(
                    new ImmutablePair<>("source_graph_path", sourceGraph.exportPath().toString()),
                    new ImmutablePair<>("source_table", sourceTable.getName()),
                    new ImmutablePair<>("target_graph_path", targetGraph.exportPath().toString()),
                    new ImmutablePair<>("target_table", targetTable.getName()),
                    new ImmutablePair<>("features_dir", "target/features/" + scenario.getDataset().getName() + "/" + scenario.getName()),
                    new ImmutablePair<>("xNetMFGammaStrucAttr", String.valueOf(GammaStrucAttr)),
                    new ImmutablePair<>("postprocessing", String.valueOf(postprocessing)),
                    new ImmutablePair<>("thresholdMatches", String.valueOf(thresholdMatches)),
                    new ImmutablePair<>("kind_of_feature", kindOfFeature)
            ));
            alignment_matrix = PythonUtils.readMatcherOutput(Arrays.stream(response.body().split("\n")).toList(), tablePair);

        } catch (Exception e){
            getLogger().error("Running Node2Vec Matcher failed, is the server running?", e);
            return  tablePair.getEmptySimMatrix();
        }

        return alignment_matrix;
    }
}
