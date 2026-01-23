# Detailed Benchmarks

Benchmarks were run with Temurin JDK 17 on a Windows 11 machine (AMD Ryzen 5 9600X 6-Core, 16GB RAM). Benchmarks use
the [large_twitch_edges dataset](https://snap.stanford.edu/data/twitch_gamers.html) from SNAP (168114 vertices, 6797557
edges, 1 SCC).

Benchmarks are divided roughly into three categories:

* **SimpleGraphBenchmark - loads graph data using a topology that does not support multi-edges (where possible), and
  with only
  vertex values (integers) (no edge values represented).
* **GraphBenchmark - loads graph data using a topology that does not support multi-edges (where possible), and with both
  vertex values (integers) and edge values (random floats).
* **NetworkBenchmark - loads graph data using a topology that supports multi-edges (where possible), and with both
  vertex values (integers) and edge values (random floats).

Three graph types are represented in the benchmarks:

* JGraphT - results contain \*JGraphT\*
* Guava Graphs - results contain \*Guava\* (all Guava results are for immutable Guava graphs, mutable Guava graphs were
  not benchmarked)
* FastGraph - results contain \*Mutable\* or \*Immutable\* (for mutable and immmutable FastGraph graphs respectively)

## Benchmark Results

All results have been ordered from slowest to fastest. As with all microbenchmarks, it is important not to read too
deeply into these results. Choices like vertex and edge property types, order of iteration, and many other minor points
can heavily influence performance results. **Microbenchmarks do not predict real-world performance.** Still, they can
provide a reasonable high level picture of performance as long as they are not treated as gospel.

It should be noted that the fact that the benchmark graph has primitive valued vertex and edge values gives FastGraph an
advantage over JGraph and Guava Graphs since FastGraph supports pure primitive properties (and neither other library
does). A benchmark using complex object properties would likely see FastGraph at less of an advantage.

### Memory Benchmarks

Measures the amount of memory used by the graph directly after being loaded, without any further interaction. For
FastGraph implementations, memory usage is further subdivided between topology and data. The benchmark graph has
primitive vertex and edge value (int and float respectively), so FastGraph's ability to reduce memory usage by avoiding
primitive boxing shines here. If the benchmark was using a graph with Object value types, FastGraph would not have as
much memory savings.

```
JGraphTSimpleGraph:   1579150656 bytes
GuavaSimpleGraph:      683802456 bytes
MutableSimpleGraph:    116142664 bytes
    topology:              115313056 bytes
    data:                     829608 bytes
ImmutableSimpleGraph:   58755488 bytes
    topology:               58082984 bytes
    data:                     672504 bytes

JGraphTGraph:         1579150656 bytes
GuavaGraph:            792563272 bytes
MutableGraph:          317469392 bytes
    topology:              115313056 bytes
    data:                  202156336 bytes
ImmutableGraph:        260082192 bytes
    topology:               58082984 bytes
    data:                  201999208 bytes

JGraphTNetwork:       1579150672 bytes
GuavaNetwork:         1143011008 bytes
MutableNetwork:        396506528 bytes
    topology:              363787120 bytes
    data:                   32719408 bytes
ImmutableNetwork:      195711488 bytes
    topology:              167848728 bytes
    data:                   27862760 bytes
```

> [!NOTE]
> Note that the ImmutableNetwork benchmark uses less overall memory than ImmutableGraph benchmark - while network
> representations use more memory for graph topology, they can often allow for a denser memory layout for edge
> properties. If edge properties constitute a reasonable fraction of the overall graph data, a network topology may
> actually reduce data usage.

### Graph Load Times

Measures the time to load the graph from disk into memory.

```
Benchmark                                     Mode  Cnt     Score      Error  Units
LoadBenchmark.loadGuavaNetwork                avgt    4  9663.323 ± 1207.073  ms/op
LoadBenchmark.loadJGraphTSimpleGraph          avgt    4  6670.245 ± 1162.554  ms/op
LoadBenchmark.loadJGraphTNetwork              avgt    4  6411.160 ±  633.879  ms/op
LoadBenchmark.loadJGraphTGraph                avgt    4  6375.327 ±  417.842  ms/op
LoadBenchmark.loadImmutableNetwork            avgt    4  4509.500 ±  411.079  ms/op
LoadBenchmark.loadGuavaSimpleGraph            avgt    4  3902.036 ±  533.735  ms/op
LoadBenchmark.loadGuavaGraph                  avgt    4  3768.795 ±  406.051  ms/op
LoadBenchmark.loadImmutableGraph              avgt    4  3118.342 ±  115.424  ms/op
LoadBenchmark.loadMutableGraph                avgt    4  2218.675 ±  289.755  ms/op
LoadBenchmark.loadMutableNetwork              avgt    4  2198.980 ±   91.763  ms/op
LoadBenchmark.loadImmutableSimpleGraph        avgt    4  1954.813 ±  103.028  ms/op
LoadBenchmark.loadMutableSimpleGraph          avgt    4  1600.386 ±  352.758  ms/op
```

