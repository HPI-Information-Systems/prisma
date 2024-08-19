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
    private Map<String, Map<String, List<Double>>> features = new HashMap<>();

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

        for (Table table : database.getTables()){
            for(Column column: table.getColumns()){
                List<Double> featureVector = new ArrayList<>();

                ProbabilityMassFunction<String> similarityMeasure = new EuclideanDistance<>();
                List<Table>[] tablesArray = new List[]{sourceTables, targetTables};
                for(List<Table> tables: tablesArray){
                    for(Table t : tables){
                        for(Column c : t.getColumns()){
                            featureVector.add((double) similarityMeasure.compare(column.getValues(), c.getValues()));
                        }
                    }
                }
                if(features.containsKey(table.getName())){
                    Map<String, List<Double>> column_map = features.get(table.getName());
                    column_map.put(column.getLabel(), featureVector);
                } else {
                    Map<String, List<Double>> columnMap = new HashMap<>();
                    columnMap.put(column.getLabel(), featureVector);
                    features.put(table.getName(), columnMap);
                }
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
