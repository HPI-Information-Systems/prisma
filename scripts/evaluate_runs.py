# call python evaluate_runs.py /path/to/schemata/results/result_xy
import os
import sys
import csv
import matplotlib.pyplot as plt
from matplotlib.colors import LinearSegmentedColormap
import matplotlib.patches as patches
import seaborn as sns
import numpy as np
import re

PERFORMANCES = {
    "Accuracy": "Accuracy",
    "F1": "F1",
    "Precision": "Precision",
    "Recall": "Recall",
    "NonBinaryPrecisionAtGroundTruth": "NonBinaryPrecisionAtGroundTruth"
}

sns.set_style("whitegrid")
sns.set(rc={"axes.grid": False})

COLORS = {"True": "blue", "False": "red"}


def extract_properties(matcher_string):
    matcher_name = matcher_string[:matcher_string.find("(")]
    pattern = r'(\w+)\s*=\s*([^\s:]+)'
    matches = re.findall(pattern, matcher_string)
    return matcher_name, dict(matches)


def create_custom_colormap():
    # Define custom colormap with red to green transition
    cmap_colors = [(0, 1, 0), (1, 0, 0)]  # Red to green
    return LinearSegmentedColormap.from_list("custom_cmap", cmap_colors, N=256)


def plot_heatmap(data, data2, dataset, scenario, matcher):
    custom_cmap = create_custom_colormap()
    plt.imshow(data, cmap=custom_cmap, interpolation='nearest', vmin=0, vmax=1)

    # Overlay circles on top of the heatmap
    for i in range(data.shape[0]):
        for j in range(data.shape[1]):
            # Check if the corresponding value in data2 is 1
            if data2[i, j] == 1:
                # Draw a circle with a bold black edge
                rect = patches.Rectangle((j - 0.5, i - 0.5), 1, 1, linewidth=2, edgecolor='black', facecolor='none')
                plt.gca().add_patch(rect)

    plt.xticks([])
    plt.yticks([])
    matcher_clean = matcher[:-4]
    if matcher_clean.startswith("Node2Vec"):
        pattern = r'(\w+)\s*=\s*([^\s:]+)'
        matches = re.findall(pattern, matcher_clean)
        properties = dict(matches)
        matcher_clean = f"EmbedAlign Struc: {properties['xNetMFGammaStruc']} Attr: {properties['xNetMFGammaAttr']} Top3: {properties['filterKNearest'][:-1]}"

    plt.title(f"{matcher_clean} \n{scenario} - {dataset}", fontsize=16)
    plt.ylabel("source columns")
    plt.xlabel("target columns")
    cbar = plt.colorbar()
    cbar.set_label('abs(Ground_truth - Actual)', labelpad=10, fontsize=14)

    plt.show()


def process_output_file(matcher_file_path, dataset, scenario, matcher):
    with open(matcher_file_path) as fd:
        csvreader = csv.reader(fd)

        data = []
        data2 = []
        for row in list(csvreader)[1:]:
            data.append([abs(float(cell.split(" / ")[0]) - float(cell.split(" / ")[1])) for cell in row[1:]])
            data2.append([int(cell.split(" / ")[0]) for cell in row[1:]])
    data = np.asarray(data)
    data2 = np.asarray(data2)
    plot_heatmap(data, data2, dataset, scenario, matcher)


def extract_score(csv_path, performance, benchmark_dict):
    with open(csv_path) as fd:
        csvreader = csv.reader(fd)
        row1, row2 = list(csvreader)[:2]
        for i, matcher in enumerate(row1):
            if i == 0: # skip row labels
                continue
            benchmark_dict.setdefault(matcher, {})[performance] = float(row2[i])
