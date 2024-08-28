import networkx as nx
import numpy as np

import random
import json

NODE_TYPES = ["ROOT", "TABLE", "COLUMN", "UCC", "FD"]
IC_TYPES = ["UCC", "FD"]


def extract_node_type(node_label):
    return node_label.split("|")[2]


def extract_column(node_label):
    return node_label.split("|")[4]


def extract_table(node_label):
    return node_label.split("|")[3]


def load_feature_dict(path, kind_of_feature):
    with open(path, "r") as fp:
        dict = json.loads(json.load(fp))
    dict = dict[kind_of_feature]
    dict = {table: {column: np.asarray(features) for column, features in columns.items()} for table, columns in dict.items()}
    return dict


class EmbedGraph:
    def __init__(self, graph: nx.Graph, feature_json_path: str, kind_of_feature : str):
        self.graph = graph
        self.original_mappings = self.get_mappings()
        self.mappings = self.get_mappings()
        self.feature_json_path = feature_json_path
        self.feature_dict = load_feature_dict(feature_json_path, kind_of_feature)
        self.kind_of_feature = kind_of_feature
        self.default_feature_vector_length_dict = (
            self.get_default_feature_vector_length_dict()
        )
        self.removed_nodes = set()

    def get_mappings(self):
        mappings = {k: [] for k in NODE_TYPES}
        for node in self.graph.nodes:
            mappings[extract_node_type(node)].append(node)
        return mappings

    def remove_random_columns(self, percentage):
        to_be_removed = random.sample(
            self.mappings["COLUMN"], int(len(self.mappings["COLUMN"]) * percentage)
        )
        to_be_removed_set = set(to_be_removed)
        for node in to_be_removed:
            self.removed_nodes.add(node)
            neighbors = list(self.graph.neighbors(node))
            for neighbor in neighbors:
                if (
                    extract_node_type(neighbor) in IC_TYPES
                    and neighbor in self.graph.nodes
                ):
                    self.removed_nodes.add(neighbor)
                    self.graph.remove_node(neighbor)
            self.graph.remove_node(node)

        self.mappings["COLUMN"] = [
            node for node in self.mappings["COLUMN"] if node not in to_be_removed_set
        ]

    def remove_random_ics(self, percentage):
        ics = [self.mappings[ic_type] for ic_type in IC_TYPES]
        ics = [item for sublist in ics for item in sublist]
        to_be_removed = random.sample(ics, int(len(ics) * percentage))
        print("To be removed ICS: ")
        print(to_be_removed)
        to_be_removed_set = set(to_be_removed)
        print(to_be_removed_set)
        for node in to_be_removed:
            self.graph.remove_node(node)

        for ic_type in IC_TYPES:
            self.mappings[ic_type] = [
                node for node in self.mappings[ic_type] if node not in to_be_removed_set
            ]

    def get_default_feature_vector_length_dict(self):
        for values in self.feature_dict.values():
            for value in values.values():
                return len(value)

    def get_features_shape(self):
        for table in self.feature_dict.values():
            for features in table.values():
                return features.shape

    def get_features(self):
        #lookup = {}
        #empty_dict_feature_list = np.asarray([
        #    0.0 for _ in range(self.default_feature_vector_length_dict)
        #])
        #for i, k in enumerate(NODE_TYPES):
        #    feature = [0.0 for _ in range(len(NODE_TYPES))]
        #    feature[i] = 1.0
        #    lookup[k] = np.asarray(feature)
        encodings = []
        for node in self.graph.nodes:
            #type_one_hot_encodings = lookup[extract_node_type(node)]
#
            if extract_node_type(node) == "COLUMN":
                features = self.feature_dict[extract_table(node)][extract_column(node)]
                encodings.append(np.asarray(features))

            #else:
            #    features = empty_dict_feature_list

        return np.asarray(encodings)

    def normalize_features(self, other_graph):
        assert self.get_features_shape() == other_graph.get_features_shape()
        min_feature_values = np.full_like(self.get_features_shape(), np.inf, dtype=float)
        max_feature_values = np.full_like(self.get_features_shape(), np.NINF, dtype=float)
        for tables in list(self.feature_dict.values()) + list(other_graph.feature_dict.values()):
            for feature in tables.values():
                min_feature_values = np.minimum(min_feature_values, feature)
                max_feature_values = np.maximum(max_feature_values, feature)

        scale_features = max_feature_values - min_feature_values
        scale_features[scale_features == 0.0] = 1.0
        scale_features = 1.0 / scale_features

        self.feature_dict = {
            table: {
                column: (features - min_feature_values) * scale_features
                for column, features in columns.items()
            }
            for table, columns in self.feature_dict.items()
        }

        other_graph.feature_dict = {
            table: {
                column: (features - min_feature_values) * scale_features
                for column, features in columns.items()
            }
            for table, columns in other_graph.feature_dict.items()
        }