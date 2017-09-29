import re
from pprint import pprint
from collections import namedtuple, defaultdict

traces = {}

CONTEXT_LEVEL = 2
PATTERN = re.compile(r'(?P<trace>[^\[]+)\[(?P<type>[^\]]+)\] -> (?P<activations>\d+)')

Context = namedtuple('Context', 'trace java_type')

def abbreviate(cls):
    return cls.split('.')[-1].split('$')[-1]

def parse_trace(trace):
    splitted = trace.split(',')
    assert len(splitted) <= 2 * CONTEXT_LEVEL + 1
    for idx, item in enumerate(splitted):
        if idx % 2 == 1:
            splitted[idx] = int(item)
        else:
            splitted[idx] = abbreviate(item)
    return tuple(splitted)

with open('result.txt', 'r') as f:
    for line in f:
        match = PATTERN.match(line.strip()).groupdict()
        ctx = Context(parse_trace(match['trace']), match['type'])
        traces[ctx] = int(match['activations'])

def find_superinstruction(traces, candidate):
    child_class = candidate.trace[-1]
    child_index = candidate.trace[-2]
    alternatives = defaultdict(dict)
    for other in traces:
        if (other.trace[:-2] == candidate.trace[:-2]
            and other.trace[-2] != child_index):
            alternatives[other.trace[-2]][other] = traces[other]
    # construct alternatives along with their activation counts
    alternatives[child_index][candidate] = traces[candidate]
    superinstruction = []
    activations = 0
    for idx in sorted(alternatives.keys()):
        choice = max(alternatives[idx], key=alternatives[idx].get)
        superinstruction.append(choice)
        activations += alternatives[idx][choice]
    return superinstruction, activations

sorted_traces = sorted(traces.keys(), key=traces.get, reverse=True)
for t in sorted_traces[:20]:
    print(t, traces[t])
    pprint(find_superinstruction(traces, t))
    print()
