import os
import csv
import re


def extract_properties(matcher_string):
    matcher_name = matcher_string[:matcher_string.find("(")]
    pattern = r'(\w+)\s*=\s*([^\s:]+)'
    matches = re.findall(pattern, matcher_string)
    matches = [(match[0].strip(";)"), match[1].strip(";)")) for match in matches]
    return matcher_name, dict(matches)


def preprocess(csv_row):
    # Replace commas within parentheses with semicolons
    modified_row = re.sub(
        r"\(([^()]*)\)", lambda x: x.group(0).replace(",", ";"), csv_row
    )
    return modified_row


def extract_score(csv_path, performance, benchmark_dict):
    with open(csv_path) as fd:
        first_two_lines = fd.readlines()[:2]
        first_two_lines = [preprocess(row) for row in first_two_lines]
        csvreader = csv.reader(first_two_lines)
        row1, row2 = list(csvreader)
        for i, matcher in enumerate(row1):
            if i == 0:  # Skip row labels
                continue
            benchmark_dict.setdefault("_performances", {}).setdefault(matcher, {})[
                performance
            ] = float(row2[i])

def import_benchmark(root_path, performance_definitions):
    benchmarks = {}
    # for performance_filename, performance_name in performance_definitions.items():
    #    performance_csv = os.path.join(root_path, "_performances", performance_filename,
    #                                   "performance_overview_line1.csv")
    #    extract_score(performance_csv, performance_name, benchmarks)
    for dataset in os.listdir(root_path):
        dataset_path = os.path.join(root_path, dataset)
        if os.path.isdir(dataset_path) and dataset != "_performances":
            dataset_benchmarks = {}
            for performance_filename, performance_name in performance_definitions.items():
                performance_csv = os.path.join(dataset_path, "_performances", performance_filename,
                                               "performance_overview_line1.csv")
                extract_score(performance_csv, performance_name, dataset_benchmarks)
            # Iterate over scenarios
            for scenario in os.listdir(dataset_path):
                scenario_benchmarks = {}
                scenario_path = os.path.join(dataset_path, scenario)
                if not os.path.isdir(scenario_path) or scenario == "_performances":
                    continue
                # outputs_dir = os.path.join(scenario_path, "_outputs", "MatchingStepLine1")
                # for matcher in os.listdir(outputs_dir):
                #    process_output_file(os.path.join(outputs_dir, matcher), dataset, scenario, matcher)
                for performance_filename, performance_name in performance_definitions.items():
                    performance_csv = os.path.join(scenario_path, "_performances", performance_filename,
                                                   "performance_overview_line1.csv")
                    extract_score(performance_csv, performance_name, scenario_benchmarks)
                dataset_benchmarks[scenario] = scenario_benchmarks
            benchmarks[dataset] = dataset_benchmarks
    return benchmarks


def generalize_benchmark_matchers(benchmarks):
    generalized_benchmark = {}
    for level, level_dict in benchmarks.items():
        new_level_dict = {}
        if level == "_performances":
            for matcher_string, performance in level_dict.items():
                matcher_name, props = extract_properties(matcher_string)
                new_level_dict.setdefault(matcher_name, []).append(dict(**{"props": props}, **performance))
        else:
            new_level_dict = generalize_benchmark_matchers(level_dict)
        generalized_benchmark[level] = new_level_dict
    return generalized_benchmark

def get_matchers(generalized_benchmark):
    return generalized_benchmark["_performances"].keys()
