import networkx as nx

NUMERIC_ATTRIBUTES = ('activations', 'childindex')

G = nx.drawing.nx_agraph.read_dot('graph.dot')

for edge in G.edges():
    for attributes in G.get_edge_data(*edge).values():
        for key, value in attributes.items():
            if key in NUMERIC_ATTRIBUTES:
                attributes[key] = int(value)

def unfold_edge_data(edges):
    for edge in edges:
        data = G.get_edge_data(*edge)
        for attributes in data.values():
            yield attributes

relations = {}
# (parent, index): variations

for parent in G.nodes():
    out_edges = G.out_edges(parent, data=True)
    child_indices = {data['childindex'] for _, _, data in out_edges}
    for child_index in child_indices:
        matching_edges = (edge for edge in out_edges
                if edge[2]['childindex'] == child_index)
        child_classes = set(child for _, child, _ in matching_edges)
        relations[parent, child_index] = child_classes

def get_activations(parent, index, child_class):
    out_edges = G.out_edges(parent, data=True)
    activations = tuple(data['activations'] for _, child, data in out_edges
            if data['childindex'] == index and child == child_class)
    return sum(activations) # TODO

def get_total_activations(parent, index):
    return sum(get_activations(parent, index, child_class) for child_class in relations[parent, index])

def abbreviate(name):
    return name.rsplit('_', 1)[1]

sorted_relations = sorted(relations.keys(), key=lambda t: get_total_activations(*t), reverse=True)
for parent, index in sorted_relations:
    print('{} child #{} witnesses {} different classes ({} total activations)'.format(
        abbreviate(parent),
        index,
        len(relations[parent, index]),
        get_total_activations(parent, index)))
    for child_class in relations[parent, index]:
        print('\t->{} ({} activations)'.format(
            abbreviate(child_class),
            get_activations(parent, index, child_class)))
    print()
