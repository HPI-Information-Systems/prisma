import os
import csv
import numpy as np
from scipy.optimize import minimize
import matplotlib.pyplot as plt
import seaborn as sns
#sns.set()


def mle_line(params, x, y):
    slope, intercept = params
    y_pred = slope * x + intercept
    likelihood = np.sum((y - y_pred) ** 2)
    return likelihood


def read_csv(path):
    with open(path, "r") as fp:
        csvreader = csv.reader(fp)
        rows = list(csvreader)
    return rows



COLORS = {
    "Original": "turquoise",
    "Encrypted": "tomato",
}

MARKER = {
    "Precision": "o",
    "Recall": "s",
    "F1": "^"
}

LINESTYLE = {
    "Original": "dotted",
    "Encrypted": "dashdot",
}
def visualize_gamma_eval():
    ## generate xNetGammaAttributePlot
    data = read_csv("./data/gamma_eval.csv")
    gammas = [float(x) for x in data[0][1:]]
    total = 1
    #plt.gca().set_aspect(1.5, adjustable='box')
    plt.figure(figsize=(10,5.5))
    plt.rc('font', family='serif')


    for i, encoding in enumerate(["Original", "Encrypted"]):
        for j, metric in enumerate(["Precision", "Recall", "F1"]):
            metric_data = [float(x) for x in data[total][1:]]
            plt.plot(gammas, metric_data, marker=MARKER[metric], label=metric, color=COLORS[encoding], linestyle=LINESTYLE[encoding])
            if metric == "F1":
                initial_guess = [1, 0]
                result = minimize(mle_line, initial_guess, args=(np.asarray(gammas), np.asarray(metric_data)))
                slope, intercept = result.x
                y_values = slope * np.asarray(gammas) + intercept
                plt.plot(gammas, y_values, color=COLORS[encoding], linewidth=2, label='Fitted line')
            total += 1

    plt.ylim([0.4, 0.8])
    plt.yticks([0.4, 0.5, 0.6, 0.7, 0.8], fontsize=16)
    plt.xticks([x / 10.0 for x in  range(0,11,2)], fontsize=16)

    handles = []
    for marker, metric in MARKER.items():
        handles.append(plt.Line2D([], [], color='gray', marker=metric, linestyle='None', markersize=10, label=marker))

    for encoding, color in COLORS.items():
        handles.append(plt.Line2D([], [], color=color, linestyle=LINESTYLE[encoding], markersize=10, label=encoding))

    plt.legend(handles=handles, loc='upper center', bbox_to_anchor=[0.45, 1.2], ncol=5, fontsize=16, frameon=False)
    plt.xlabel("γ (Features vs Structure)", fontsize=19)
    plt.ylabel("Performance", fontsize=19)
    plt.tight_layout()
    plt.grid()
    plt.savefig("performance_gamma.pdf")
    plt.show()


RUNTIME_COLORS = {
    "PRISMA": "tomato",
    "LEAPME": "turquoise",
    "EmbDI": "#82B366",
    "CS": "gray",
    "I40": "#9673A6",
    "K&N" : "yellow",
    "JMM" : "cyan"
}

HATCHES = {
    "PRISMA": "/",
    "LEAPME": ".",
    "EmbDI": "\\",
    "CS": "x",
    "I40": "+",
    "Profiling": "o",
    "Pdep Calculation": "+",
    "K&N": "..",
    "JMM": "X"
}