### BFS

Breadth first iteration of all vertices in the graph starting from a known vertex. For JGraphT and Guava, results are
included both for the BFS implementation included in those libraries, and with a custom BFS implementation written
similarly to FastGraph's BFS implementation for a fairer, more apples-to-apples comparison.

BFS focuses heavily on vertices, and does not care about edges as first class citizens. This provides a test case where
we would expect to see **SimpleGraphBenchmark and **GraphBenchmark benchmarks be able to outperform **NetworkBenchmark.

```
Benchmark                                     Mode  Cnt     Score      Error  Units
GuavaNetworkBenchmark.bfsGuava                avgt    4  1472.074 ±  378.759  ms/op
GuavaGraphBenchmark.bfsGuava                  avgt    4   903.669 ±  242.390  ms/op
JGraphSimpleGraphBenchmark.bfsJGraph          avgt    4   751.516 ±   92.338  ms/op
JGraphNetworkBenchmark.bfsJGraph              avgt    4   748.076 ±   74.406  ms/op
GuavaSimpleGraphBenchmark.bfsGuava            avgt    4   745.596 ±   94.911  ms/op
JGraphBenchmark.bfsJGraph                     avgt    6   667.561 ±   53.290  ms/op
GuavaNetworkBenchmark.bfs                     avgt    4   623.127 ±   41.933  ms/op
JGraphSimpleGraphBenchmark.bfs                avgt    4   581.057 ±   51.654  ms/op
JGraphNetworkBenchmark.bfs                    avgt    4   477.687 ±   36.511  ms/op
JGraphBenchmark.bfs                           avgt    4   464.802 ±   45.972  ms/op
GuavaSimpleGraphBenchmark.bfs                 avgt    4   442.799 ±   64.068  ms/op
GuavaGraphBenchmark.bfs                       avgt    4   412.511 ±   19.472  ms/op
MutableSimpleGraphBenchmark.bfs               avgt    4   137.702 ±    1.948  ms/op
MutableNetworkBenchmark.bfs                   avgt    4   131.842 ±    2.697  ms/op
MutableGraphBenchmark.bfs                     avgt    4   130.584 ±    4.967  ms/op
ImmutableNetworkBenchmark.bfs                 avgt    4    21.427 ±    1.202  ms/op
ImmutableGraphBenchmark.bfs                   avgt    4    18.926 ±    1.235  ms/op
ImmutableSimpleGraphBenchmark.bfs             avgt    4    16.802 ±    1.537  ms/op
```

### Dijkstra's Shortest Paths

Uses Dijkstra's algorithm to calculate the shortest path from one vertex to all other vertices in the graph. Since
JGraphT ships its own highly optimized version of Dijkstra's algorithm, that is tested in addition to a custom
implementation which mimics the benchmark implementation (for a more apples-to-apples comparison).

Dijkstra's focuses heavily on edges and weights, and thus this provides a test case where we would expect to see
*Network* benchmarks be able to outperform *Graph* benchmarks (and where property reads contribute heavily).

```
Benchmark                                     Mode  Cnt     Score      Error  Units
GuavaGraphBenchmark.dijkstras                 avgt    4  5383.428 ±   93.734  ms/op
GuavaNetworkBenchmark.dijkstras               avgt    4  3125.794 ±  126.471  ms/op
JGraphNetworkBenchmark.dijkstras              avgt    4   905.458 ±   78.649  ms/op
JGraphGraphBenchmark.dijkstras                avgt    4   918.595 ±   62.341  ms/op
MutableGraphBenchmark.dijkstras               avgt    4   836.289 ±   10.841  ms/op
MutableNetworkBenchmark.dijkstras             avgt    4   635.534 ±    9.620  ms/op
JGraphGraphBenchmark.dijkstrasJGraph          avgt    4   455.715 ±   34.777  ms/op
JGraphNetworkBenchmark.dijkstrasJGraph        avgt    4   448.324 ±   18.224  ms/op
ImmutableGraphBenchmark.dijkstras             avgt    4   423.763 ±    8.328  ms/op
ImmutableNetworkBenchmark.dijkstras           avgt    4   311.660 ±    3.504  ms/op
```

