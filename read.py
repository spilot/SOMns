import re, itertools
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
#        shorter = ctx
#        while True:
#            shorter = Context(shorter.trace[2:], ctx.java_type)
#            if len(shorter.trace) <= 1:
#                break
#            traces[shorter] = int(match['activations'])

def find_superinstruction(traces, candidate):
    child_class = candidate.trace[-1]
    child_index = candidate.trace[-2]
    if candidate.trace[-3] == 'SequenceNode':
        return {}
    alternatives = defaultdict(dict)
    for other in traces:
        if (other.trace[:-2] == candidate.trace[:-2]
            and other.trace[-2] != child_index):
            alternatives[other.trace[-2]][other] = traces[other]
    # construct alternatives along with their activation counts
    alternatives[child_index][candidate] = traces[candidate]
    constructions = []
    activations = 0
    for idx in sorted(alternatives.keys()):
        construction = list(alternatives[idx].items())
        constructions.append(construction)
    pool = {}
    for combination in itertools.product(*constructions):
        superinstruction = [x[0] for x in combination]
        activations = [x[1] for x in combination]
        pool[tuple(superinstruction)] = sum(activations)
    return pool

sorted_traces = sorted(traces.keys(), key=traces.get, reverse=True)
pool = {}
for t in sorted_traces[:20]:
    pool.update(find_superinstruction(traces, t))

top10 = sorted(pool.keys(), key=pool.get, reverse=True)
for t in top10[:20]:
    pprint(t)
    print(pool[t])