def visualize_runtime():
    ## generate xNetGammaAttributePlot
    data = read_csv("./data/runtimes.csv")
    matchers = data[0][1:]
    num_matchers = len(matchers)
    bar_width = 0.35  # Width of each bar
    margin = 0.5
    index = np.arange(num_matchers)  # Index for the x-axis positions

    plt.figure(figsize=(10, 5.5))
    plt.rc('font', family='serif')

    all_positions = []

    for offset, runtimes_matcher in enumerate(data[1:]):
        name = runtimes_matcher[0]
        runtimes = [float(runtime) for runtime in runtimes_matcher[1:]]  # Convert runtimes to integers
        bar_positions = (index * bar_width) + offset * (num_matchers  * bar_width + margin)  # Adjust bar positions for grouping
        all_positions += list(bar_positions)
        for runtime, matcher, pos in zip(runtimes, matchers, bar_positions):
            if runtime == -1:
                plt.plot([pos], [0.1], marker='*', color='black', markersize=10, zorder=5)
            plt.bar([pos], [runtime], bar_width, color=RUNTIME_COLORS[matcher], edgecolor='black', label=("" if offset==0 else "_")+ matcher, hatch=HATCHES[matcher], zorder=3)
            #if matcher == "PRISMA":
            #    plt.bar([pos], [runtime * 0.98], bar_width, color=RUNTIME_COLORS[matcher], edgecolor='black', label=("" if offset==0 else "_")+ matcher + " Pdep Calculation", hatch=HATCHES["Pdep Calculation"], zorder=4)
            #    plt.bar([pos], [runtime * np.log(0.6)], bar_width, color=RUNTIME_COLORS[matcher], edgecolor='black', label=("" if offset==0 else "_")+ matcher + " Profiling", hatch=HATCHES["Profiling"], zorder=5)


    plt.ylabel('Runtimes (s)', labelpad=-10, fontsize=19)
    plt.ylim([0.1, 2000])

    plt.yscale('log')


    handles = []
    #for matcher, color in RUNTIME_COLORS.items():
    #    handles.append(plt.Line2D([], [], color=color, marker="s", linestyle='None', markersize=10, label=matcher))
    #plt.legend(handles=handles, loc='upper center', bbox_to_anchor=[0.45, 1.2], ncol=5, fontsize=16)
    plt.legend(loc='upper center', bbox_to_anchor=[0.45, 1.2], ncol=6, fontsize=16, frameon=False)

    plt.tight_layout()
    plt.yticks([1, 10, 100, 1000], ["1", "10", "100", "1000"], fontsize=16)
    plt.xticks([0.7, 2.95], ["Single Table", "Multiple Tables"], fontsize=19)
    plt.grid(zorder=0)
    plt.savefig("runtime_comparison.pdf")

    plt.show()


def visualize_runtime_two_subplots():
    plt.rc('font', family='serif')

    # Read the CSV file manually
    with open("./data/runtimes.csv", newline='') as csvfile:
        data = list(csv.reader(csvfile))

    matchers = data[0][1:]
    num_matchers = len(matchers)
    bar_width = 0.2  # Width of each bar
    margin = 0.5
    index = np.arange(num_matchers)  # Index for the x-axis positions

    # Create subplots
    fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(11, 6))
    plt.rc('font', family='serif')

    # Iterate through each row in data
    for ax, runtimes_matcher in zip([ax1, ax2], data[1:]):
        runtimes = [float(runtime) for runtime in runtimes_matcher[1:]]  # Convert runtimes to floats
        bar_positions = (index * bar_width)   # Adjust bar positions for grouping

        for runtime, matcher, pos in zip(runtimes, matchers, bar_positions):

            ax.bar(pos, runtime, bar_width,
                    color=RUNTIME_COLORS[matcher],
                    edgecolor='black', hatch=HATCHES[matcher],
                    zorder=3, label=("" if ax==ax1 else "_")+matcher)


    ax1.text(0.5, -0.08, "Single Table", fontsize=20, ha='center', transform=ax1.transAxes)
    ax2.text(0.5, -0.08, "Multiple Tables", fontsize=20, ha='center', transform=ax2.transAxes)


    # Customize the first subplot
    ax1.set_ylabel('Runtimes (s)', labelpad=-5, fontsize=20)
    ax1.set_ylim([0.1, 2000])
    ax1.set_xlim([-0.3, 1.5])
    ax1.set_yscale('log')
    ax1.set_yticks([1, 10, 100, 1000], ["1", "10", "100", "1000"], fontsize=16)

    ax1.set_xticks([])
    ax1.grid(zorder=0)

    # Customize the second subplot
    ax2.set_ylim([0.1, 2000])
    ax2.set_xlim([-0.3, 1.5])
    ax2.set_yscale('log')
    ax2.set_xticks([])
    ax2.set_yticks([1, 10, 100, 1000], ["1", "10", "100", "1000"], fontsize=16)

    ax2.grid(zorder=0)
    fig.legend( loc='upper center', bbox_to_anchor=[0.5, 1.0], ncol=7, fontsize=17, frameon=False, columnspacing=0.7)

    plt.savefig("runtime_comparison.pdf")

    plt.show()


