from output_helper import *


PERFORMANCES = {
    "Accuracy": "Accuracy",
    "F1": "F1",
    "Precision": "Precision",
    "Recall": "Recall",
    "MatcherRuntime": "MatcherRuntime",
}

METRICS = ["F1", "Recall", "Precision", "MatcherRuntime"]
FILTER_CONFIGS = [{"postprocessing" : "true", "kindOfFeature": "Distribution", "thresholdMatches": "true"}]
DATASETS = [
    "ORIG_MTSN_SHUFFLED",
    "ORIG_MTDN_SHUFFLED",
    "ORIG_MTSN_SAMPLED",
    "ORIG_MTDN_SAMPLED",
    "Magellan-Unionable",
    "ChEMBL-Unionable",
    "Valentine-Wikidata-Unionable",
    "TPC-DI-Unionable",
]

ENC_DATASETS = [
    "ENC_MTSN_SHUFFLED",
    "ENC_MTDN_SHUFFLED",
    "ENC_MTSN_SAMPLED",
    "ENC_MTDN_SAMPLED",
    "Magellan-Unionable_md5_encrypted",
    "ChEMBL-Unionable_md5_encrypted",
    "Valentine-Wikidata-Unionable_md5_encrypted",
    "TPC-DI-Unionable_md5_encrypted",
]

MATCHER_LABELS = [
    "EmbeddedMappingMatcher",
    "KangEtNaughton",
    "OverlapInstanceMatcher",
    "CosineMatcher",
    "EmbdiMatcher",
    "LeapmeMatcher",
]
benchmark = import_benchmark("../prisma/results/shuffled_columns", PERFORMANCES)

gen_benchmark = generalize_benchmark_matchers(benchmark)


for metric in METRICS:
    print(metric)
    print(DATASETS)
    for dataset_name in DATASETS:
        dataset_perf = gen_benchmark[dataset_name]
        dataset_perf = dataset_perf["_performances"]
        labels = MATCHER_LABELS
        perfs = [dataset_perf[label][0][metric] for label in labels]

        filtered_prisma_perfs = [
            perf
            for perf in dataset_perf["PRISMAMatcher"]
            if perf["props"]["postprocessing"] == "true"
            and perf["props"]["kindOfFeature"] == "Distribution"
            and perf["props"]["thresholdMatches"] == "true"
        ]
        filtered_prisma_perfs = sorted(
            filtered_prisma_perfs,
            key=lambda x: (x["props"]["gDepFiltering"], x["props"]["GammaStrucAttr"]),
        )
        for prisma_perf in filtered_prisma_perfs:
            labels.append(prisma_perf["props"])
            perfs.append(prisma_perf[metric])

        print(",".join([str(perf) for perf in perfs]))
