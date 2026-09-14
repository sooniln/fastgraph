# Module FastGraph

A high performance mathematical graph-theory library for JVM, designed primarily for read-heavy work.

Unlike most graph libraries, FastGraph stores graph topology and graph data separately. A
[Graph][io.github.sooniln.fastgraph.Graph] is a set of vertices and edges only; data associated with vertices and edges is
stored in [VertexProperty][io.github.sooniln.fastgraph.VertexProperty] and [EdgeProperty][io.github.sooniln.fastgraph.EdgeProperty] instances
created on demand from the graph (see [Graph.createVertexProperty][io.github.sooniln.fastgraph.Graph.createVertexProperty] and
[Graph.createEdgeProperty][io.github.sooniln.fastgraph.Graph.createEdgeProperty]).

# Package io.github.sooniln.fastgraph

Graph topologies ([Graph], [MutableGraph], [ImmutableGraph], [ValueGraph]), vertex and edge properties, and utilities
for building, copying, filtering, and traversing graphs.
