#! /usr/bin/python3

# the math in this script is taken from
# "Statistically Rigorous Java Performance Evaluation" by A.Georges, D. Buytaert and L. Eeckhout

import fileinput
from collections import defaultdict
from scipy.stats import norm as normal_distribution
from scipy.stats import t as students_t_distribution
from math import sqrt
from numpy import rint as round_to_int
import numpy as np
from itertools import combinations
from tabulate import tabulate
import re
import sys

confidence_level = 0.95
one_minus_half_alpha = 1-(confidence_level/2)
z = normal_distribution.ppf(one_minus_half_alpha)
t = lambda degrees_of_freedom: students_t_distribution.ppf(one_minus_half_alpha, degrees_of_freedom)
mean = lambda data: sum(data)/len(data)
standard_deviation = lambda data: (sqrt(sum([(x - mean(data))**2 for x in data]) / (len(data) -1))) if len(data) > 1 else 0

zero_in = lambda pair: (pair[0] < 0 and pair[1] > 0) or (pair[0] > 1 and pair[1] < 0)

def compute_confidence_interval(data):
    sample_mean = mean(data)
    table_value = 0
    if len(data) < 30:
        # use Student's t distribution
        lower_confidence = sample_mean - t(len(data)-1) * (standard_deviation(data) / sqrt(len(data)))
        upper_confidence = sample_mean + t(len(data)-1) * (standard_deviation(data) / sqrt(len(data)))
        return (np.round(lower_confidence, 3), np.round(sample_mean, 3), np.round(upper_confidence, 3))
    else:
        # use normal distribution
        lower_confidence = sample_mean - z * (standard_deviation(data) / sqrt(len(data)))
        upper_confidence = sample_mean + z * (standard_deviation(data) / sqrt(len(data)))
        return (lower_confidence, sample_mean, upper_confidence)


def compare_two_alternatives(vm1, vm2):
    def degrees_of_freedom(vm1, vm2):
        n1 = mean(vm1)
        n2 = mean(vm2)
        s1 = standard_deviation(vm1)
        s2 = standard_deviation(vm2)
        numerator = ((s1**2)/n1 + (s2**2)/n2)**2
        denominator_summand1 = ((s1**2)/n1)**2 / (n1-1)
        denominator_summand2 = ((s2**2)/n2)**2 / (n2-1)
        return int(round_to_int(numerator / (denominator_summand1 + denominator_summand2)))

    difference_of_the_means = mean(vm1) - mean(vm2)
    standard_deviation_of_the_difference = sqrt(((standard_deviation(vm1)**2)/len(vm1)) + ((standard_deviation(vm2)**2)/len(vm2)))
    percentage = np.round((1 - (mean(vm2) / mean(vm1))) * 100, 1)
    # use Student's t distribution or normal distribution
    table_value = t(degrees_of_freedom(vm1, vm2)) if len(vm1) < 30 or len(vm2) < 30 else z
    lower_confidence = difference_of_the_means - table_value * standard_deviation_of_the_difference
    upper_confidence = difference_of_the_means + table_value * standard_deviation_of_the_difference
    return (lower_confidence, percentage, upper_confidence)

# create Dictionary of Dictionaries
# benchmark-name: String -> VM-name: String -> duration-in-milliseconds: float
benchmarks = defaultdict(lambda: defaultdict(lambda: []))
sed = lambda text: re.sub("^.*\]\s*(\d*\.\d*)\s*ms\s*total\s*([A-Za-z]*)\s*([A-Za-z-]*)\s*([A-Za-z-]*).*", "\\2-\\4,\\3,\\1", text, flags=re.M).replace('\n','')
for tuple3 in (sed(line).split(',') for line in fileinput.input() if "rebench" not in line):
    benchmarks[tuple3[0]][tuple3[1]].append(float(tuple3[2]))

# from json import dumps as dump_as_json
# print(dump_as_json(benchmarks, indent=2))

all_VMs = set((item for sublist in (benchmarks[key].keys() for key in benchmarks.keys()) for item in sublist))
vm_pairs = list(combinations(all_VMs, 2))

# for every pair of VMs and every benchmark, make a comparison
for (vm1, vm2) in vm_pairs:
    significant_vms = []
    non_significant_vms = []
    print("H_0: There is no statistically significant difference between the\nperformance of "+vm1+" and "+vm2+"\n")
    for benchmark in benchmarks.keys():
        try:
            (lower_confidence, percentage, upper_confidence) = compare_two_alternatives(benchmarks[benchmark][vm1], benchmarks[benchmark][vm2])
        except ZeroDivisionError:
            sys.stderr.write("A ZeroDivisionError occured handling "+benchmark+"\n")
            continue
        if zero_in((lower_confidence, upper_confidence)):
            non_significant_vms.append((benchmark))
        else:
            faster_vm = vm1 if mean(benchmarks[benchmark][vm1]) < mean(benchmarks[benchmark][vm2]) else vm2
            significant_vms.append((benchmark, percentage, faster_vm))
    print("H_0 was rejected for the following list of benchmarks:")
    print(tabulate(sorted(significant_vms, key=lambda pair: pair[1]), headers=["Benchmark", "D (%)", "faster VM"]))
    print()
    print("H_0 was not rejected for the following list of benchmarks.")
    print(non_significant_vms)
    print()
    print()
















##
##significant_list = []
##non_significant_list = []
##
##for key in benchmarks.keys():
##    (sig, nonsig) = compare_these_vms(benchmarks[key], key)
##    significant_list.extend(sig)
##    non_significant_list.extend(nonsig)
##
##import pprint
##pp = pprint.PrettyPrinter(indent=2, width=80, depth=None, stream=None, compact=False)
##
##pp.pprint(sorted(significant_list, key=lambda tuple3: tuple3[2], reverse=False))
##print("")
##pp.pprint(non_significant_list)
##
