import re

traces = {}

PATTERN = re.compile(r'(?P<trace>[^\[]+)\[(?P<type>[^\]]+)\] -> (?P<activations>\d+)')

with open('result.txt', 'r') as f:
    for line in f:
        match = PATTERN.match(line.strip()).groupdict()
        traces[(match['trace'], match['type'])] = int(match['activations'])

sorted_traces = sorted(traces.keys(), key=traces.get, reverse=True)
for t in sorted_traces[:10]:
    print(t, traces[t])