> [!NOTE]
> It's interesting that FastGraph graph operations can be fast enough to run the unoptimized version of Dijkstra's
> algorithm used for benchmarking here faster than JGraphT's optimized version. Notably, JGraphT's algorithm uses a
> priority queue 4-heap implementation which improves decreaseKey() time at the expense of worse insert()/removeMin()
> time. Since decreaseKey() usually the most common operation in Dijkstra's algorithm, this usually pays of with a
> faster running time.
>
> JGraphT performance obtains a roughly 2x speedup from its Dijkstra's implementation vs the benchmark implementation -
> we could guess (but easily could be incorrect until tested) that we could obtain a similar speedup to FastGraph
> results by using a similar implementation.

### Vertex Iteration

Iterates over all vertices in the graph, without reading vertex values. Since JGraphT and Guava represent vertices as
values, they are excluded from this benchmark. See instead the vertex value iteration benchmark for a fair comparison.

```
Benchmark                                     Mode  Cnt     Score      Error  Units
ImmutableSimpleGraphBenchmark.vertices        avgt    4    33.528 ±    1.360  us/op
MutableGraphBenchmark.vertices                avgt    4    33.475 ±    0.602  us/op
MutableNetworkBenchmark.vertices              avgt    4    33.365 ±    0.955  us/op
ImmutableGraphBenchmark.vertices              avgt    4    33.307 ±    2.043  us/op
ImmutableNetworkBenchmark.vertices            avgt    4    33.294 ±    0.394  us/op
MutableSimpleGraphBenchmark.vertices          avgt    4    33.161 ±    1.284  us/op
```

### Vertex Value Iteration

Iterates over all vertices in the graph, reading associated values for each.

```
Benchmark                                     Mode  Cnt     Score      Error  Units
GuavaNetworkBenchmark.vertexValues            avgt    6  2529.763 ±   51.504  us/op
GuavaGraphBenchmark.vertexValues              avgt    6  2422.174 ±   44.938  us/op
GuavaSimpleGraphBenchmark.vertexValues        avgt    6  2479.615 ±  168.781  us/op
JGraphSimpleGraphBenchmark.vertexValues       avgt    6  1433.584 ±   96.944  us/op
JGraphNetworkBenchmark.vertexValues           avgt    6  1337.532 ±  298.632  us/op
JGraphGraphBenchmark.vertexValues             avgt    6  1183.256 ±  127.849  us/op
ImmutableGraphBenchmark.vertexValues          avgt    4    31.966 ±    4.159  us/op
ImmutableSimpleGraphBenchmark.vertexValues    avgt    4    31.623 ±    1.451  us/op
ImmutableNetworkBenchmark.vertexValues        avgt    4    31.306 ±    0.656  us/op
MutableSimpleGraphBenchmark.vertexValues      avgt    4    31.166 ±    0.476  us/op
MutableGraphBenchmark.vertexValues            avgt    4    31.146 ±    0.401  us/op
MutableNetworkBenchmark.vertexValues          avgt    4    31.134 ±    0.570  us/op
```

### Successor Iteration

Iterates over all vertices in the graph, for each vertex then iterating over all successors of that vertex, reading
associated values for every successor.

```
Benchmark                                     Mode  Cnt     Score      Error  Units
GuavaNetworkBenchmark.successors              avgt    4   528.717 ±   43.582  ms/op
JGraphNetworkBenchmark.successors             avgt    4   401.032 ±   40.780  ms/op
JGraphGraphBenchmark.successors               avgt    4   389.061 ±   34.632  ms/op
JGraphSimpleGraphBenchmark.successors         avgt    4   388.373 ±   14.482  ms/op
GuavaGraphBenchmark.successors                avgt    4   304.422 ±   59.933  ms/op
GuavaSimpleGraphBenchmark.successors          avgt    4   250.285 ±   24.003  ms/op
MutableNetworkBenchmark.successors            avgt    4    98.760 ±    2.903  ms/op
MutableGraphBenchmark.successors              avgt    4    80.196 ±    1.943  ms/op
MutableSimpleGraphBenchmark.successors        avgt    4    79.702 ±    2.061  ms/op
ImmutableNetworkBenchmark.successors          avgt    4    14.897 ±    0.519  ms/op
ImmutableGraphBenchmark.successors            avgt    4    11.712 ±    0.704  ms/op
ImmutableSimpleGraphBenchmark.successors      avgt    4    10.273 ±    1.773  ms/op
```

### Edge Iteration

Iterates over all edges in the graph, without reading edge values. For JGraphT and Guava, only the
**SimpleGraphBenchmarks support edge iteration without edge values, so only those are included in this benchmark.

