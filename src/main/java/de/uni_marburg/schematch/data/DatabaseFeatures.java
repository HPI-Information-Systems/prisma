package de.uni_marburg.schematch.data;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.uni_marburg.schematch.data.metadata.Datatype;
import de.uni_marburg.schematch.similarity.list.EuclideanDistance;
import de.uni_marburg.schematch.similarity.list.ProbabilityMassFunction;
import lombok.Data;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@Data
public class DatabaseFeatures {
    private final Logger logger = LogManager.getLogger(this.getClass());

    // private Map<Table, Map<Column, List<Double>>>
    private Map<String, Map<String, Map<String, List<Double>>>> features = new HashMap<>();

    private Database database;
    private double getAverageLength(final List<String> values){
        if(values == null || values.isEmpty()){
            return 0.0;
        }
        int num_values = values.size();
        long total_length = 0;
        for(String value: values){
            total_length += value.length();
        }
        return (double) total_length / num_values;
    }

    private List<Double> getDatatypeEncoded(Column column){
        List<Double> encoded = new ArrayList<>(Datatype.values().length);
        for(Datatype datatype: Datatype.values()){
            if(datatype == column.getDatatype()){
                encoded.add(1.0);
            } else {
                encoded.add(0.0);
            }
        }
        return encoded;
    }

    private double getEntropy(final List<String> values){
        int total = values.size();

        Map<String, Integer> frequencyCounter = new HashMap<>();
        for (String value : values) {
            frequencyCounter.put(value, frequencyCounter.getOrDefault(value, 0) + 1);
        }

        // Calculate entropy
        double entropy = 0.0;
        for (Map.Entry<String, Integer> entry : frequencyCounter.entrySet()) {
            double probability = (double) entry.getValue() / total;
            entropy -= probability * Math.log(probability) / Math.log(2);
        }

        return entropy;
    }

    public DatabaseFeatures(final Database database){
        this.database = database;
        List<Table> sourceTables = database.getScenario().getSourceDatabase().getTables();
        List<Table> targetTables = database.getScenario().getTargetDatabase().getTables();

        String[] kindsOfFeatures = new String[] {"Distribution", "Entropy"};
        List<Table>[] tablesArray = new List[]{sourceTables, targetTables};
        List<Double> entropies = new ArrayList<>();
        for(List<Table> tables: tablesArray){
            for(Table t : tables){
                for(Column c : t.getColumns()){
                    entropies.add(getEntropy(c.getValues()));
                }
            }
        }

        for(String kindOfFeature : kindsOfFeatures){
            Map<String, Map<String, List<Double>>> featuresMap = new HashMap<>();
            this.features.put(kindOfFeature, featuresMap);
        }

        for (Table table : database.getTables()){
            for(String kindOfFeature : kindsOfFeatures){
                Map<String, List<Double>> tableMap = new HashMap<>();
                this.features.get(kindOfFeature).put(table.getName(), tableMap);
            }
            for(Column column: table.getColumns()){
                List<Double> distributionFeatureVector = new ArrayList<>();
                List<Double> entropyFeatureVector = new ArrayList<>();

                ProbabilityMassFunction<String> similarityMeasure = new EuclideanDistance<>();
                int count = 0;
                for(List<Table> tables: tablesArray){
                    for(Table t : tables){
                        for(Column c : t.getColumns()){
                            distributionFeatureVector.add((double) similarityMeasure.compare(column.getValues(), c.getValues()));
                            entropyFeatureVector.add(Math.abs(getEntropy(column.getValues()) - entropies.get(count)));
                            count++;
                        }
                    }
                }
                features.get("Distribution").get(table.getName()).put(column.getLabel(), distributionFeatureVector);
                features.get("Entropy").get(table.getName()).put(column.getLabel(), entropyFeatureVector);
            }
        }
    }

    public void exportFeatures(final String path_to_directory){
        Path database_directory = Path.of(path_to_directory);
        try {
            Files.createDirectories(database_directory);
        } catch (IOException e) {
            logger.error("Could not create directory " + path_to_directory);
            throw new RuntimeException(e);
        }

        ObjectMapper objectMapper = new ObjectMapper();
        String json;
        try {
            json = objectMapper.writeValueAsString(features);
        } catch (JsonProcessingException e) {
            logger.error("Could not export features " + database.getName());
            throw new RuntimeException(e);
        }

        try {
            File file = new File(database_directory.resolve(this.database.getName() + ".json").toString());
            objectMapper.writeValue(file, json);
        } catch (IOException e) {
            logger.error("Could not export features " + database.getName());
            throw new RuntimeException(e);
        }
    }
}
