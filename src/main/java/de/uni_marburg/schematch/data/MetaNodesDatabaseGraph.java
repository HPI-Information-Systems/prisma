package de.uni_marburg.schematch.data;

import de.uni_marburg.schematch.data.metadata.dependency.FunctionalDependency;
import de.uni_marburg.schematch.data.metadata.dependency.Metanome;
import de.uni_marburg.schematch.data.metadata.dependency.UniqueColumnCombination;
import de.uni_marburg.schematch.evaluation.Evaluator;
import de.uni_marburg.schematch.utils.MetadataUtils;
import lombok.Getter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleDirectedGraph;
import org.jgrapht.nio.graphml.GraphMLExporter;

import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;

public class MetaNodesDatabaseGraph extends DatabaseGraph {
    private final Logger logger = LogManager.getLogger(this.getClass());
    private static Integer globalGraphCounter = 1;


    private final Database database;
    private final SimpleDirectedGraph<String, DefaultEdge> graph = new SimpleDirectedGraph<>(DefaultEdge.class);
    @Getter
    private final Integer graphId; // in order to ensure that two graphs won't have identically named nodes
    @Getter
    private final String gDepThreshold;
    private Integer uccCounter = 1;
    private Integer fdCounter = 1;

    private List<String> columnsToExclude = List.of(
//          "phone",
//          "ptmxztdd" // author_fname
    );

    public MetaNodesDatabaseGraph(final Database database, String gDepThreshold) {
        this.database = database;
        this.gDepThreshold = gDepThreshold;
        this.graphId = globalGraphCounter;
        globalGraphCounter++;

        graphBuildingTime = Evaluator.profileRuntime(() -> {
            buildFor(database);
            exportGraph();
            return 0;
        }).getRight();
    }

    private void buildFor(final Database database) {
        graph.addVertex(graphRoot());

        for (Table table : database.getTables()) {
            graph.addVertex(tableNode(table));
            addEdge(graphRoot(), tableNode(table), true);

            for (Column column : table.getColumns()) {
                graph.addVertex(columnNode(column));

                if (columnsToExclude.contains(column.getLabel())) {
                    continue;
                }

                addEdge(tableNode(table), columnNode(column), true);
            }
        }



        //int maxFdSize = Math.min(countMaxAFDsSourceTargetAboveThreshold(database.getScenario(), Double.parseDouble(this.gDepThreshold)), 5000);
        int maxFdSize = 0;
        if(this.gDepThreshold.startsWith("absolute_")){
            String numberPart = this.gDepThreshold.substring(9);
            maxFdSize = Integer.parseInt(numberPart);
        } else  if(this.gDepThreshold.startsWith("col_scale_")){
            String numberPart = this.gDepThreshold.substring(10);
            maxFdSize = (int) Math.min(database.getNumColumns() * Double.parseDouble(numberPart), 5000);
        }else if (this.gDepThreshold.startsWith("gdep_threshold_")){
            String numberPart = this.gDepThreshold.substring(15);
            maxFdSize =  Math.min(countMaxAFDsSourceTargetAboveThreshold(database.getScenario(), Double.parseDouble(numberPart)), 5000);
        }

        logger.info("Building graph for " + database.getName() + "using "  + maxFdSize + " FDs @ gDepThreshold " + this.gDepThreshold);

        List<FunctionalDependency> fds = database.getMetadata().getFds().stream()
                .sorted((FunctionalDependency l, FunctionalDependency r) -> Double.compare(r.getPdepTuple().gpdep, l.getPdepTuple().gpdep))
                .toList();

        Iterator<FunctionalDependency> iterator = fds.iterator();
        for (int i = 0; i < Math.min(maxFdSize, fds.size()) && iterator.hasNext(); i++) {
            this.addFd(iterator.next());
        }

        // We might have computed some new pdep scores and want to save those.
        if (Metanome.SAVE) {
            for (Table table : database.getTables()) {
                MetadataUtils.saveFDs(
                        MetadataUtils.getMetadataPathFromTable(Path.of(table.getPath())),
                        database.getMetadata().getTableFDs(table)
                );
            }
        }
    }