```
Benchmark                                     Mode  Cnt      Score      Error  Units
GuavaSimpleGraphBenchmark.edges               avgt    4   635.390 ±  157.396  ms/op
MutableSimpleGraphBenchmark.edges             avgt    4   128.463 ±    5.805  ms/op
MutableGraphBenchmark.edges                   avgt    4   119.681 ±    3.403  ms/op
JGraphSimpleGraphBenchmark.edges              avgt    4    55.875 ±    5.415  ms/op
ImmutableSimpleGraphBenchmark.edges           avgt    4    24.786 ±    0.949  ms/op
ImmutableGraphBenchmark.edges                 avgt    4    24.602 ±    1.059  ms/op
ImmutableNetworkBenchmark.edges               avgt    4     3.253 ±    0.100  ms/op
MutableNetworkBenchmark.edges                 avgt    4     1.335 ±    0.002  ms/op
```

### Edge Value Iteration

Iterates over all edges in the graph, reading associated values for each. **SimpleGraphBenchmarks do not have any edge
values and are excluded from this benchmark.

```
Benchmark                                     Mode  Cnt     Score      Error  Units
GuavaGraphBenchmark.edgeValues                avgt    4  3393.901 ±  400.610  ms/op
MutableGraphBenchmark.edgeValues              avgt    4   345.366 ±   14.163  ms/op
ImmutableGraphBenchmark.edgeValues            avgt    4   217.301 ±    2.638  ms/op
GuavaNetworkBenchmark.edgeValues              avgt    4    58.930 ±    2.670  ms/op
JGraphGraphBenchmark.edgeValues               avgt    4    54.059 ±    8.713  ms/op
JGraphNetworkBenchmark.edgeValues             avgt    4    50.026 ±    5.069  ms/op
MutableNetworkBenchmark.edgeValues            avgt    4     2.795 ±    0.011  ms/op
ImmutableNetworkBenchmark.edgeValues          avgt    4     4.673 ±    0.182  ms/op
```

### Outgoing Edge Iteration

Iterates over all vertices in the graph, for each vertex then iterating over all outgoing edges of that vertex, without
reading edge values. For JGraphT and Guava, only the **SimpleGraphBenchmarks support edge iteration without edge values,
so only those are included in this benchmark.

```
Benchmark                                     Mode  Cnt     Score      Error  Units
GuavaSimpleGraphBenchmark.outgoingEdges       avgt    4   233.582 ±   14.555  ms/op
MutableNetworkBenchmark.outgoingEdges         avgt    4   130.707 ±    6.878  ms/op
MutableSimpleGraphBenchmark.outgoingEdges     avgt    4   114.158 ±    5.001  ms/op
JGraphSimpleGraphBenchmark.outgoingEdges      avgt    4    95.841 ±   14.629  ms/op
MutableGraphBenchmark.outgoingEdges           avgt    4    83.640 ±    2.062  ms/op
ImmutableNetworkBenchmark.outgoingEdges       avgt    4    37.312 ±    1.439  ms/op
ImmutableGraphBenchmark.outgoingEdges         avgt    4    31.433 ±    2.448  ms/op
ImmutableSimpleGraphBenchmark.outgoingEdges   avgt    4    30.392 ±    1.629  ms/op
```

### Outgoing Edge Value Iteration

Iterates over all vertices in the graph, for each vertex then iterating over all outgoing edges of that vertex, reading
associated values for every outgoing edge. **SimpleGraphBenchmarks do not have any edge values and are excluded from
this benchmark.

```
Benchmark                                     Mode  Cnt     Score      Error  Units
GuavaGraphBenchmark.outgoingEdgeValues        avgt    4  4840.707 ±  457.686  ms/op
MutableGraphBenchmark.outgoingEdgeValues      avgt    4   431.767 ±   49.324  ms/op
GuavaNetworkBenchmark.outgoingEdgeValues      avgt    4   528.506 ±   33.291  ms/op
ImmutableGraphBenchmark.outgoingEdgeValues    avgt    4   292.242 ±   12.169  ms/op
MutableNetworkBenchmark.outgoingEdgeValues    avgt    4   130.526 ±   10.913  ms/op
ImmutableNetworkBenchmark.outgoingEdgeValues  avgt    4   119.536 ±   16.799  ms/op
JGraphNetworkBenchmark.outgoingEdgeValues     avgt    4   102.809 ±    9.191  ms/op
JGraphGraphBenchmark.outgoingEdgeValues       avgt    4    99.473 ±   12.739  ms/op
```
