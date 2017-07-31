import itertools
from collections import defaultdict

import networkx as nx

NUMERIC_ATTRIBUTES = ('activations', 'childindex')

def _preprocess_graph_attributes(G):
    for edge in G.edges():
        for attributes in G.get_edge_data(*edge).values():
            for key, value in attributes.items():
                if key in NUMERIC_ATTRIBUTES:
                    attributes[key] = int(value)
    return G

def load_graph(filename):
    G = nx.drawing.nx_agraph.read_dot(filename)
    _preprocess_graph_attributes(G)
    return G

def erase_javatypes(G):
    for _, _, data in G.edges(data=True):
        if 'javatype' in data:
            data['javatype'] = '%'

def remove_bimorphic(G):
    edges_to_remove = []
    for node in G.nodes():
        edges_by_child_index = defaultdict(set)
        for _, child, key, data in G.out_edges(node, keys=True, data=True):
            edges_by_child_index[data['childindex']].add((node, child, key))
        for child_index, edges in edges_by_child_index.items():
            if len(edges) <= 2:
                edges_to_remove.extend(edges)
    G.remove_edges_from(edges_to_remove)
    # remove all nodes without edges
    nodes_to_remove = []
    for node in G.nodes():
        if G.degree(node) == 0:
            nodes_to_remove.append(node)
    G.remove_nodes_from(nodes_to_remove)

def get_child_indices(G, node):
    return {data['childindex'] for _, _, data in G.out_edges(node, data=True)}

def get_edges_with_child_index(G, node, child_index):
    return [edge for edge in G.out_edges(node, data=True)
            if edge[2]['childindex'] == child_index]

G = load_graph('graph.dot')
remove_bimorphic(G)
#erase_javatypes(G)

def get_activations(parent, index, child_class, javatype):
    out_edges = G.out_edges(parent, data=True)
    activations = tuple(data['activations'] for _, child, data in out_edges
            if data['childindex'] == index
                and child == child_class
                and data['javatype'] == javatype)
    assert len(activations) == 1 # TODO: breaks if java types are erased
    return activations[0]

def get_total_activations(parent, index):
    return sum(get_activations(parent, index, child_class, javatype)
            for child_class, javatype in relations[parent, index])

def abbreviate(name):
    return name.rsplit('.', 1)[-1]

def find_superinstruction_candidates(G, parent):
    child_indices = get_child_indices(G, parent)
    child_types = defaultdict(set)
    for child_index in child_indices:
        child_types[child_index] = get_edges_with_child_index(G, parent, child_index)
    # only take the 10 most activated ones
    for child_index, types in child_types.items():
        child_types[child_index] = list(sorted(types, key=lambda x: x[2]['activations'], reverse=True))[:10]
    keys = list(child_types.keys())
    combinations = itertools.product(*map(child_types.get, keys))
    combination_activations = []
    for combination in combinations:
        activations = [data['activations'] for _, _, data in combination]
        combination_activations.append((combination, sum(activations)))
    nice_ones = sorted(combination_activations, key=lambda x: x[1], reverse=True)[:20]
    for combination, activations in nice_ones:
        formatted = ' '.join('{}/{}'.format(abbreviate(e[1]), abbreviate(e[2]['javatype'])) for e in combination)
        print(formatted, activations)

find_superinstruction_candidates(G, "som.interpreter.nodes.MessageSendNode$GenericMessageSendNode")