    public Path exportPath() {
        return Path.of("target/graphs")
                .resolve(database.getScenario().getDataset().getName())
                .resolve(database.getScenario().getName())
                .resolve(this.gDepThreshold + "_" + database.getName() + ".gml");
    }

    private void exportGraph() {
        GraphMLExporter<String, DefaultEdge> exporter = new GraphMLExporter<>(Function.identity());
        try {
            Files.createDirectories(exportPath().getParent());
            Writer sourceGraphWriter = new FileWriter(exportPath().toString());
            exporter.exportGraph(graph, sourceGraphWriter);
        } catch (IOException e) {
            logger.error("Could not open file " + graph);
            throw new RuntimeException(e);
        }
    }

    private Integer countMaxStrictFDsSourceTarget(Scenario scenario){
        Integer sourceFDs = 0;
        Integer targetFDs = 0;
        for(FunctionalDependency fd : scenario.getSourceDatabase().getMetadata().getFds()){
            if(fd.getPdepTuple().pdep == 1.0){
                sourceFDs++;
            }
        }
        for(FunctionalDependency fd : scenario.getTargetDatabase().getMetadata().getFds()){
            if(fd.getPdepTuple().pdep == 1.0){
                targetFDs++;
            }
        }
        return Math.max(sourceFDs, targetFDs);
    }

    private Integer countMaxAFDsSourceTargetAboveThreshold(Scenario scenario, double threshold){
        Integer sourceFDs = 0;
        Integer targetFDs = 0;
        for(FunctionalDependency fd : scenario.getSourceDatabase().getMetadata().getFds()){
            if(fd.getPdepTuple().gpdep >= threshold){
                sourceFDs++;
            }
        }
        for(FunctionalDependency fd : scenario.getTargetDatabase().getMetadata().getFds()){
            if(fd.getPdepTuple().gpdep >= threshold){
                targetFDs++;
            }
        }
        return Math.max(sourceFDs, targetFDs);
    }
    private void addUcc(UniqueColumnCombination ucc) {
        String thisUccNode = vertexName("UCC", String.valueOf(uccCounter));
        graph.addVertex(thisUccNode);
//        addEdge(thisUccNode, uccMetaNode());
        for (Column c : ucc.getColumnCombination()) {
            addEdge(columnNode(c), thisUccNode, true);
        }

        uccCounter++;
    }

    private void addFd(FunctionalDependency fd) {
        if (fd.getDeterminant().stream().anyMatch((c) -> columnsToExclude.contains(c.getLabel())) ||
                columnsToExclude.contains(fd.getDependant().getLabel())) {
            return;
        }

        String thisFdNode = vertexName("FD", String.valueOf(fdCounter));
        graph.addVertex(thisFdNode);
//        addEdge(thisFdNode, fdMetaNode());
        for (Column c : fd.getDeterminant()) {
            addEdge(columnNode(c), thisFdNode);
        }
        addEdge(thisFdNode, columnNode(fd.getDependant()));

        fdCounter++;
    }

    private void addEdge(final String sourceVertex, final String targetVertex) {
        addEdge(sourceVertex, targetVertex, false);
    }

    private void addEdge(final String sourceVertex, final String targetVertex, final Boolean bothDirections) {
        graph.addEdge(sourceVertex, targetVertex);
        if (bothDirections) {
            graph.addEdge(targetVertex, sourceVertex);
        }
//        String antiDirectionNode = vertexName("ANTI_DIRECTION", String.valueOf(antiDirectionalityNodeCounter));
//        graph.addVertex(antiDirectionNode);
//        graph.addEdge(targetVertex, antiDirectionNode);
//        graph.addEdge(antiDirectionNode, sourceVertex);
//
//        antiDirectionalityNodeCounter++;
    }

    private String graphRoot() {
        return vertexName("ROOT", "");
    }
    private String uccMetaNode() {
        return vertexName("UCC", "");
    }
    private String fdMetaNode() {
        return vertexName("FD", "");
    }
    private String tableNode(final Table table) {
        return vertexName("TABLE", table.getName());
    }
    private String columnNode(final Column column) {
        return vertexName("COLUMN", column.getTable().getName() + "|" + column.getLabel());
    }
    private String vertexName(final String resourceType, final String resourceName) {
        return String.format("DB|%d|%s|%s", graphId, resourceType, resourceName);
    }
}