MARKER = [("|",15),("s",10),("^",10),("*",10),("x",10)]
COLORS = ["tomato","turquoise","#82B366","gray","#9673A6","yellow"]

def visualize_gdep_gamma(csv_name):
    data = read_csv(f"./data/{csv_name}")
    gammas = data[0]
    plt.figure(figsize=(11.5, 5.5))
    plt.rc('font', family='serif')

    all_positions = []

    for offset, row in enumerate(data[2:]):
        gdep_treshold = row[0]
        f1_scores = row[1:-1]
        f1_scores= [float(f1_score) for f1_score in f1_scores]  # Convert runtimes to integers
        plt.plot(gammas[1:-1], f1_scores,marker=MARKER[offset][0], label=gdep_treshold, markersize=MARKER[offset][1], zorder=5)
        #    plt.bar([pos], [runtime], bar_width, color=RUNTIME_COLORS[matcher], edgecolor='black', label=("" if offset==0 else "_")+ matcher, hatch=HATCHES[matcher], zorder=3)
            #if matcher == "PRISMA":
            #    plt.bar([pos], [runtime * 0.98], bar_width, color=RUNTIME_COLORS[matcher], edgecolor='black', label=("" if offset==0 else "_")+ matcher + " Pdep Calculation", hatch=HATCHES["Pdep Calculation"], zorder=4)
            #    plt.bar([pos], [runtime * np.log(0.6)], bar_width, color=RUNTIME_COLORS[matcher], edgecolor='black', label=("" if offset==0 else "_")+ matcher + " Profiling", hatch=HATCHES["Profiling"], zorder=5)


    plt.ylabel('F1 Score', labelpad=0, fontsize=23)
    plt.xlabel('γ Parameter', labelpad=0, fontsize=23)
    handles = []
    #for matcher, color in RUNTIME_COLORS.items():
    #    handles.append(plt.Line2D([], [], color=color, marker="s", linestyle='None', markersize=10, label=matcher))
    #plt.legend(handles=handles, loc='upper center', bbox_to_anchor=[0.45, 1.2], ncol=5, fontsize=16)
    plt.legend(loc='lower center', bbox_to_anchor=[0.45, 0.97], ncol=6, fontsize=18, frameon=False)
    #plt.tight_layout()
    plt.text(0.5, 1.11, 'Gpdep Threshold τ', fontsize=18, ha='center', transform=plt.gca().transAxes)
    #plt.title("Gpdep Threshold τ")
    plt.yticks([0.44,0.46,0.48,0.50,.52], [0.44,0.46,0.48,0.50,.52], fontsize=16)
    plt.xticks(gammas[1:-1:2], [f"0.{i}" for i in range(0, 10)], fontsize=19)
    plt.grid(zorder=0)
    plt.subplots_adjust(left=0.11, bottom=0.13)
    plt.savefig("Gamma-Gpdep.pdf")

    plt.show()


def main():
    visualize_runtime_two_subplots()
    #isualize_gdep_gamma("gdep_gamma_eval.csv")
    #visualize_gdep_gamma("gdep_gamma_eval_sakila.csv")
    #visualize_gdep_gamma("gdep_gamma_eval_valentine.csv")
    visualize_runtime()
    visualize_gamma_eval()


if __name__ == "__main__":
    main()