from output_helper import *


PERFORMANCES = {
    "Accuracy": "Accuracy",
    "F1": "F1",
    "Precision": "Precision",
    "Recall": "Recall"
}

FILTER_CONFIGS = [{"postprocessing" : "true", "kindOfFeature": "Distribution", "thresholdMatches": "true"}]

benchmark = import_benchmark("../prisma/results/result-folder", PERFORMANCES)

gen_benchmark = generalize_benchmark_matchers(benchmark)


for dataset_name, dataset_perf in gen_benchmark.items():
    dataset_perf = dataset_perf["_performances"]
    labels = ["EmbeddedMappingMatcher", "KangEtNaughton"]
    f1s = [dataset_perf["EmbeddedMappingMatcher"][0]["F1"], dataset_perf["KangEtNaughton"][0]["F1"]]

    filtered_prisma_perfs = [perf for perf in dataset_perf["PRISMAMatcher"] if perf["props"]["postprocessing"] == "true" and perf["props"]["kindOfFeature"] == "Distribution" and perf["props"]["thresholdMatches"] == "true"]
    filtered_prisma_perfs = sorted(
        filtered_prisma_perfs,
        key=lambda x: (x["props"]["gDepFiltering"],
                       x["props"]["GammaStrucAttr"])
    )
    for prisma_perf in filtered_prisma_perfs:
        labels.append(prisma_perf["props"])
        f1s.append(prisma_perf["Precision"])

    # print(labels)
    # print(dataset_name)
    print(f1s)
# gen_benchmark = gen_benchmark["_performances"]["EmbedAlignMatcher"]

# def calculate_f1_score(precision, recall):
#    if precision == 0 or recall == 0:
#        return 0  # Avoid division by zero
#    else:
#        return 2 * (precision * recall) / (precision + recall)
#
#
# for config in CONFIGS:
#    perf = [bench for bench in gen_benchmark if int(bench["props"]["topKRow"]) == config[0] and int(bench["props"]["topKCol"]) == config[1]][0]
#    #print(perf)
#    print(config, perf["Precision"], perf["Recall"], calculate_f1_score(perf["Precision"], perf["Recall"]))
