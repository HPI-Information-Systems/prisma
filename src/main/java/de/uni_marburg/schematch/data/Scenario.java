package de.uni_marburg.schematch.data;

import de.uni_marburg.schematch.data.metadata.ScenarioMetadata;
import de.uni_marburg.schematch.matching.MatcherFactory;
import de.uni_marburg.schematch.utils.Configuration;
import de.uni_marburg.schematch.utils.InputReader;
import de.uni_marburg.schematch.utils.StringUtils;
import lombok.Data;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.Arrays;
import java.util.List;

@Data
public class Scenario {
    private final Dataset dataset;
    private final String path;
    private final String name;
    private boolean isDenormalized;
    private Database sourceDatabase;
    private Database targetDatabase;
    private ScenarioMetadata metadata;
    private static final Logger log = LogManager.getLogger(MatcherFactory.class);

    public Scenario(Dataset dataset, String path) {
        this.dataset = dataset;
        this.path = path;
        this.name = StringUtils.getFolderName(path);
        this.sourceDatabase = new Database(this, this.path + File.separator + Configuration.getInstance().getDefaultSourceDatabaseDir());
        this.targetDatabase = new Database(this, this.path + File.separator + Configuration.getInstance().getDefaultTargetDatabaseDir());
        List<String> gdepTresholds = Arrays.asList("col_scale_0.2", "col_scale_0.4", "col_scale_0.6","col_scale_0.8", "gdep_threshold_0.1", "gdep_threshold_0.2", "gdep_threshold_0.3", "gdep_threshold_0.4", "gdep_threshold_0.5");
        for (String gdepTreshold : gdepTresholds) {
            this.sourceDatabase.getGraphs().add(new MetaNodesDatabaseGraph(this.sourceDatabase, gdepTreshold));
            this.targetDatabase.getGraphs().add(new MetaNodesDatabaseGraph(this.targetDatabase, gdepTreshold));
        }
        this.sourceDatabase.setDatabaseFeatures(new DatabaseFeatures(this.sourceDatabase));
        this.sourceDatabase.getDatabaseFeatures().exportFeatures("target/features/" + this.dataset.getName() + "/" + this.name);
        this.targetDatabase.setDatabaseFeatures(new DatabaseFeatures(this.targetDatabase));
        this.targetDatabase.getDatabaseFeatures().exportFeatures("target/features/" + this.dataset.getName() + "/" + this.name);


        String actual_ground_truth_path = this.path + File.separator + Configuration.getInstance().getDefaultGroundTruthDir() + File.separator + "actual_ground_truth.txt";
        this.isDenormalized = new File(actual_ground_truth_path).exists();
        log.info("Scenario " + this.name + " is handled as " + (this.isDenormalized ? "" : "not-") + "denormalized.");

        // TODO: read dependencies on demand
        if (Configuration.getInstance().isReadDependencies()) {
            this.metadata = InputReader.readScenarioMetadata(this.path, sourceDatabase, targetDatabase);
        }
    }
}