def import_benchmark(root_path):
    benchmarks = {}
    for dataset in os.listdir(root_path):
        dataset_path = os.path.join(root_path, dataset)
        if os.path.isdir(dataset_path) and dataset != "_performances" and dataset.startswith("ISIA"):
            dataset_benchmarks = {}
            # Iterate over scenarios
            for scenario in os.listdir(dataset_path):
                scenario_benchmarks = {}
                scenario_path = os.path.join(dataset_path, scenario)
                if not os.path.isdir(scenario_path) or scenario == "_performances":
                    continue
                # Now you can perform operations within each scenario
                print("Dataset:", dataset)
                print("Scenario:", scenario)
                # Add your processing logic here
                outputs_dir = os.path.join(scenario_path, "_outputs", "MatchingStepLine1")
                # for matcher in os.listdir(outputs_dir):
                #    process_output_file(os.path.join(outputs_dir, matcher), dataset, scenario, matcher)
                for performance_filename, performance_name in PERFORMANCES.items():
                    performance_csv = os.path.join(scenario_path, "_performances", performance_filename,
                                                   "performance_overview_line1.csv")
                    extract_score(performance_csv, performance_name, scenario_benchmarks)
                dataset_benchmarks[scenario] = scenario_benchmarks
            benchmarks[dataset] = dataset_benchmarks
    return benchmarks


def visualize_performance(benchmarks):
    plt.figure(figsize=(10, 8))

    datasets = sorted(list(benchmarks.keys()))
    num_datasets = len(datasets)
    num_columns = 1

    for idx, dataset in enumerate(datasets, start=1):
        plt.subplot(num_datasets // num_columns + 1, num_columns, idx)
        plt.title(f"{dataset} - All Scenario Performance Scores", fontsize=18)
        plt.ylabel("Performance Score", fontdict={"fontsize": 16})
        plt.xlabel("GammaStruc / GammaAttr", fontdict={"fontsize": 16})

        seen = set()
        for scenario, benchmark in benchmarks[dataset].items():
            for filterKNearest in ["True", "False"]:
                runs = sorted([x for x in benchmark if x["filterKNearest"] == filterKNearest], key=lambda x: x["ratio"])
                del runs[1]  # remove double ratio (1.0) # TODO: make this robust and smarter
                perfomances = [
                    x["perf"] for x in runs
                ]
                ratios = [r["ratio"] for r in runs]

                label = f"filter {filterKNearest}"
                if label in seen:
                    plt.plot(
                        ratios,
                        perfomances,
                        color=COLORS[filterKNearest],
                    )
                else:
                    plt.plot(
                        ratios,
                        perfomances,
                        color=COLORS[filterKNearest],
                        label=label
                    )
                    seen.add(label)

        plt.legend(loc='center left', bbox_to_anchor=(1, 0.5), fontsize=16)

    plt.tight_layout()  # Adjust layout to prevent overlap
    plt.show()


def generalize_benchmark_matchers(benchmarks):
    generalized_benchmark = {}
    for dataset, scenario_dict in benchmarks.items():
        new_scenario_dict = {}
        for scenario, matcher_dict in scenario_dict.items():
            new_matcher_dict = {}
            for matcher_string, performance in matcher_dict.items():
                matcher_name, props = extract_properties(matcher_string)
                new_matcher_dict.setdefault(matcher_name, []).append(dict(**{"props": props}, **performance))
            new_scenario_dict[scenario] = new_matcher_dict
        generalized_benchmark[dataset] = new_scenario_dict
    return generalized_benchmark


def plot_dataset_evaluation(scenario_dict):
    num_scenarios = len(scenario_dict)
    num_columns=2
    fig, axs = plt.subplots(
        num_scenarios // num_columns+1, num_columns, figsize=(12, 8), sharey=True
    )

    for idx, (scenario, ax) in enumerate(zip(scenario_dict.keys(), axs.flatten()), start=1):
        x_labels = []
        y = []
        for matcher, performances in scenario_dict[scenario].items():
            x_labels.append(matcher)
            y.append(max([performance["F1"] for performance in performances]))
        ax.bar(x_labels, y)
        ax.tick_params(axis='x', labelrotation=90)
    plt.tight_layout()
    plt.show()


def get_matchers(generalized_benchmark):
    for scenario_dict in generalized_benchmark.values():
        for runs in scenario_dict.values():
            return list(set(runs.keys()))


if __name__ == "__main__":
    root_path = sys.argv[1]
    benchmarks = import_benchmark(root_path)
    generalized_benchmarks = generalize_benchmark_matchers(benchmarks)
    matchers = get_matchers(generalized_benchmarks)

    for dataset, scenario_dict in generalized_benchmarks.items():
        plot_dataset_evaluation(scenario_dict, matchers)